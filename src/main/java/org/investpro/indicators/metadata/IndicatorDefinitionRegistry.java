package org.investpro.indicators.metadata;

import org.investpro.indicators.INDICATORS;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class IndicatorDefinitionRegistry {

    private static final Map<INDICATORS, IndicatorDefinition> DEFINITIONS = buildDefinitions();

    private IndicatorDefinitionRegistry() {
    }

    public static Optional<IndicatorDefinition> find(INDICATORS indicator) {
        return Optional.ofNullable(indicator == null ? null : DEFINITIONS.get(indicator));
    }

    public static IndicatorDefinition get(INDICATORS indicator) {
        return find(indicator).orElseGet(() -> fallback(indicator == null ? INDICATORS.UNKNOWN : indicator));
    }

    public static List<IndicatorDefinition> all() {
        return Arrays.stream(INDICATORS.values())
                .map(IndicatorDefinitionRegistry::get)
                .toList();
    }

    private static Map<INDICATORS, IndicatorDefinition> buildDefinitions() {
        Map<INDICATORS, IndicatorDefinition> definitions = new EnumMap<>(INDICATORS.class);

        put(definitions, INDICATORS.APO, outputs("apo"), p("fastPeriod", "12"), p("slowPeriod", "26"), p("maType", "EMA"));
        put(definitions, INDICATORS.AROON, outputs("aroonUp", "aroonDown"), p("period", "25"));
        put(definitions, INDICATORS.AROON_OSCILLATOR, outputs("aroonOscillator"), p("period", "25"));
        put(definitions, INDICATORS.ADX, outputs("adx"), p("period", "14"));
        put(definitions, INDICATORS.ATR, outputs("atr"), p("period", "14"));
        put(definitions, INDICATORS.BOLLINGER_BANDS, outputs("upper", "middle", "lower", "bandwidth", "percentB"), p("period", "20"), p("stdDevMult", "2.0"));
        put(definitions, INDICATORS.CHAIKIN_AD_OSCILLATOR, outputs("chaikinOscillator"), p("fastPeriod", "3"), p("slowPeriod", "10"));
        put(definitions, INDICATORS.CCI, outputs("cci"), p("period", "20"));
        put(definitions, INDICATORS.DMI, outputs("plusDI", "minusDI", "adx"), p("period", "14"));
        put(definitions, INDICATORS.DEMA, outputs("dema"), p("period", "20"));
        put(definitions, INDICATORS.ELDER_RAY, outputs("bullPower", "bearPower"), p("period", "13"));
        put(definitions, INDICATORS.EMA, outputs("ema"), p("period", "20"));
        put(definitions, INDICATORS.HMA, outputs("hma"), p("period", "20"));
        put(definitions, INDICATORS.ICHIMOKU, outputs("tenkan", "kijun", "senkouA", "senkouB", "chikou"), p("tenkan", "9"), p("kijun", "26"), p("senkouB", "52"), p("displacement", "26"));
        put(definitions, INDICATORS.KAMA, outputs("kama"), p("period", "10"), p("fastPeriod", "2"), p("slowPeriod", "30"));
        put(definitions, INDICATORS.MESA_ADAPTIVE_MOVING_AVERAGE, outputs("mama", "fama"), p("fastLimit", "0.5"), p("slowLimit", "0.05"));
        put(definitions, INDICATORS.MOMENTUM, outputs("momentum"), p("period", "10"));
        put(definitions, INDICATORS.MFI, outputs("mfi"), p("period", "14"));
        put(definitions, INDICATORS.MACD, outputs("macd", "signal", "histogram"), p("fastPeriod", "12"), p("slowPeriod", "26"), p("signalPeriod", "9"));
        put(definitions, INDICATORS.MACD_LINE, outputs("macd", "signal", "histogram"), p("fastPeriod", "12"), p("slowPeriod", "26"), p("signalPeriod", "9"));
        put(definitions, INDICATORS.MACD_SIGNAL, outputs("macd", "signal", "histogram"), p("fastPeriod", "12"), p("slowPeriod", "26"), p("signalPeriod", "9"));
        put(definitions, INDICATORS.MACD_HISTOGRAM, outputs("macd", "signal", "histogram"), p("fastPeriod", "12"), p("slowPeriod", "26"), p("signalPeriod", "9"));
        put(definitions, INDICATORS.OBV, outputs("obv"));
        put(definitions, INDICATORS.PARABOLIC_SAR, outputs("psar"), p("step", "0.02"), p("maxStep", "0.2"));
        put(definitions, INDICATORS.PSAR, outputs("psar"), p("step", "0.02"), p("maxStep", "0.2"));
        put(definitions, INDICATORS.PERCENT_CHANGE, outputs("percentChange"), p("period", "1"));
        put(definitions, INDICATORS.PPO, outputs("ppo", "signal", "histogram"), p("fastPeriod", "12"), p("slowPeriod", "26"), p("signalPeriod", "9"));
        put(definitions, INDICATORS.ROC, outputs("roc"), p("period", "12"));
        put(definitions, INDICATORS.RSI, outputs("rsi"), p("period", "14"), p("oversold", "30"), p("overbought", "70"));
        put(definitions, INDICATORS.RSI_REGION_CROSSOVER, outputs("rsi", "crossoverSignal"), p("period", "14"), p("oversold", "30"), p("overbought", "70"), p("crossoverMode", "REGION_EXIT"));
        put(definitions, INDICATORS.SMA, outputs("sma"), p("period", "20"));
        put(definitions, INDICATORS.STOCH_RSI_REGION_CROSSOVER, outputs("stochRsiK", "stochRsiD", "crossoverSignal"), p("rsiPeriod", "14"), p("stochPeriod", "14"), p("kSmooth", "3"), p("dSmooth", "3"), p("oversold", "20"), p("overbought", "80"), p("crossoverMode", "REGION_EXIT"));
        put(definitions, INDICATORS.STOCHASTIC, outputs("percentK", "percentD"), p("kPeriod", "14"), p("dPeriod", "3"), p("smooth", "3"));
        put(definitions, INDICATORS.STOCHASTIC_RSI, outputs("stochRsiK", "stochRsiD"), p("rsiPeriod", "14"), p("stochPeriod", "14"), p("kSmooth", "3"), p("dSmooth", "3"));
        put(definitions, INDICATORS.STOCH_RSI, outputs("stochRsiK", "stochRsiD"), p("rsiPeriod", "14"), p("stochPeriod", "14"), p("kSmooth", "3"), p("dSmooth", "3"));
        put(definitions, INDICATORS.STOCHASTIC_REGION_CROSSOVER, outputs("percentK", "percentD", "crossoverSignal"), p("kPeriod", "14"), p("dPeriod", "3"), p("smooth", "3"), p("oversold", "20"), p("overbought", "80"), p("crossoverMode", "REGION_EXIT"));
        put(definitions, INDICATORS.T3_TILLSON, outputs("t3"), p("period", "5"), p("volumeFactor", "0.7"));
        put(definitions, INDICATORS.TIME_SERIES_FORECAST, outputs("tsf"), p("period", "14"));
        put(definitions, INDICATORS.TMA, outputs("tma"), p("period", "20"));
        put(definitions, INDICATORS.TEMA, outputs("tema"), p("period", "20"));
        put(definitions, INDICATORS.ULTIMATE_OSCILLATOR, outputs("ultimateOscillator"), p("period1", "7"), p("period2", "14"), p("period3", "28"));
        put(definitions, INDICATORS.WMA, outputs("wma"), p("period", "20"));
        put(definitions, INDICATORS.WILLIAMS_R, outputs("williamsR"), p("period", "14"), p("oversold", "-80"), p("overbought", "-20"));

        for (INDICATORS indicator : INDICATORS.values()) {
            definitions.putIfAbsent(indicator, fallback(indicator));
        }
        return Map.copyOf(definitions);
    }

    private static void put(Map<INDICATORS, IndicatorDefinition> definitions, INDICATORS indicator, List<String> outputs, IndicatorParameterDefinition... parameters) {
        definitions.put(indicator, new IndicatorDefinition(
                indicator,
                indicator.getDisplayName(),
                indicator.getDescription(),
                indicator.getCategory(),
                List.of(parameters),
                outputs));
    }

    private static IndicatorDefinition fallback(INDICATORS indicator) {
        return new IndicatorDefinition(
                indicator,
                indicator.getDisplayName(),
                indicator.getDescription() == null || indicator.getDescription().isBlank()
                        ? indicator.getDisplayName()
                        : indicator.getDescription(),
                indicator.getCategory(),
                List.of(),
                outputs(defaultOutput(indicator)));
    }

    private static IndicatorParameterDefinition p(String name, String defaultValue) {
        return new IndicatorParameterDefinition(name, display(name), defaultValue, inferType(defaultValue), display(name), true);
    }

    private static List<String> outputs(String... values) {
        return List.of(values);
    }

    private static String defaultOutput(INDICATORS indicator) {
        return indicator.name().toLowerCase();
    }

    private static String display(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (char ch : name.toCharArray()) {
            if (Character.isUpperCase(ch) && !builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(builder.isEmpty() ? Character.toUpperCase(ch) : ch);
        }
        return builder.toString();
    }

    private static String inferType(String value) {
        if (value == null) {
            return "STRING";
        }
        try {
            Integer.parseInt(value);
            return "INTEGER";
        } catch (NumberFormatException ignored) {
            try {
                Double.parseDouble(value);
                return "DOUBLE";
            } catch (NumberFormatException ignoredAgain) {
                return "STRING";
            }
        }
    }
}
