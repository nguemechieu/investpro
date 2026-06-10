package org.investpro.spi;

import lombok.extern.slf4j.Slf4j;
import org.investpro.indicators.ADX;
import org.investpro.indicators.ATR;
import org.investpro.indicators.BollingerBands;
import org.investpro.indicators.CCI;
import org.investpro.indicators.EngineBackedIndicator;
import org.investpro.indicators.Indicator;
import org.investpro.indicators.IndicatorCatalog;
import org.investpro.indicators.INDICATORS;
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
import org.investpro.indicators.metadata.IndicatorParameterDefinition;

import java.util.LinkedHashSet;
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

    private PluginIndicatorFactory() {
    }

    public static List<String> supportedChoices() {
        LinkedHashSet<String> choices = new LinkedHashSet<>();
        for (INDICATORS indicator : IndicatorCatalog.allIndicators()) {
            choices.add(IndicatorCatalog.get(indicator).displayName());
        }
        return List.copyOf(choices);
    }

    public static List<IndicatorParameter> parametersFor(String choice) {
        Optional<INDICATORS> parsed = parseChoiceIndicator(choice);
        if (parsed.isEmpty()) {
            return List.of();
        }

        int explicitPeriod = explicitPeriod(choice);
        return IndicatorCatalog.get(parsed.get()).parameters().stream()
                .map(parameter -> {
                    String defaultValue = parameter.defaultValue();
                    if (explicitPeriod > 0 && "period".equalsIgnoreCase(parameter.name())) {
                        defaultValue = String.valueOf(explicitPeriod);
                    }
                    return new IndicatorParameter(
                            parameter.name(),
                            parameter.displayName() == null || parameter.displayName().isBlank()
                                    ? humanizeKey(parameter.name())
                                    : parameter.displayName(),
                            parseValueType(parameter),
                            defaultValue,
                            parameter.description() == null ? "" : parameter.description());
                })
                .toList();
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
            Optional<Indicator> fallback = Optional.ofNullable(switch (normalize(choice)) {
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
            if (fallback.isPresent()) {
                return fallback;
            }

            return parseChoiceIndicator(choice)
                    .map(indicator -> (Indicator) new EngineBackedIndicator(indicator, mergeConfig(choice, config)));
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
        int explicitPeriod = explicitPeriod(choice);
        if (explicitPeriod > 0) {
            merged.put("period", String.valueOf(explicitPeriod));
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

    private static Optional<INDICATORS> parseChoiceIndicator(String choice) {
        Optional<INDICATORS> parsed = IndicatorCatalog.parse(choice);
        if (parsed.isPresent()) {
            return parsed;
        }

        return switch (normalize(choice)) {
            case "SMA 20", "SMA 50", "SMA 200" -> Optional.of(INDICATORS.SMA);
            case "EMA 12", "EMA 26" -> Optional.of(INDICATORS.EMA);
            case "RSI 14" -> Optional.of(INDICATORS.RSI);
            case "CCI 20" -> Optional.of(INDICATORS.CCI);
            case "ATR 14" -> Optional.of(INDICATORS.ATR);
            case "ADX 14" -> Optional.of(INDICATORS.ADX);
            default -> Optional.empty();
        };
    }

    private static int explicitPeriod(String choice) {
        if (choice == null || choice.isBlank()) {
            return -1;
        }
        String[] tokens = choice.trim().split("\\s+");
        if (tokens.length == 0) {
            return -1;
        }
        String tail = tokens[tokens.length - 1];
        try {
            return Integer.parseInt(tail);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    private static IndicatorValueType parseValueType(IndicatorParameterDefinition parameter) {
        String type = parameter.type() == null ? "" : parameter.type().trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "INTEGER", "INT", "LONG" -> IndicatorValueType.INTEGER;
            case "DOUBLE", "FLOAT", "DECIMAL", "NUMBER" -> IndicatorValueType.DOUBLE;
            case "BOOLEAN", "BOOL" -> IndicatorValueType.BOOLEAN;
            default -> {
                String defaultValue = parameter.defaultValue() == null ? "" : parameter.defaultValue().trim();
                if (defaultValue.matches("[-+]?\\d+")) {
                    yield IndicatorValueType.INTEGER;
                }
                if (defaultValue.matches("[-+]?\\d*\\.\\d+")) {
                    yield IndicatorValueType.DOUBLE;
                }
                if ("true".equalsIgnoreCase(defaultValue) || "false".equalsIgnoreCase(defaultValue)) {
                    yield IndicatorValueType.BOOLEAN;
                }
                yield IndicatorValueType.TEXT;
            }
        };
    }

    private static String humanizeKey(String key) {
        if (key == null || key.isBlank()) {
            return "Parameter";
        }
        String normalized = key.replace('_', ' ');
        StringBuilder builder = new StringBuilder(normalized.length());
        char[] chars = normalized.toCharArray();
        for (int index = 0; index < chars.length; index++) {
            char current = chars[index];
            if (index > 0 && Character.isUpperCase(current) && Character.isLowerCase(chars[index - 1])) {
                builder.append(' ');
            }
            builder.append(current);
        }
        String spaced = builder.toString().trim();
        if (spaced.isEmpty()) {
            return "Parameter";
        }
        return Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
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
