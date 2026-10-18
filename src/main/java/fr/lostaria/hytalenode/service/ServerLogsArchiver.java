package fr.lostaria.hytalenode.service;

import fr.lostaria.hytalenode.service.command.CommandRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ServerLogsArchiver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServerLogsArchiver.class);

    private static final String DATE_PATTERN = "yyyy-MM-dd_HH-mm-ss";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern(DATE_PATTERN);

    private final Path logsRoot;
    private final Path runningRoot;

    public ServerLogsArchiver(String logsPath) {
        this.logsRoot = Path.of(logsPath);
        this.runningRoot = logsRoot.resolve("running");
    }

    public Path prepare(String serverId) throws IOException {
        Path dir = runningRoot.resolve(LocalDateTime.now().format(DATE_FORMAT) + "_" + serverId);
        Files.createDirectories(dir);
        LOGGER.info("LOGS directory ready serverId={} path={}", serverId, dir);
        return dir;
    }

    public void watch(String serverId) {
        Thread.ofVirtual().name("logs-" + serverId).start(() -> {
            try {
                CommandRunner.run(List.of("docker", "wait", containerName(serverId)));
            } catch (Exception e) {
                LOGGER.warn("LOGS docker wait failed serverId={}: {}", serverId, e.toString());
            }
            archive(serverId);
        });
    }

    public void archiveStopped() {
        if (!Files.isDirectory(runningRoot)) return;

        List<Path> dirs;
        try (Stream<Path> list = Files.list(runningRoot)) {
            dirs = list.filter(Files::isDirectory).toList();
        } catch (IOException e) {
            LOGGER.error("LOGS cannot list {}: {}", runningRoot, e.toString());
            return;
        }

        for (Path dir : dirs) {
            String serverId = serverIdOf(dir);
            if (serverId == null) continue;

            if (isRunning(serverId)) {
                LOGGER.info("LOGS server still running, watching serverId={}", serverId);
                watch(serverId);
            } else {
                archive(serverId);
            }
        }
    }

    public synchronized void archive(String serverId) {
        if (!Files.isDirectory(runningRoot)) return;

        List<Path> dirs;
        try (Stream<Path> list = Files.list(runningRoot)) {
            dirs = list.filter(Files::isDirectory)
                    .filter(dir -> serverId.equals(serverIdOf(dir)))
                    .toList();
        } catch (IOException e) {
            LOGGER.error("LOGS cannot list {}: {}", runningRoot, e.toString());
            return;
        }

        for (Path dir : dirs) {
            archiveDir(serverId, dir);
        }
    }

    private void archiveDir(String serverId, Path dir) {
        try {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(dir)) {
                files = walk.filter(Files::isRegularFile).toList();
            }

            if (files.isEmpty()) {
                deleteRecursively(dir);
                LOGGER.info("LOGS nothing to archive serverId={}", serverId);
                return;
            }

            Path zip = logsRoot.resolve(dir.getFileName() + ".zip");
            try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
                for (Path file : files) {
                    out.putNextEntry(new ZipEntry(dir.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }

            deleteRecursively(dir);
            LOGGER.info("LOGS archived serverId={} files={} archive={}", serverId, files.size(), zip);

        } catch (IOException e) {
            LOGGER.error("LOGS archive failed serverId={} path={}: {}", serverId, dir, e.toString());
        }
    }

    private static boolean isRunning(String serverId) {
        try {
            CommandRunner.Result res = CommandRunner.run(
                    List.of("docker", "inspect", "-f", "{{.State.Running}}", containerName(serverId))
            );
            return res.ok() && "true".equals(res.output().trim());
        } catch (Exception e) {
            LOGGER.warn("LOGS docker inspect failed serverId={}: {}", serverId, e.toString());
            return false;
        }
    }

    private static String serverIdOf(Path dir) {
        String name = dir.getFileName().toString();
        int prefix = DATE_PATTERN.length();
        if (name.length() <= prefix + 1 || name.charAt(prefix) != '_') return null;
        return name.substring(prefix + 1);
    }

    private static String containerName(String serverId) {
        return "hytale-" + serverId;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(p);
            }
        }
    }
}
