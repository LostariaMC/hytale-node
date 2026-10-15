package fr.lostaria.hytalenode.service.command;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CommandRunner {

    private CommandRunner() {}

    public record Result(int exitCode, String output, long durationMs) {

        public boolean ok() {
            return exitCode == 0;
        }

        public String lastLine() {
            String[] lines = output.split("\\R");
            return lines[lines.length - 1].trim();
        }
    }

    public static Result run(List<String> cmd) throws IOException, InterruptedException {
        long start = System.currentTimeMillis();

        Process process = new ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start();

        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exitCode = process.waitFor();

        return new Result(exitCode, output, System.currentTimeMillis() - start);
    }
}
