package org.investpro.investpro.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PredictorRuntimeManager {
    private static final Logger logger = LoggerFactory.getLogger(PredictorRuntimeManager.class);
    private static final Object LOCK = new Object();
    private static final String HOST = System.getProperty("investpro.ai.host", "localhost");
    private static final int PORT = Integer.getInteger("investpro.ai.port", 50051);

    private static volatile Process predictorProcess;
    private static volatile boolean startedByManager;
    private static volatile boolean startAttempted;

    private PredictorRuntimeManager() {
    }

    public static boolean ensureAvailable(Duration startupTimeout) {
        if (!isLocalHost()) {
            if (isHealthy()) {
                return true;
            }
            logger.debug("Skipping predictor auto-start because host {} is not local.", HOST);
            return false;
        }

        synchronized (LOCK) {
            if (isPortOpen(Duration.ofMillis(350)) && isHealthy()) {
                return true;
            }

            if (predictorProcess != null && predictorProcess.isAlive()) {
                return waitForHealthy(startupTimeout);
            }

            if (startAttempted) {
                return waitForHealthy(Duration.ofSeconds(2));
            }

            startAttempted = true;
            predictorProcess = startLocalPredictor();
            startedByManager = predictorProcess != null;
            return waitForHealthy(startupTimeout);
        }
    }

    public static void shutdownStartedProcess() {
        synchronized (LOCK) {
            if (!startedByManager || predictorProcess == null) {
                return;
            }

            if (predictorProcess.isAlive()) {
                predictorProcess.destroy();
                try {
                    predictorProcess.waitFor();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            predictorProcess = null;
            startedByManager = false;
        }
    }

    private static boolean isHealthy() {
        InvestProAIPredictor predictor = new InvestProAIPredictor(HOST, PORT);
        try {
            return predictor.checkHealth();
        } finally {
            predictor.shutdown();
        }
    }
    private static boolean waitForHealthy(Duration timeout) {
        long timeoutMillis = Math.max(1_000L, timeout.toMillis());
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        while (System.nanoTime() < deadline) {
            if (isHealthy()) {
                logger.info("Predictor is available at {}:{}.", HOST, PORT);
                return true;
            }

            try {
                TimeUnit.MILLISECONDS.sleep(750);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        return isHealthy();
    }

    private static Process startLocalPredictor() {
        Path logFile = prepareLogFile();
        List<List<String>> commands = List.of(
                List.of(
                        Path.of(System.getProperty("user.dir"), ".venv", "Scripts", "python.exe").toString(),
                        "-m",
                        "predictor",
                        "--host",
                        HOST,
                        "--port",
                        Integer.toString(PORT)
                ),
                List.of("py", "-m", "predictor", "--host", HOST, "--port", Integer.toString(PORT)),
                List.of("python", "-m", "predictor", "--host", HOST, "--port", Integer.toString(PORT))
        );
        for (List<String> command : commands) {
            try {
                ProcessBuilder builder = new ProcessBuilder(command);
                builder.directory(new File(System.getProperty("user.dir")));
                builder.redirectErrorStream(true);
                if (logFile != null) {
                    builder.redirectOutput(ProcessBuilder.Redirect.appendTo(logFile.toFile()));
                }
                Process process = builder.start();
                logger.info("Started Python predictor with command {} for {}:{}.", command.getFirst(), HOST, PORT);
                return process;
            } catch (IOException e) {
                logger.debug("Unable to start predictor with {}: {}", command.getFirst(), e.getMessage());
            }
        }

        logger.warn("Unable to auto-start the Python predictor. Checked local venv, py, and python launchers.");
        return null;
    }

    private static boolean isPortOpen(Duration timeout) {
        int timeoutMillis = (int) Math.max(100L, timeout.toMillis());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(HOST, PORT), timeoutMillis);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    private static Path prepareLogFile() {
        try {
            Path targetDir = Path.of(System.getProperty("user.dir"), "target");
            Files.createDirectories(targetDir);
            return targetDir.resolve("predictor-runtime.log");
        } catch (IOException e) {
            logger.debug("Unable to prepare predictor log directory: {}", e.getMessage());
            return null;
        }
    }

    private static boolean isLocalHost() {
        if (PredictorRuntimeManager.HOST == null) {
            return false;
        }

        return switch (PredictorRuntimeManager.HOST.trim().toLowerCase()) {
            case "localhost", "127.0.0.1", "::1" -> true;
            default -> false;
        };
    }
}
