package fr.lostaria.hytalenode.service;

import fr.lostaria.hytalenode.config.NodeConfig;
import fr.lostaria.hytalenode.http.AuthClient;
import fr.lostaria.hytalenode.http.ManagerClient;
import fr.lostaria.hytalenode.model.RegisterNodeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NodeRunner implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRunner.class);

    private final NodeConfig config;
    private final AuthClient authClient;
    private final ManagerClient managerClient;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jwt-refresher");
        t.setDaemon(true);
        return t;
    });

    private volatile String jwt;
    private volatile String nodeId;

    public NodeRunner(NodeConfig config) {
        this.config = config;
        this.authClient = new AuthClient(config.getAuthUrl(), config.getDeviceToken());
        this.managerClient = new ManagerClient(config.getManagerUrl());
    }

    @Override
    public void run() {
        try {
            refreshJwtOrThrow();

            scheduler.scheduleAtFixedRate(() -> {
                try {
                    refreshJwtOrThrow();
                } catch (Exception e) {
                    LOGGER.warn("JWT refresh failed: {}", e.toString());
                }
            }, 55, 55, TimeUnit.MINUTES);

            var node = managerClient.register(new RegisterNodeRequest(config.getCurrentHostIp()), jwt);
            this.nodeId = node.id();
            LOGGER.info("REGISTERED nodeId={} nodeIp={}", nodeId, node.ip());

            long backoffMs = 250;
            int timeout = clamp(25, 1, 30);

            while (true) {
                try {
                    String msg = managerClient.pollMessage(nodeId, timeout, jwt);
                    if (msg != null) {
                        LOGGER.info("MESSAGE: {}", msg);
                    }
                    backoffMs = 250;

                } catch (ManagerClient.UnauthorizedException e) {
                    LOGGER.warn("Unauthorized from manager, refreshing JWT...");
                    try {
                        refreshJwtOrThrow();
                    } catch (Exception refreshErr) {
                        LOGGER.error("JWT refresh after 401 failed: {}", refreshErr.toString());
                        sleep(backoffMs);
                        backoffMs = Math.min(backoffMs * 2, 5000);
                    }

                } catch (Exception e) {
                    LOGGER.warn("Poll error: {}", e.toString());
                    sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 5000);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Runner fatal: {}", e.toString());
        } finally {
            scheduler.shutdownNow();
        }
    }

    private void refreshJwtOrThrow() throws Exception {
        String newJwt = authClient.fetchJwt();
        this.jwt = newJwt;
        LOGGER.info("JWT refreshed.");
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
