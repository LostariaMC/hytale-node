package fr.lostaria.hytalenode.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class NodeConfigManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NodeConfigManager.class);

    private static final String CONFIG_FILE_NAME = "config.json";
    private static final String RESOURCE_DEFAULT_CONFIG = "/config.json";

    private final ObjectMapper mapper;

    public NodeConfigManager() {
        this.mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    public Path configPath() {
        return Paths.get(CONFIG_FILE_NAME).toAbsolutePath();
    }

    public NodeConfig loadOrCreate() {
        Path path = configPath();

        if (Files.notExists(path)) {
            createFromResources(path);
        }

        return read(path);
    }

    private void createFromResources(Path targetPath) {
        LOGGER.info("Config file not found. Creating default config at {}", targetPath);

        try (InputStream in = getClass().getResourceAsStream(RESOURCE_DEFAULT_CONFIG)) {
            if (in == null) {
                throw new IllegalStateException("Default config not found in resources: " + RESOURCE_DEFAULT_CONFIG);
            }
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Default config created. Edit it and set enable=true to start the node.");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config file: " + targetPath + " : " + e.getMessage(), e);
        }
    }

    private NodeConfig read(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            NodeConfig cfg = mapper.readValue(in, NodeConfig.class);

            if (cfg.getManagerUrl() == null) cfg.setManagerUrl("");
            if (cfg.getAuthUrl() == null) cfg.setAuthUrl("");
            if (cfg.getPubsubUrl() == null) cfg.setPubsubUrl("");
            if (cfg.getDeviceToken() == null) cfg.setDeviceToken("");
            if (cfg.getCurrentHostIp() == null) cfg.setCurrentHostIp("127.0.0.1");

            return cfg;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read config file: " + path + " : " + e.getMessage(), e);
        }
    }

}
