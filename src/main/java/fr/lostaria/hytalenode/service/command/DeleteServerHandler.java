package fr.lostaria.hytalenode.service.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lostaria.hytalenode.model.MessageEnvelope;
import fr.lostaria.hytalenode.model.command.DeleteServerPayload;

public class DeleteServerHandler implements MessageHandler {

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

        new ProcessBuilder(
                "docker",
                "rm",
                "-f",
                containerName
        ).inheritIO().start().waitFor();

        new ProcessBuilder(
                "sudo",
                "/usr/sbin/ufw",
                "delete",
                "allow",
                p.port() + "/udp"
        ).inheritIO().start().waitFor();
    }
}
