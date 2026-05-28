package org.investpro.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * Central configuration reader for InvestPro.
 *
 * Resolution order:
 * 1. JVM system property: -DKEY=value
 * 2. OS environment variable: KEY=value
 * 3. .env file in project/app working directory
 * 4. config.properties in project/app working directory or classpath
 * 5. Provided default value
 */
@Slf4j
public final class AppConfig {

    private static final Dotenv DOTENV = Dotenv.configure()
            .ignoreIfMissing()
            .ignoreIfMalformed()
            .load();
    private static final Properties CONFIG_PROPERTIES = loadConfigProperties();

    private AppConfig() {
    }

    public static @NotNull String get(@NotNull String key) {
        return get(key, "");
    }

    public static @NotNull String get(@NotNull String key, @Nullable String defaultValue) {
        Objects.requireNonNull(key, "key must not be null");

        String systemPropertyValue = System.getProperty(key);
        if (hasText(systemPropertyValue)) {
            return systemPropertyValue.trim();
        }

        String environmentValue = System.getenv(key);
        if (hasText(environmentValue)) {
            return environmentValue.trim();
        }

        String dotenvValue = DOTENV.get(key);
        if (hasText(dotenvValue)) {
            return stripQuotes(dotenvValue.trim());
        }

        String propertiesValue = CONFIG_PROPERTIES.getProperty(key);
        if (hasText(propertiesValue)) {
            return stripQuotes(propertiesValue.trim());
        }

        String legacyValue = getLegacyInvestProValue(key);
        if (hasText(legacyValue)) {
            return stripQuotes(legacyValue.trim());
        }

        return defaultValue == null ? "" : defaultValue.trim();
    }

    public static boolean has(@NotNull String key) {
        return hasText(get(key));
    }

    public static boolean missing(@NotNull String key) {
        return !has(key);
    }

    public static int getInt(@NotNull String key, int defaultValue) {
        String value = get(key);

        if (!hasText(value)) {
            return defaultValue;
        }

        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("Invalid integer config for key={} value={}. Using default={}", key, maskIfSensitive(key, value), defaultValue);
            return defaultValue;
        }
    }

    public static long getLong(@NotNull String key, long defaultValue) {
        String value = get(key);

        if (!hasText(value)) {
            return defaultValue;
        }

        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("Invalid long config for key={} value={}. Using default={}", key, maskIfSensitive(key, value), defaultValue);
            return defaultValue;
        }
    }

    public static double getDouble(@NotNull String key, double defaultValue) {
        String value = get(key);

        if (!hasText(value)) {
            return defaultValue;
        }

        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException exception) {
            log.warn("Invalid double config for key={} value={}. Using default={}", key, maskIfSensitive(key, value), defaultValue);
            return defaultValue;
        }
    }

    public static boolean getBoolean(@NotNull String key, boolean defaultValue) {
        String value = get(key);

        if (!hasText(value)) {
            return defaultValue;
        }

        String normalized = value.trim().toLowerCase(Locale.ROOT);

        return switch (normalized) {
            case "true", "yes", "y", "1", "on", "enabled" -> true;
            case "false", "no", "n", "0", "off", "disabled" -> false;
            default -> {
                log.warn("Invalid boolean config for key={} value={}. Using default={}", key, maskIfSensitive(key, value), defaultValue);
                yield defaultValue;
            }
        };
    }

    public static @NotNull Path getPath(@NotNull String key, @NotNull String defaultValue) {
        return Path.of(get(key, defaultValue));
    }

    public static @NotNull String require(@NotNull String key) {
        String value = get(key);

        if (!hasText(value)) {
            throw new IllegalStateException("Missing required configuration key: " + key);
        }

        return value;
    }

    public static @NotNull String requireAny(@NotNull String primaryKey, @NotNull String fallbackKey) {
        String primaryValue = get(primaryKey);

        if (hasText(primaryValue)) {
            return primaryValue;
        }

        String fallbackValue = get(fallbackKey);

        if (hasText(fallbackValue)) {
            return fallbackValue;
        }

        throw new IllegalStateException(
                "Missing required configuration. Expected one of: " + primaryKey + ", " + fallbackKey);
    }

    public static void logStartupSummary() {
        log.info(
                "InvestPro config loaded. app={} env={} defaultExchange={} defaultMarket={} autoTrading={} aiReasoning={}",
                get(AppConfigKeys.APP_NAME, "InvestPro"),
                get(AppConfigKeys.APP_ENV, "development"),
                get(AppConfigKeys.DEFAULT_EXCHANGE, "OANDA"),
                get(AppConfigKeys.DEFAULT_MARKET_TYPE, "FOREX"),
                getBoolean(AppConfigKeys.AUTO_TRADING_ENABLED, false),
                getBoolean(AppConfigKeys.AI_REASONING_ENABLED, true)
        );
    }

    public static @NotNull String maskIfSensitive(@NotNull String key, @Nullable String value) {
        if (!hasText(value)) {
            return "";
        }

        String normalizedKey = key.toUpperCase(Locale.ROOT);

        boolean sensitive = normalizedKey.contains("KEY")
                || normalizedKey.contains("SECRET")
                || normalizedKey.contains("TOKEN")
                || normalizedKey.contains("PASSWORD")
                || normalizedKey.contains("PRIVATE")
                || normalizedKey.contains("PASSPHRASE");

        if (!sensitive) {
            return value;
        }

        String text = value.trim();

        if (text.length() <= 8) {
            return "********";
        }

        return text.substring(0, 4) + "..." + text.substring(text.length() - 4);
    }

    private static boolean hasText(@Nullable String value) {
        return value != null && !value.trim().isEmpty();
    }

    private static @NotNull String stripQuotes(@NotNull String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");

            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }

        return value;
    }

    private static @NotNull Properties loadConfigProperties() {
        Properties properties = new Properties();
        Path workingDirectoryConfig = Path.of("config.properties");

        if (Files.isRegularFile(workingDirectoryConfig)) {
            try (InputStream inputStream = Files.newInputStream(workingDirectoryConfig)) {
                properties.load(inputStream);
                return properties;
            } catch (IOException exception) {
                log.warn("Unable to load config.properties from working directory: {}", exception.getMessage());
            }
        }

        try (InputStream inputStream = AppConfig.class.getClassLoader().getResourceAsStream("config.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            log.warn("Unable to load config.properties from classpath: {}", exception.getMessage());
        }

        return properties;
    }

    private static @Nullable String getLegacyInvestProValue(@NotNull String key) {
        if (!key.startsWith("investpro.")) {
            return null;
        }

        String legacyKey = "trade" + "adviser" + key.substring("investpro".length());
        String systemPropertyValue = System.getProperty(legacyKey);
        if (hasText(systemPropertyValue)) {
            return systemPropertyValue;
        }

        String environmentValue = System.getenv(legacyKey);
        if (hasText(environmentValue)) {
            return environmentValue;
        }

        String dotenvValue = DOTENV.get(legacyKey);
        if (hasText(dotenvValue)) {
            return dotenvValue;
        }

        return CONFIG_PROPERTIES.getProperty(legacyKey);
    }
}
