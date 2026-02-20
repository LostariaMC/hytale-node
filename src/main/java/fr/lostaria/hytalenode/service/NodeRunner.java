package fr.lostaria.hytalenode.service;

import fr.lostaria.hytalenode.config.NodeConfig;
import fr.lostaria.hytalenode.http.AuthClient;
import fr.lostaria.hytalenode.http.ManagerClient;
import fr.lostaria.hytalenode.http.PubsubClient;
import fr.lostaria.hytalenode.http.UnauthorizedException;
import fr.lostaria.hytalenode.model.RegisterNodeRequest;
import fr.lostaria.hytalenode.service.command.CreateServerHandler;
import fr.lostaria.hytalenode.service.command.DeleteServerHandler;
import fr.lostaria.hytalenode.service.command.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static fr.lostaria.hytalenode.utils.HttpUtils.clamp;

public class NodeRunner implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRunner.class);

    private final NodeConfig config;
    private final AuthClient authClient;
    private final ManagerClient managerClient;
    private final PubsubClient pubsubClient;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jwt-refresher");
        t.setDaemon(true);
        return t;
    });

    private volatile String jwt;
    private volatile String nodeId;
    private volatile String consumer;

    public NodeRunner(NodeConfig config) {
        this.config = config;
        this.authClient = new AuthClient(config.getAuthUrl(), config.getDeviceToken());
        this.managerClient = new ManagerClient(config.getManagerUrl());
        this.pubsubClient = new PubsubClient(config.getPubsubUrl());
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

            var node = managerClient.register(
                    new RegisterNodeRequest(
                            config.getCurrentHostIp(),
                            config.getPortRangeStart(),
                            config.getPortRangeEnd()
                    ),
                    jwt
            );

            this.nodeId = node.id();
            this.consumer = "node-" + nodeId;

            LOGGER.info(
                    "REGISTERED nodeId={} consumer={} ip={} portStart={} portEnd={}",
                    nodeId,
                    consumer,
                    node.ip(),
                    node.portRangeStart(),
                    node.portRangeEnd()
            );

            var router = new MessageRouter(
                    List.of(
                            new CreateServerHandler(),
                            new DeleteServerHandler()
                    )
            );

            long backoffMs = 250;
            int timeout = clamp(25, 1, 30);

            while (true) {
                try {
                    var msg = pubsubClient.pollMessage(consumer, timeout, jwt);
                    if (msg != null) {
                        LOGGER.info("MESSAGE type={} producer={}", msg.type(), msg.producer());
                        router.route(msg);
                    }
                    backoffMs = 250;

                } catch (UnauthorizedException e) {
                    LOGGER.warn("Unauthorized, refreshing JWT...");
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

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
