package fr.lostaria.hytalenode.service;

import fr.lostaria.hytalenode.config.Config;
import fr.lostaria.hytalenode.http.AuthClient;
import fr.lostaria.hytalenode.http.ManagerClient;
import fr.lostaria.hytalenode.http.UnauthorizedException;
import fr.lostaria.hytalenode.model.MessageEnvelope;
import fr.lostaria.hytalenode.model.RegisterNodeRequest;
import fr.lostaria.hytalenode.pubsub.PubsubClient;
import fr.lostaria.hytalenode.service.command.CreateServerHandler;
import fr.lostaria.hytalenode.service.command.DeleteServerHandler;
import fr.lostaria.hytalenode.service.command.MessageRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class NodeRunner implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRunner.class);

    private final Config config;
    private final AuthClient authClient;
    private final ManagerClient managerClient;
    private final PubsubClient pubsubClient;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "jwt-refresher");
        t.setDaemon(true);
        return t;
    });

    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "node-commands"));

    private volatile String jwt;
    private volatile String nodeId;
    private volatile String consumer;

    public NodeRunner(Config config) {
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
                    "REGISTERED nodeId={} ip={} portStart={} portEnd={}",
                    nodeId,
                    node.ip(),
                    node.portRangeStart(),
                    node.portRangeEnd()
            );

            var router = new MessageRouter(
                    List.of(
                            new CreateServerHandler(config),
                            new DeleteServerHandler()
                    )
            );

            long backoffMs = 250;

            while (true) {
                long connectedAt = System.currentTimeMillis();
                try {
                    pubsubClient.listen(consumer, jwt, msg -> commandExecutor.execute(() -> handle(router, msg)));

                } catch (UnauthorizedException e) {
                    LOGGER.warn("Unauthorized, refreshing JWT...");
                    try {
                        refreshJwtOrThrow();
                    } catch (Exception refreshErr) {
                        LOGGER.error("JWT refresh after 401 failed: {}", refreshErr.toString());
                    }

                } catch (Exception e) {
                    LOGGER.warn("Pubsub error: {}", e.toString());
                }

                if (System.currentTimeMillis() - connectedAt > 30_000) {
                    backoffMs = 250;
                }
                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, 5000);
            }

        } catch (Exception e) {
            LOGGER.error("Runner fatal: {}", e.toString());
        } finally {
            scheduler.shutdownNow();
            commandExecutor.shutdownNow();
        }
    }

    private void handle(MessageRouter router, MessageEnvelope msg) {
        LOGGER.info("MESSAGE type={} producer={}", msg.type(), msg.producer());
        try {
            router.route(msg);
        } catch (Exception e) {
            LOGGER.error("Handler error type={}: {}", msg.type(), e.toString());
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
