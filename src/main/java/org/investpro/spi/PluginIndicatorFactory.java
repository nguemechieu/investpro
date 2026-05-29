package org.investpro.spi;

import lombok.extern.slf4j.Slf4j;
import org.investpro.indicators.ADX;
import org.investpro.indicators.ATR;
import org.investpro.indicators.BollingerBands;
import org.investpro.indicators.CCI;
import org.investpro.indicators.Indicator;
import org.investpro.indicators.ExponentialMovingAverage;
import org.investpro.indicators.FibonacciRetracement;
import org.investpro.indicators.Fractal;
import org.investpro.indicators.Ichimoku;
import org.investpro.indicators.MACD;
import org.investpro.indicators.OBV;
import org.investpro.indicators.ParabolicSAR;
import org.investpro.indicators.RSI;
import org.investpro.indicators.SimpleMovingAverage;
import org.investpro.indicators.Stochastic;
import org.investpro.indicators.Volatility;
import org.investpro.indicators.Volume;
import org.investpro.indicators.VWAP;
import org.investpro.indicators.Zigzag;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.prefs.Preferences;

@Slf4j
public final class PluginIndicatorFactory {

    private static final Preferences PREFS = Preferences.userNodeForPackage(PluginIndicatorFactory.class)
            .node("indicator-config");
    private static final List<String> SUPPORTED_CHOICES = List.of(
            "SMA 20",
            "SMA 50",
            "SMA 200",
            "EMA 12",
            "EMA 26",
            "RSI 14",
            "Stochastic",
            "CCI 20",
            "MACD",
            "Bollinger Bands",
            "ATR 14",
            "VWAP",
            "OBV",
            "Volume",
            "ADX 14",
            "Ichimoku",
            "Parabolic SAR",
            "Fibonacci Retracement",
            "Zigzag",
            "Fractal",
            "Volatility");

    private PluginIndicatorFactory() {
    }

    public static List<String> supportedChoices() {
        return SUPPORTED_CHOICES;
    }

    public static List<IndicatorParameter> parametersFor(String choice) {
        return switch (normalize(choice)) {
            case "SMA 20", "SMA 50", "SMA 200", "EMA 12", "EMA 26", "RSI 14", "CCI 20", "ATR 14", "ADX 14" -> List.of(
                    new IndicatorParameter("period", "Period", IndicatorValueType.INTEGER,
                            String.valueOf(defaultPeriodValue(choice)), "Lookback period"));
            case "STOCHASTIC" -> List.of(
                    new IndicatorParameter("kPeriod", "%K Period", IndicatorValueType.INTEGER, "14",
                            "Fast stochastic lookback"),
                    new IndicatorParameter("kSlowPeriod", "%K Smoothing", IndicatorValueType.INTEGER, "3",
                            "Smoothing for %K"),
                    new IndicatorParameter("dPeriod", "%D Period", IndicatorValueType.INTEGER, "3",
                            "Signal period for %D"));
            case "MACD" -> List.of(
                    new IndicatorParameter("fastPeriod", "Fast Period", IndicatorValueType.INTEGER, "12",
                            "Fast EMA length"),
                    new IndicatorParameter("slowPeriod", "Slow Period", IndicatorValueType.INTEGER, "26",
                            "Slow EMA length"),
                    new IndicatorParameter("signalPeriod", "Signal Period", IndicatorValueType.INTEGER, "9",
                            "Signal EMA length"));
            case "BOLLINGER BANDS" -> List.of(
                    new IndicatorParameter("period", "Period", IndicatorValueType.INTEGER, "20",
                            "Moving average lookback"),
                    new IndicatorParameter("stdDevMultiplier", "Std Dev Multiplier", IndicatorValueType.DOUBLE, "2.0",
                            "Band width multiplier"));
            case "ICHIMOKU" -> List.of(
                    new IndicatorParameter("conversionPeriod", "Conversion Period", IndicatorValueType.INTEGER, "9",
                            "Tenkan-sen lookback"),
                    new IndicatorParameter("basePeriod", "Base Period", IndicatorValueType.INTEGER, "26",
                            "Kijun-sen lookback"),
                    new IndicatorParameter("leadingSpanPeriod", "Leading Span Period", IndicatorValueType.INTEGER, "52",
                            "Senkou Span B lookback"));
            case "PARABOLIC SAR" -> List.of(
                    new IndicatorParameter("initialAF", "Initial AF", IndicatorValueType.DOUBLE, "0.02",
                            "Acceleration factor start"),
                    new IndicatorParameter("maxAF", "Max AF", IndicatorValueType.DOUBLE, "0.2",
                            "Acceleration factor cap"));
            case "FIBONACCI RETRACEMENT" -> List.of(
                    new IndicatorParameter("lookbackPeriod", "Lookback Period", IndicatorValueType.INTEGER, "20",
                            "Range used to derive levels"));
            case "ZIGZAG" -> List.of(
                    new IndicatorParameter("thresholdPercent", "Threshold %", IndicatorValueType.DOUBLE, "5.0",
                            "Minimum swing percentage"));
            case "FRACTAL" -> List.of(
                    new IndicatorParameter("lookback", "Lookback", IndicatorValueType.INTEGER, "5",
                            "Bars used to detect fractals"));
            default -> List.of();
        };
    }

