package org.investpro.ai.local.grpc;

import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.config.AppConfigKeys;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
public final class LocalAiRuntimeLauncher {

    private static final Duration DEFAULT_STARTUP_WAIT = Duration.ofSeconds(20);
    private static volatile Process managedProcess;

    private LocalAiRuntimeLauncher() {
    }

    public static synchronized void startIfConfigured() {
        if (LocalAiRuntimeService.isGrpcAdvisoryEnabled()) {
            log.info("Local AI gRPC advisory is disabled. Skipping Python AI runtime startup.");
            return;
        }

        boolean autoStart = AppConfig.getBoolean(AppConfigKeys.PYTHON_AI_START_AUTOMATICALLY, true);
        if (!autoStart) {
            log.info("PYTHON_AI_START_AUTOMATICALLY is disabled. Skipping Python AI runtime startup.");
            return;
        }

        String host = AppConfig.get(AppConfigKeys.AI_LOCAL_GRPC_HOST, "127.0.0.1");
        int port = AppConfig.getInt(AppConfigKeys.AI_LOCAL_GRPC_PORT, 8010);
        long timeoutMs = AppConfig.getLong(AppConfigKeys.AI_LOCAL_GRPC_TIMEOUT_MS, 1500L);

        if (isHealthy(host, port, timeoutMs)) {
            log.info("Local AI gRPC runtime already reachable at {}:{}.", host, port);
            return;
        }

        if (managedProcess != null && managedProcess.isAlive()) {
            if (waitUntilHealthy(host, port, timeoutMs, Duration.ofSeconds(5))) {
                log.info("Managed Python AI runtime became healthy at {}:{}.", host, port);
            } else {
                log.warn("Managed Python AI runtime is running but health checks are still failing at {}:{}.", host,
                        port);
            }
            return;
        }

        ProcessBuilder processBuilder = buildProcessBuilder();
        if (processBuilder == null) {
            log.warn("Unable to build Python AI startup command. Auto-start skipped.");
            return;
        }

        try {
            managedProcess = processBuilder.start();
            log.info("Started local Python AI runtime process. Waiting for gRPC health at {}:{}...", host, port);

            if (waitUntilHealthy(host, port, timeoutMs, DEFAULT_STARTUP_WAIT)) {
                log.info("Local Python AI runtime is healthy at {}:{}.", host, port);
            } else {
                log.warn("Python AI runtime started but health did not become ready within {} seconds.",
                        DEFAULT_STARTUP_WAIT.toSeconds());
            }
        } catch (Exception exception) {
            log.warn("Failed to start Python AI runtime: {}", exception.getMessage());
        }
    }

    public static synchronized void stopManagedProcess() {
        Process process = managedProcess;
        managedProcess = null;

        if (process == null || !process.isAlive()) {
            return;
        }

        try {
            process.destroy();
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            log.info("Stopped managed local Python AI runtime process.");
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } catch (Exception exception) {
            log.debug("Error while stopping managed Python AI runtime", exception);
        }
    }

    private static boolean waitUntilHealthy(String host, int port, long timeoutMs, Duration duration) {
        Instant deadline = Instant.now().plus(duration);

        while (Instant.now().isBefore(deadline)) {
            if (isHealthy(host, port, timeoutMs)) {
                return true;
            }

            try {
                Thread.sleep(250);
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return false;
    }

    private static boolean isHealthy(String host, int port, long timeoutMs) {
        try (PythonAiGrpcClient client = new PythonAiGrpcClient(host, port, timeoutMs)) {
            return client.health(false, "UNKNOWN", 0L, "").ok();
        } catch (Exception ignored) {
            return false;
        }
    }

    private static ProcessBuilder buildProcessBuilder() {
        String configuredCommand = AppConfig.get(AppConfigKeys.PYTHON_AI_COMMAND, "");
        Path projectRoot = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();

        List<String> command;
        if (!configuredCommand.isBlank()) {
            command = shellCommand(configuredCommand.trim());
        } else {
            Path serverScript = projectRoot.resolve("ai-service").resolve("app").resolve("server.py");
            if (!Files.exists(serverScript)) {
                log.warn("Local AI server script not found at {}", serverScript);
                return null;
            }

            String pythonExecutable = resolvePythonExecutable(projectRoot);
            command = List.of(pythonExecutable, serverScript.toString());
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(projectRoot.toFile());
        processBuilder.redirectErrorStream(true);
        processBuilder.redirectOutput(ProcessBuilder.Redirect.DISCARD);

        String pythonPathEntry = projectRoot.resolve("ai-service").toString();
        Map<String, String> environment = processBuilder.environment();
        environment.compute("PYTHONPATH", (k, existing) -> appendPath(existing, pythonPathEntry));

        return processBuilder;
    }

    private static List<String> shellCommand(String commandLine) {
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        if (windows) {
            return List.of("cmd", "/c", commandLine);
        }

        return List.of("sh", "-c", commandLine);
    }

    private static String resolvePythonExecutable(Path projectRoot) {
        Path windowsVenv = projectRoot.resolve(".venv").resolve("Scripts").resolve("python.exe");
        if (Files.exists(windowsVenv)) {
            return windowsVenv.toString();
        }

        Path unixVenv = projectRoot.resolve(".venv").resolve("bin").resolve("python");
        if (Files.exists(unixVenv)) {
            return unixVenv.toString();
        }

        return "python";
    }

    private static String appendPath(String existing, String entry) {
        String safeEntry = Objects.requireNonNullElse(entry, "").trim();
        if (safeEntry.isBlank()) {
            return existing == null ? "" : existing;
        }

        if (existing == null || existing.isBlank()) {
            return safeEntry;
        }

        List<String> parts = new ArrayList<>();
        String separator = System.getProperty("path.separator", ";");
        for (String value : existing.split(java.util.regex.Pattern.quote(separator))) {
            if (!value.isBlank()) {
                parts.add(value.trim());
            }
        }

        if (!parts.contains(safeEntry)) {
            parts.add(safeEntry);
        }

        return String.join(separator, parts);
    }
}