package org.investpro.config;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Slf4j
public final class ProductionStartupValidator {

    private ProductionStartupValidator() {
    }

    public static @NotNull StartupValidationReport validateCurrentEnvironment() {
        return validate(AppConfig::get);
    }

    static @NotNull StartupValidationReport validate(@NotNull Function<String, String> configLookup) {
        Objects.requireNonNull(configLookup, "configLookup must not be null");

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String environment = value(configLookup, AppConfigKeys.APP_ENV, "development").toLowerCase(Locale.ROOT);
        boolean production = environment.equals("production") || environment.equals("prod");
        boolean strictValidation = production
                || parseBoolean(value(configLookup, AppConfigKeys.APP_STRICT_STARTUP_VALIDATION, "false"), false);

        if (!strictValidation) {
            return new StartupValidationReport(environment, false, List.of(), List.of());
        }

        if (parseBoolean(value(configLookup, AppConfigKeys.APP_DEBUG, "false"), false)) {
            errors.add("APP_DEBUG must be false when strict startup validation is enabled.");
        }

        if (missing(configLookup, AppConfigKeys.DEFAULT_ACCOUNT_MODE)) {
            warnings.add("DEFAULT_ACCOUNT_MODE is not set; safe startup default remains PAPER.");
        }

        if (missing(configLookup, "AUTO_TRADING_ENABLED")) {
            warnings.add("AUTO_TRADING_ENABLED is not set; safe startup default remains false.");
        }

        if (parseBoolean(value(configLookup, AppConfigKeys.TELEGRAM_ENABLED, "false"), false)
                && missing(configLookup, AppConfigKeys.TELEGRAM_TOKEN)) {
            errors.add("TELEGRAM_ENABLED is true but TELEGRAM_TOKEN is missing.");
        }

        if (parseBoolean(value(configLookup, AppConfigKeys.OPENAI_ENABLED, "false"), false)
                && missing(configLookup, AppConfigKeys.OPENAI_API_KEY)) {
            errors.add("OPENAI_ENABLED is true but OPENAI_API_KEY is missing.");
        }

        String accountMode = value(configLookup, AppConfigKeys.DEFAULT_ACCOUNT_MODE, "PAPER").toUpperCase(Locale.ROOT);
        boolean liveMode = !(accountMode.contains("PAPER") || accountMode.contains("DEMO")
                || accountMode.contains("SIM"));
        if (liveMode) {
            String defaultExchange = value(configLookup, AppConfigKeys.DEFAULT_EXCHANGE, "OANDA")
                    .toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace(' ', '_');
            validateLiveExchangeCredentials(configLookup, defaultExchange, errors, warnings);
        }

        return new StartupValidationReport(environment, production, List.copyOf(errors), List.copyOf(warnings));
    }

    private static void validateLiveExchangeCredentials(
            Function<String, String> configLookup,
            String exchange,
            List<String> errors,
            List<String> warnings) {
        Map<String, List<String>> requiredKeys = Map.of(
                "OANDA", List.of(AppConfigKeys.OANDA_API_KEY, AppConfigKeys.OANDA_ACCOUNT_ID),
                "BINANCE", List.of(AppConfigKeys.BINANCE_API_KEY, AppConfigKeys.BINANCE_API_SECRET),
                "BINANCE_US", List.of(AppConfigKeys.BINANCEUS_API_KEY, AppConfigKeys.BINANCEUS_API_SECRET),
                "ALPACA", List.of(AppConfigKeys.ALPACA_API_KEY, AppConfigKeys.ALPACA_API_SECRET),
                "KRAKEN", List.of(AppConfigKeys.KRAKEN_API_KEY, AppConfigKeys.KRAKEN_API_SECRET),
                "BYBIT", List.of(AppConfigKeys.BYBIT_API_KEY, AppConfigKeys.BYBIT_API_SECRET),
                "OKX",
                List.of(AppConfigKeys.OKX_API_KEY, AppConfigKeys.OKX_API_SECRET, AppConfigKeys.OKX_API_PASSPHRASE),
                "KUCOIN",
                List.of(AppConfigKeys.KUCOIN_API_KEY, AppConfigKeys.KUCOIN_API_SECRET,
                        AppConfigKeys.KUCOIN_API_PASSPHRASE),
                "STELLAR_NETWORK", List.of(AppConfigKeys.STELLAR_PUBLIC_KEY, AppConfigKeys.STELLAR_SECRET_KEY),
                "SCHWAB", List.of(AppConfigKeys.SCHWAB_CLIENT_ID, AppConfigKeys.SCHWAB_CLIENT_SECRET,
                        AppConfigKeys.SCHWAB_REFRESH_TOKEN));

        if ("COINBASE".equals(exchange)) {
            boolean hasKey = !missing(configLookup, AppConfigKeys.COINBASE_API_KEY);
            boolean hasSecretMaterial = !missing(configLookup, AppConfigKeys.COINBASE_API_SECRET)
                    || !missing(configLookup, AppConfigKeys.COINBASE_PRIVATE_KEY)
                    || !missing(configLookup, AppConfigKeys.COINBASE_PRIVATE_KEY_FILE);
            if (!hasKey || !hasSecretMaterial) {
                errors.add(
                        "Coinbase live mode requires COINBASE_API_KEY and one of COINBASE_API_SECRET, COINBASE_PRIVATE_KEY, or COINBASE_PRIVATE_KEY_FILE.");
            }
            return;
        }

        List<String> required = requiredKeys.get(exchange);
        if (required == null) {
            warnings.add("No strict live credential validation rule is defined for default exchange " + exchange + ".");
            return;
        }

        for (String key : required) {
            if (missing(configLookup, key)) {
                errors.add("Missing required live-trading configuration key: " + key + " for default exchange "
                        + exchange + ".");
            }
        }
    }

    private static boolean missing(Function<String, String> configLookup, String key) {
        return value(configLookup, key, "").isBlank();
    }

    private static String value(Function<String, String> configLookup, String key, String fallback) {
        String value = configLookup.apply(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private static boolean parseBoolean(String value, boolean fallback) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "yes", "y", "1", "on", "enabled" -> true;
            case "false", "no", "n", "0", "off", "disabled", "" -> false;
            default -> fallback;
        };
    }

    public record StartupValidationReport(
            @NotNull String environment,
            boolean production,
            @NotNull List<String> errors,
            @NotNull List<String> warnings) {

        public boolean isValid() {
            return errors.isEmpty();
        }

        public void logSummary() {
            warnings.forEach(warning -> log.warn("Startup validation warning: {}", warning));
            errors.forEach(error -> log.error("Startup validation error: {}", error));
        }

        public @NotNull String failureMessage() {
            if (errors.isEmpty()) {
                return "";
            }
            return "Production startup validation failed:%n - %s".formatted(String.join("%n - ", errors));
        }
    }
}