    public static Map<String, String> defaultConfig(String choice) {
        Map<String, String> defaults = new LinkedHashMap<>();
        for (IndicatorParameter parameter : parametersFor(choice)) {
            defaults.put(parameter.key(), parameter.defaultValue());
        }
        return defaults;
    }

    public static Map<String, String> loadConfig(String choice) {
        Map<String, String> config = defaultConfig(choice);
        Preferences node = PREFS.node(configKey(choice));
        try {
            for (String key : node.keys()) {
                config.put(key, node.get(key, config.get(key)));
            }
        } catch (Exception exception) {
            log.debug("Failed to load indicator config for {}", choice, exception);
        }
        return config;
    }

    public static void saveConfig(String choice, Map<String, String> config) {
        Preferences node = PREFS.node(configKey(choice));
        Map<String, String> merged = new LinkedHashMap<>(defaultConfig(choice));
        if (config != null) {
            merged.putAll(config);
        }
        for (Map.Entry<String, String> entry : merged.entrySet()) {
            node.put(entry.getKey(), entry.getValue());
        }
    }

    public static Optional<Indicator> create(String choice, PluginRegistry pluginRegistry) {
        return create(choice, pluginRegistry, loadConfig(choice));
    }

    public static Optional<Indicator> create(String choice, PluginRegistry pluginRegistry, Map<String, String> config) {
        if (choice == null || choice.isBlank()) {
            return Optional.empty();
        }

        PluginRegistry registry = pluginRegistry == null ? PluginRegistry.loadDefault() : pluginRegistry;
        Map<String, String> mergedConfig = mergeConfig(choice, config);
        String providerId = providerId(choice);

        return registry.findIndicatorProvider(providerId)
                .map(provider -> {
                    try {
                        return provider.create(new IndicatorProviderContext(null, null, mergedConfig));
                    } catch (Exception exception) {
                        log.warn("Indicator provider failed for choice '{}': {}", choice, exception.getMessage(),
                                exception);
                        return null;
                    }
                })
                .or(() -> createFallback(choice, mergedConfig));
    }

