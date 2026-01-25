package fr.lostaria.hytalenode.service;

import fr.lostaria.hytalenode.config.NodeConfig;
import fr.lostaria.hytalenode.http.ManagerClient;
import fr.lostaria.hytalenode.model.RegisterNodeRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NodeRunner implements Runnable {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeRunner.class);

    private final NodeConfig config;
    private final ManagerClient client;

    private volatile String nodeId;

    public NodeRunner(NodeConfig config) {
        this.config = config;
        this.client = new ManagerClient(config.getManagerUrl(), config.getManagerToken());
    }

    @Override
    public void run() {
        try {
            var node = client.register(new RegisterNodeRequest(config.getCurrentHostIp()));
            this.nodeId = node.id();
            LOGGER.info("REGISTERED nodeId={} publicIp={}", nodeId, node.publicIp());

            long backoffMs = 250;
            int timeout = clamp(25, 1, 30);

            while (true) {
                try {
                    String msg = client.pollMessage(nodeId, timeout);
                    if (msg != null) {
                        LOGGER.info("MESSAGE: {}", msg);
                    }
                    backoffMs = 250;
                } catch (Exception e) {
                    LOGGER.warn("Poll error: {}", e.toString());
                    sleep(backoffMs);
                    backoffMs = Math.min(backoffMs * 2, 5000);
                }
            }

        } catch (Exception e) {
            LOGGER.error("Runner fatal: {}", e.toString());
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
