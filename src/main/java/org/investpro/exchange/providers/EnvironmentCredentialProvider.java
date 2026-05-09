package org.investpro.exchange.providers;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.contracts.CredentialProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Getter
@Setter
public class EnvironmentCredentialProvider implements CredentialProvider {

    private final Map<String, String> dotenvCache = new ConcurrentHashMap<>();
    private volatile boolean dotenvLoaded;

    @Override
    public Optional<String> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String envValue = System.getenv(key);

        if (envValue != null && !envValue.trim().isEmpty()) {
            return Optional.of(envValue.trim());
        }

        loadDotEnvOnce();

        String dotenvValue = dotenvCache.get(key);

        if (dotenvValue == null || dotenvValue.trim().isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(dotenvValue.trim());
    }

    private void loadDotEnvOnce() {
        if (dotenvLoaded) {
            return;
        }

        synchronized (dotenvCache) {
            if (dotenvLoaded) {
                return;
            }

            findDotEnv().ifPresent(this::loadDotEnv);
            dotenvLoaded = true;
        }
    }

    private Optional<Path> findDotEnv() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();

        for (int depth = 0; current != null && depth < 8; depth++) {
            Path candidate = current.resolve(".env");

            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }

            current = current.getParent();
        }

        return Optional.empty();
    }

    private void loadDotEnv(Path dotenvPath) {
        try {
            for (String line : Files.readAllLines(dotenvPath)) {
                String trimmed = line.trim();

                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int equalsIndex = trimmed.indexOf('=');

                if (equalsIndex <= 0) {
                    continue;
                }

                String name = trimmed.substring(0, equalsIndex).trim();
                String value = trimmed.substring(equalsIndex + 1).trim();

                value = stripQuotes(value);
                value = value.replace("\\n", "\n");

                dotenvCache.putIfAbsent(name, value);
            }

            log.info("Loaded credentials from .env: {}", dotenvPath);

        } catch (IOException exception) {
            log.warn("Unable to load .env file: {}", exception.getMessage());
        }
    }

    private String stripQuotes(String value) {
        if (value == null) {
            return "";
        }

        value = value.trim();

        if (value.length() >= 2
                && value.charAt(0) == value.charAt(value.length() - 1)
                && (value.charAt(0) == '"' || value.charAt(0) == '\'')) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}