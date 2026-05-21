package org.investpro.spi;

import org.investpro.data.CandleData;
import org.investpro.ui.tools.ChartOptions;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record IndicatorProviderContext(
        List<CandleData> candles,
        ChartOptions chartOptions,
        Map<String, String> config) {

    public IndicatorProviderContext {
        candles = candles == null ? List.of() : List.copyOf(candles);
        config = config == null ? Map.of() : Map.copyOf(config);
    }

    public Optional<String> configValue(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(config.get(key));
    }

    public int intConfig(String key, int fallback) {
        return configValue(key)
                .flatMap(value -> {
                    try {
                        return Optional.of(Integer.parseInt(value.trim()));
                    } catch (NumberFormatException exception) {
                        return Optional.empty();
                    }
                })
                .orElse(fallback);
    }

    public double doubleConfig(String key, double fallback) {
        return configValue(key)
                .flatMap(value -> {
                    try {
                        return Optional.of(Double.parseDouble(value.trim()));
                    } catch (NumberFormatException exception) {
                        return Optional.empty();
                    }
                })
                .orElse(fallback);
    }
}
