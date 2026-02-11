package fr.lostaria.hytalenode;

import fr.lostaria.hytalenode.config.NodeConfig;
import fr.lostaria.hytalenode.config.NodeConfigManager;
import fr.lostaria.hytalenode.service.NodeRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HytaleNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(HytaleNode.class);

    public static void main(String[] args) {
        NodeConfigManager configManager = new NodeConfigManager();
        NodeConfig config = configManager.loadOrCreate();

        if (!config.isEnable()) {
            LOGGER.warn("STATUS=DISABLED (enable=false). No request will be sent to manager.");
            LOGGER.warn("Edit {} and set enable=true to start.", configManager.configPath());
            return;
        }

        if (config.getManagerUrl().isBlank()
                || config.getAuthUrl().isBlank()
                || config.getPubsubUrl().isBlank()
                || config.getDeviceToken().isBlank()
                || config.getPortRangeStart() <= 0
                || config.getPortRangeEnd() <= 0) {

            LOGGER.error(
                    "STATUS=ERROR Missing managerUrl or authUrl or pubsubUrl or deviceToken "
                            + "or portRangeStart or portRangeEnd in config. "
                            + "Fix config.json."
            );
            return;
        }

        LOGGER.info("STATUS=ENABLED. Starting runner. managerUrl={}", config.getManagerUrl());
        new Thread(new NodeRunner(config), "node-runner").start();
    }
}
