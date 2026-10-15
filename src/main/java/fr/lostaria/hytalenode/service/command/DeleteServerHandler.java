package fr.lostaria.hytalenode.service.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lostaria.hytalenode.model.MessageEnvelope;
import fr.lostaria.hytalenode.model.command.DeleteServerPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class DeleteServerHandler implements MessageHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(DeleteServerHandler.class);

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String type() {
        return "DELETE_SERVER";
    }

    @Override
    public void handle(MessageEnvelope message) throws Exception {
        DeleteServerPayload p =
                mapper.treeToValue(message.payload(), DeleteServerPayload.class);

        String containerName = "hytale-" + p.serverId();

        LOGGER.info("DELETE_SERVER serverId={} port={}", p.serverId(), p.port());

        CommandRunner.Result docker = CommandRunner.run(List.of("docker", "rm", "-f", containerName));
        if (docker.ok()) {
            LOGGER.info("CONTAINER removed name={} in {} ms", containerName, docker.durationMs());
        } else {
            LOGGER.warn("CONTAINER remove failed name={} exit={} output={}", containerName, docker.exitCode(), docker.output());
        }

        CommandRunner.Result ufw = CommandRunner.run(List.of("sudo", "/usr/sbin/ufw", "delete", "allow", p.port() + "/udp"));
        if (ufw.ok()) {
            LOGGER.info("FIREWALL closed port={}/udp ({})", p.port(), ufw.output().replace('\n', ' '));
        } else {
            LOGGER.warn("FIREWALL close failed port={}/udp exit={} output={}", p.port(), ufw.exitCode(), ufw.output());
        }
    }
}
