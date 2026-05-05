package org.investpro.investpro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class AppFiles {
    private static final Logger logger = LoggerFactory.getLogger(AppFiles.class);
    private static final Path APP_HOME = Path.of(System.getProperty("user.home"), ".investpro");

    private AppFiles() {
    }

    public static Path getAppHome() {
        ensureAppHome();
        return APP_HOME;
    }

    public static Path resolveInAppHome(String fileName) {
        ensureAppHome();
        return APP_HOME.resolve(fileName);
    }

    public static Path ensureConfigFile(String resourceName) {
        ensureAppHome();
        Path path = APP_HOME.resolve(resourceName);
        if (Files.exists(path)) {
            return path;
        }

        try (InputStream inputStream = AppFiles.class.getResourceAsStream("/" + resourceName)) {
            if (inputStream != null) {
                Files.copy(inputStream, path);
            } else {
                Files.createFile(path);
            }
        } catch (IOException e) {
            logger.warn("Failed to initialize config file {}", path, e);
        }
        return path;
    }

    public static Properties loadProperties(Path path, String resourceName) {
        Properties properties = new Properties();

        try (InputStream inputStream = AppFiles.class.getResourceAsStream("/" + resourceName)) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException e) {
            logger.warn("Failed to load bundled defaults for {}", resourceName, e);
        }

        ensureConfigFile(resourceName);
        if (Files.notExists(path)) {
            return properties;
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            properties.load(inputStream);
        } catch (IOException e) {
            logger.warn("Failed to load user config {}", path, e);
        }

        return properties;
    }

    public static void storeProperties(Properties properties, Path path, String comments) {
        ensureAppHome();
        try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            properties.store(writer, comments);
        } catch (IOException e) {
            logger.error("Failed to save properties to {}", path, e);
        }
    }

    private static void ensureAppHome() {
        try {
            Files.createDirectories(APP_HOME);
        } catch (IOException e) {
            logger.error("Failed to create application directory {}", APP_HOME, e);
        }
    }
}
