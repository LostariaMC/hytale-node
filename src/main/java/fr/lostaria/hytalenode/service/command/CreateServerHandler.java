package fr.lostaria.hytalenode.service.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.lostaria.hytalenode.model.MessageEnvelope;
import fr.lostaria.hytalenode.model.command.CreateServerPayload;

import java.util.ArrayList;
import java.util.List;

public class CreateServerHandler implements MessageHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String type() {
        return "CREATE_SERVER";
    }

    @Override
    public void handle(MessageEnvelope message) throws Exception {
        CreateServerPayload p =
                mapper.treeToValue(message.payload(), CreateServerPayload.class);

        new ProcessBuilder(
                "sudo",
                "/usr/sbin/ufw",
                "allow",
                p.port() + "/udp"
        ).inheritIO().start().waitFor();

        String fullImage = "ghcr.io/lostariamc/" + p.image() + ":prod";
        String containerName = "hytale-" + p.serverId();

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

        cmd.add(fullImage);

        new ProcessBuilder(cmd)
                .inheritIO()
                .start();
    }
}
