package fr.lostaria.hytalenode.service.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lostaria.hytalenode.config.Config;
import fr.lostaria.hytalenode.model.MessageEnvelope;
import fr.lostaria.hytalenode.model.command.CreateServerPayload;
import fr.lostaria.hytalenode.service.ServerLogsArchiver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CreateServerHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateServerHandler.class);

    private static final String CONTAINER_LOGS_PATH = "/srv/server/logs";

    private final Config config;
    private final ServerLogsArchiver logsArchiver;

    private final ObjectMapper mapper = new ObjectMapper();

    public CreateServerHandler(Config config, ServerLogsArchiver logsArchiver) {
        this.config = config;
        this.logsArchiver = logsArchiver;
    }

    @Override
    public String type() {
        return "CREATE_SERVER";
    }

    @Override
    public void handle(MessageEnvelope message) throws Exception {
        CreateServerPayload p =
                mapper.treeToValue(message.payload(), CreateServerPayload.class);

        String fullImage = "ghcr.io/lostariamc/" + p.image() + ":prod";
        String containerName = "hytale-" + p.serverId();

        LOGGER.info("CREATE_SERVER serverId={} image={} port={}", p.serverId(), fullImage, p.port());

        CommandRunner.Result ufw = CommandRunner.run(List.of("sudo", "/usr/sbin/ufw", "allow", p.port() + "/udp"));
        if (ufw.ok()) {
            LOGGER.info("FIREWALL opened port={}/udp ({})", p.port(), ufw.output().replace('\n', ' '));
        } else {
            LOGGER.warn("FIREWALL open failed port={}/udp exit={} output={}", p.port(), ufw.exitCode(), ufw.output());
        }

        List<String> cmd = new ArrayList<>();
        cmd.add("docker");
        cmd.add("run");
        cmd.add("--rm");
        cmd.add("-d");

        cmd.add("--name");
        cmd.add(containerName);

        cmd.add("-e"); cmd.add("SERVER_ID=" + p.serverId());
        cmd.add("-e"); cmd.add("SERVER_PORT=" + p.port());
        cmd.add("-e"); cmd.add("SESSION_TOKEN=" + p.hytaleAuth().sessionToken());
        cmd.add("-e"); cmd.add("IDENTITY_TOKEN=" + p.hytaleAuth().identityToken());

        cmd.add("-p"); cmd.add(p.port() + ":" + p.port() + "/udp");
        cmd.add("-v"); cmd.add("/srv/resources/assets/Assets.zip:/srv/server/Assets.zip");

        cmd.add("-v");
        cmd.add(config.getDeviceToken() + ":" + config.getDeviceToken() + ":ro");

        Path logsDir = logsArchiver.prepare(p.serverId());
        cmd.add("-v"); cmd.add(logsDir + ":" + CONTAINER_LOGS_PATH);

        cmd.add(fullImage);

        CommandRunner.Result docker = CommandRunner.run(cmd);

        if (!docker.ok()) {
            LOGGER.error("CONTAINER start failed name={} exit={} output={}", containerName, docker.exitCode(), docker.output());

            logsArchiver.archive(p.serverId());

            CommandRunner.Result rollback = CommandRunner.run(List.of("sudo", "/usr/sbin/ufw", "delete", "allow", p.port() + "/udp"));
            LOGGER.info("FIREWALL closed port={}/udp after failed start (exit={})", p.port(), rollback.exitCode());

            throw new IllegalStateException("docker run failed for " + containerName);
        }

        String containerId = docker.lastLine();
        LOGGER.info(
                "CONTAINER started name={} id={} in {} ms",
                containerName,
                containerId.substring(0, Math.min(12, containerId.length())),
                docker.durationMs()
        );

        logsArchiver.watch(p.serverId());
    }
}
