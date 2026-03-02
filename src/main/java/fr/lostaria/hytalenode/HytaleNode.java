package fr.lostaria.hytalenode;

import fr.lostaria.hytalenode.config.Config;
import fr.lostaria.hytalenode.config.ConfigManager;
import fr.lostaria.hytalenode.service.NodeRunner;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HytaleNode {

    private static final Logger LOGGER = LoggerFactory.getLogger(HytaleNode.class);

    @Getter
    private static ConfigManager configManager;

    public static void main(String[] args) {
        configManager = new ConfigManager();
        Config config = configManager.loadOrCreate();

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