    private static Optional<Indicator> createFallback(String choice, Map<String, String> config) {
        try {
            return Optional.ofNullable(switch (normalize(choice)) {
                case "SMA 20", "SMA 50", "SMA 200" ->
                    new SimpleMovingAverage(intConfig(config, "period", defaultPeriodValue(choice)));
                case "EMA 12", "EMA 26" ->
                    new ExponentialMovingAverage(intConfig(config, "period", defaultPeriodValue(choice)));
                case "RSI 14" -> new RSI(intConfig(config, "period", 14));
                case "STOCHASTIC" -> new Stochastic(
                        intConfig(config, "kPeriod", 14),
                        intConfig(config, "kSlowPeriod", 3),
                        intConfig(config, "dPeriod", 3));
                case "CCI 20" -> new CCI(intConfig(config, "period", 20));
                case "MACD" -> new MACD(
                        intConfig(config, "fastPeriod", 12),
                        intConfig(config, "slowPeriod", 26),
                        intConfig(config, "signalPeriod", 9));
                case "BOLLINGER BANDS" -> new BollingerBands(
                        intConfig(config, "period", 20),
                        doubleConfig(config, "stdDevMultiplier", 2.0));
                case "ATR 14" -> new ATR(intConfig(config, "period", 14));
                case "VWAP" -> new VWAP();
                case "OBV" -> new OBV();
                case "VOLUME" -> new Volume();
                case "ADX 14" -> new ADX(intConfig(config, "period", 14));
                case "ICHIMOKU" -> new Ichimoku(
                        intConfig(config, "conversionPeriod", 9),
                        intConfig(config, "basePeriod", 26),
                        intConfig(config, "leadingSpanPeriod", 52));
                case "PARABOLIC SAR" -> new ParabolicSAR(
                        doubleConfig(config, "initialAF", 0.02),
                        doubleConfig(config, "maxAF", 0.2));
                case "FIBONACCI RETRACEMENT" -> new FibonacciRetracement(intConfig(config, "lookbackPeriod", 20));
                case "ZIGZAG" -> new Zigzag(doubleConfig(config, "thresholdPercent", 5.0));
                case "FRACTAL" -> new Fractal(intConfig(config, "lookback", 5));
                case "VOLATILITY" -> new Volatility();
                default -> null;
            });
        } catch (Exception exception) {
            log.warn("Indicator fallback failed for '{}': {}", choice, exception.getMessage(), exception);
            return Optional.empty();
        }
    }

    private static Map<String, String> mergeConfig(String choice, Map<String, String> config) {
        Map<String, String> merged = new LinkedHashMap<>(defaultConfig(choice));
        if (config != null) {
            merged.putAll(config);
        }
        return merged;
    }

    private static int defaultPeriodValue(String choice) {
        String normalized = normalize(choice);
        if (normalized.endsWith(" 12")) {
            return 12;
        }
        if (normalized.endsWith(" 14")) {
            return 14;
        }
        if (normalized.endsWith(" 20")) {
            return 20;
        }
        if (normalized.endsWith(" 26")) {
            return 26;
        }
        if (normalized.endsWith(" 50")) {
            return 50;
        }
        if (normalized.endsWith(" 200")) {
            return 200;
        }
        return 20;
    }

    private static int intConfig(Map<String, String> config, String key, int defaultValue) {
        try {
            return Integer.parseInt(config.getOrDefault(key, String.valueOf(defaultValue)).trim());
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private static double doubleConfig(Map<String, String> config, String key, double defaultValue) {
        try {
            return Double.parseDouble(config.getOrDefault(key, String.valueOf(defaultValue)).trim());
        } catch (Exception exception) {
            return defaultValue;
        }
    }

    private static String providerId(String choice) {
        return switch (normalize(choice)) {
            case "SMA 20", "SMA 50", "SMA 200" -> "SMA";
            case "EMA 12", "EMA 26" -> "EMA";
            case "RSI 14" -> "RSI";
            case "CCI 20" -> "CCI";
            case "ATR 14" -> "ATR";
            case "BOLLINGER BANDS" -> "BOLLINGER_BANDS";
            case "MACD" -> "MACD";
            case "VWAP" -> "VWAP";
            default -> normalize(choice).replace(' ', '_');
        };
    }

    private static String normalize(String choice) {
        return choice == null ? "" : choice.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
    }

    private static String configKey(String choice) {
        return normalize(choice).replace(' ', '_');
    }

    private record IndicatorRequest(String providerId, Map<String, String> config) {
    }

    public record IndicatorParameter(String key, String label, IndicatorValueType type, String defaultValue,
            String description) {
    }

    public enum IndicatorValueType {
        INTEGER,
        DOUBLE,
        TEXT,
        BOOLEAN
    }
}
