package org.investpro.spi;

import lombok.extern.slf4j.Slf4j;
import org.investpro.indicators.ChartIndicator;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Slf4j
public final class PluginIndicatorFactory {

    private PluginIndicatorFactory() {
    }

    public static Optional<ChartIndicator> create(String choice, PluginRegistry pluginRegistry) {
        if (choice == null || choice.isBlank()) {
            return Optional.empty();
        }

        PluginRegistry registry = pluginRegistry == null ? PluginRegistry.loadDefault() : pluginRegistry;
        IndicatorRequest request = parse(choice);

        return registry.findIndicatorProvider(request.providerId())
                .map(provider -> {
                    try {
                        return provider.create(new IndicatorProviderContext(null, null, request.config()));
                    } catch (Exception exception) {
                        log.warn("Indicator provider failed for choice '{}': {}", choice, exception.getMessage(), exception);
                        return null;
                    }
                });
    }

    private static IndicatorRequest parse(String choice) {
        String normalized = choice.trim().toUpperCase(Locale.ROOT);
        Map<String, String> config = new LinkedHashMap<>();

        if (normalized.startsWith("SMA")) {
            putTrailingPeriod(normalized, config);
            return new IndicatorRequest("SMA", config);
        }
        if (normalized.startsWith("EMA")) {
            putTrailingPeriod(normalized, config);
            return new IndicatorRequest("EMA", config);
        }
        if (normalized.startsWith("RSI")) {
            putTrailingPeriod(normalized, config);
            return new IndicatorRequest("RSI", config);
        }
        if (normalized.startsWith("ATR")) {
            putTrailingPeriod(normalized, config);
            return new IndicatorRequest("ATR", config);
        }
        if (normalized.equals("BOLLINGER BANDS") || normalized.equals("BOLLINGER_BANDS")) {
            return new IndicatorRequest("BOLLINGER_BANDS", config);
        }
        if (normalized.equals("MACD")) {
            return new IndicatorRequest("MACD", config);
        }
        if (normalized.equals("VWAP")) {
            return new IndicatorRequest("VWAP", config);
        }

        return new IndicatorRequest(normalized, config);
    }

    private static void putTrailingPeriod(String normalized, Map<String, String> config) {
        String[] parts = normalized.split("\\s+");
        if (parts.length < 2) {
            return;
        }
        try {
            int period = Integer.parseInt(parts[parts.length - 1]);
            config.put("period", String.valueOf(period));
        } catch (NumberFormatException ignored) {
            // Indicator provider defaults are used when no explicit period is present.
        }
    }

    private record IndicatorRequest(String providerId, Map<String, String> config) {
    }
}
