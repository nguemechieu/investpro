package org.investpro.indicators;

import org.investpro.indicators.metadata.IndicatorDefinition;
import org.investpro.indicators.metadata.IndicatorParameterDefinition;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.LinkedHashMap;

public final class IndicatorCatalog {

        private IndicatorCatalog() {
        }

        public enum IndicatorRenderTarget {
                PRICE_OVERLAY,
                SEPARATE_PANE,
                BOTH
        }

        private static final Map<INDICATORS, IndicatorDefinition> DEFINITIONS = buildDefinitions();

        public static Optional<IndicatorDefinition> find(INDICATORS indicator) {
                if (indicator == null) {
                        return Optional.empty();
                }

                return Optional.ofNullable(DEFINITIONS.get(indicator));
        }

        public static IndicatorDefinition get(INDICATORS indicator) {
                INDICATORS safeIndicator = indicator == null ? INDICATORS.UNKNOWN : indicator;
                return DEFINITIONS.getOrDefault(safeIndicator, fallback(safeIndicator));
        }

        public static List<IndicatorDefinition> all() {
                return Arrays.stream(INDICATORS.values())
                                .filter(indicator -> indicator != INDICATORS.UNKNOWN)
                                .map(IndicatorCatalog::get)
                                .toList();
        }

        public static List<INDICATORS> allIndicators() {
                return Arrays.stream(INDICATORS.values())
                                .filter(indicator -> indicator != INDICATORS.UNKNOWN)
                                .toList();
        }

        public static boolean contains(INDICATORS indicator) {
                return indicator != null && DEFINITIONS.containsKey(indicator);
        }

        public static boolean isPriceOverlay(INDICATORS indicator) {
                return find(indicator)
                                .map(definition -> definition.renderTarget() == IndicatorRenderTarget.PRICE_OVERLAY
                                                || definition.renderTarget() == IndicatorRenderTarget.BOTH)
                                .orElse(false);
        }

        public static boolean isPriceOverlay(String indicatorName) {
                return parse(indicatorName)
                                .map(IndicatorCatalog::isPriceOverlay)
                                .orElse(false);
        }

        public static boolean isSeparatePane(INDICATORS indicator) {
                return find(indicator)
                                .map(definition -> definition.renderTarget() == IndicatorRenderTarget.SEPARATE_PANE)
                                .orElse(false);
        }

        public static Map<String, String> defaultParams(INDICATORS indicator) {
                Map<String, String> defaults = new LinkedHashMap<>();

                find(indicator).ifPresent(definition -> definition.parameters()
                                .forEach(parameter -> defaults.put(parameter.name(), parameter.defaultValue())));

                return defaults;
        }

        public static Optional<INDICATORS> parse(String name) {
                String normalized = normalize(name);

                if (normalized.isBlank()) {
                        return Optional.empty();
                }

                return Arrays.stream(INDICATORS.values())
                                .filter(indicator -> normalize(indicator.name()).equals(normalized)
                                                || normalize(indicator.getDisplayName()).equals(normalized))
                                .findFirst();
        }

        public static String normalize(String name) {
                if (name == null) {
                        return "";
                }

                return name
                                .trim()
                                .replaceAll("\\s+", "_")
                                .replace("-", "_")
                                .replace("/", "_")
                                .replace("%", "PERCENT")
                                .replace("(", "")
                                .replace(")", "")
                                .toUpperCase(Locale.ROOT);
        }

        private static Map<INDICATORS, IndicatorDefinition> buildDefinitions() {
                Map<INDICATORS, IndicatorDefinition> definitions = new EnumMap<>(INDICATORS.class);

                put(definitions, INDICATORS.SMA,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("sma"),
                                p("period", "20"));

                put(definitions, INDICATORS.EMA,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("ema"),
                                p("period", "20"));

                put(definitions, INDICATORS.WMA,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("wma"),
                                p("period", "20"));

                put(definitions, INDICATORS.HMA,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("hma"),
                                p("period", "20"));

                put(definitions, INDICATORS.DEMA,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("dema"),
                                p("period", "20"));

                put(definitions, INDICATORS.TEMA,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("tema"),
                                p("period", "20"));

                put(definitions, INDICATORS.TMA,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("tma"),
                                p("period", "20"));

                put(definitions, INDICATORS.KAMA,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("kama"),
                                p("period", "10"),
                                p("fastPeriod", "2"),
                                p("slowPeriod", "30"));

                put(definitions, INDICATORS.MESA_ADAPTIVE_MOVING_AVERAGE,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("mama", "fama"),
                                p("fastLimit", "0.5"),
                                p("slowLimit", "0.05"));

                put(definitions, INDICATORS.T3_TILLSON,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("t3"),
                                p("period", "5"),
                                p("volumeFactor", "0.7"));

                put(definitions, INDICATORS.RSI,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("rsi"),
                                p("period", "14"),
                                p("oversold", "30"),
                                p("overbought", "70"));

                put(definitions, INDICATORS.RSI_REGION_CROSSOVER,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("rsi", "crossoverSignal"),
                                p("period", "14"),
                                p("oversold", "30"),
                                p("overbought", "70"),
                                p("crossoverMode", "REGION_EXIT"));

                put(definitions, INDICATORS.MACD,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("macd", "signal", "histogram"),
                                p("fastPeriod", "12"),
                                p("slowPeriod", "26"),
                                p("signalPeriod", "9"));

                put(definitions, INDICATORS.MACD_LINE,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("macd"),
                                p("fastPeriod", "12"),
                                p("slowPeriod", "26"));

                put(definitions, INDICATORS.MACD_SIGNAL,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("signal"),
                                p("signalPeriod", "9"));

                put(definitions, INDICATORS.MACD_HISTOGRAM,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("histogram"),
                                p("fastPeriod", "12"),
                                p("slowPeriod", "26"),
                                p("signalPeriod", "9"));

                put(definitions, INDICATORS.MOMENTUM,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("momentum"),
                                p("period", "10"));

                put(definitions, INDICATORS.ROC,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("roc"),
                                p("period", "12"));

                put(definitions, INDICATORS.PERCENT_CHANGE,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("percentChange"),
                                p("period", "1"));

                put(definitions, INDICATORS.PPO,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("ppo", "signal", "histogram"),
                                p("fastPeriod", "12"),
                                p("slowPeriod", "26"),
                                p("signalPeriod", "9"));

                put(definitions, INDICATORS.APO,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("apo"),
                                p("fastPeriod", "12"),
                                p("slowPeriod", "26"),
                                p("maType", "EMA"));

                put(definitions, INDICATORS.CCI,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("cci"),
                                p("period", "20"));

                put(definitions, INDICATORS.WILLIAMS_R,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("williamsR"),
                                p("period", "14"),
                                p("oversold", "-80"),
                                p("overbought", "-20"));

                put(definitions, INDICATORS.ULTIMATE_OSCILLATOR,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("ultimateOscillator"),
                                p("period1", "7"),
                                p("period2", "14"),
                                p("period3", "28"));

                put(definitions, INDICATORS.STOCHASTIC,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("percentK", "percentD"),
                                p("kPeriod", "14"),
                                p("dPeriod", "3"),
                                p("smooth", "3"));

                put(definitions, INDICATORS.STOCHASTIC_RSI,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("stochRsiK", "stochRsiD"),
                                p("rsiPeriod", "14"),
                                p("stochPeriod", "14"),
                                p("kSmooth", "3"),
                                p("dSmooth", "3"));

                put(definitions, INDICATORS.STOCH_RSI,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("stochRsiK", "stochRsiD"),
                                p("rsiPeriod", "14"),
                                p("stochPeriod", "14"),
                                p("kSmooth", "3"),
                                p("dSmooth", "3"));

                put(definitions, INDICATORS.STOCH_RSI_REGION_CROSSOVER,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("stochRsiK", "stochRsiD", "crossoverSignal"),
                                p("rsiPeriod", "14"),
                                p("stochPeriod", "14"),
                                p("kSmooth", "3"),
                                p("dSmooth", "3"),
                                p("oversold", "20"),
                                p("overbought", "80"),
                                p("crossoverMode", "REGION_EXIT"));

                put(definitions, INDICATORS.STOCHASTIC_REGION_CROSSOVER,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("percentK", "percentD", "crossoverSignal"),
                                p("kPeriod", "14"),
                                p("dPeriod", "3"),
                                p("smooth", "3"),
                                p("oversold", "20"),
                                p("overbought", "80"),
                                p("crossoverMode", "REGION_EXIT"));

                put(definitions, INDICATORS.ATR,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("atr"),
                                p("period", "14"));

                put(definitions, INDICATORS.ATR_PERCENT,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("atrPercent"),
                                p("period", "14"));

                put(definitions, INDICATORS.BOLLINGER_BANDS,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("upper", "middle", "lower", "bandwidth", "percentB"),
                                p("period", "20"),
                                p("stdDevMult", "2.0"));

                put(definitions, INDICATORS.BOLLINGER_UPPER,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("upper"),
                                p("period", "20"),
                                p("stdDevMult", "2.0"));

                put(definitions, INDICATORS.BOLLINGER_MIDDLE,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("middle"),
                                p("period", "20"));

                put(definitions, INDICATORS.BOLLINGER_LOWER,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("lower"),
                                p("period", "20"),
                                p("stdDevMult", "2.0"));

                put(definitions, INDICATORS.BOLLINGER_WIDTH,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("bandwidth"),
                                p("period", "20"),
                                p("stdDevMult", "2.0"));

                put(definitions, INDICATORS.BOLLINGER_PERCENT_B,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("percentB"),
                                p("period", "20"),
                                p("stdDevMult", "2.0"));

                put(definitions, INDICATORS.KELTNER_CHANNEL,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("upper", "middle", "lower"),
                                p("emaPeriod", "20"),
                                p("atrPeriod", "14"),
                                p("atrMult", "1.5"));

                put(definitions, INDICATORS.KELTNER_UPPER,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("upper"),
                                p("emaPeriod", "20"),
                                p("atrPeriod", "14"),
                                p("atrMult", "1.5"));

                put(definitions, INDICATORS.KELTNER_LOWER,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("lower"),
                                p("emaPeriod", "20"),
                                p("atrPeriod", "14"),
                                p("atrMult", "1.5"));

                put(definitions, INDICATORS.DONCHIAN_CHANNEL,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("high", "middle", "low"),
                                p("period", "20"));

                put(definitions, INDICATORS.DONCHIAN_HIGH,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("high"),
                                p("period", "20"));

                put(definitions, INDICATORS.DONCHIAN_LOW,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("low"),
                                p("period", "20"));

                put(definitions, INDICATORS.STANDARD_DEVIATION,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("standardDeviation"),
                                p("period", "20"));

                put(definitions, INDICATORS.HISTORICAL_VOLATILITY,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("historicalVolatility"),
                                p("period", "20"));

                put(definitions, INDICATORS.ADX,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("adx"),
                                p("period", "14"));

                put(definitions, INDICATORS.DMI,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("plusDI", "minusDI", "adx"),
                                p("period", "14"));

                put(definitions, INDICATORS.AROON,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("aroonUp", "aroonDown"),
                                p("period", "25"));

                put(definitions, INDICATORS.AROON_OSCILLATOR,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("aroonOscillator"),
                                p("period", "25"));

                put(definitions, INDICATORS.PARABOLIC_SAR,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("psar"),
                                p("step", "0.02"),
                                p("maxStep", "0.2"));

                put(definitions, INDICATORS.PSAR,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("psar"),
                                p("step", "0.02"),
                                p("maxStep", "0.2"));

                put(definitions, INDICATORS.ICHIMOKU,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("tenkan", "kijun", "senkouA", "senkouB", "chikou"),
                                p("tenkan", "9"),
                                p("kijun", "26"),
                                p("senkouB", "52"),
                                p("displacement", "26"));

                put(definitions, INDICATORS.ICHIMOKU_TENKAN,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("tenkan"),
                                p("period", "9"));

                put(definitions, INDICATORS.ICHIMOKU_KIJUN,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("kijun"),
                                p("period", "26"));

                put(definitions, INDICATORS.ICHIMOKU_SENKOU_A,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("senkouA"));

                put(definitions, INDICATORS.ICHIMOKU_SENKOU_B,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("senkouB"),
                                p("period", "52"));

                put(definitions, INDICATORS.ICHIMOKU_CHIKOU,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("chikou"),
                                p("displacement", "26"));

                put(definitions, INDICATORS.OBV,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("obv"));

                put(definitions, INDICATORS.MFI,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("mfi"),
                                p("period", "14"));

                put(definitions, INDICATORS.VWAP,
                                IndicatorRenderTarget.PRICE_OVERLAY,
                                outputs("vwap"));

                put(definitions, INDICATORS.VOLUME_SMA,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("volumeSma"),
                                p("period", "20"));

                put(definitions, INDICATORS.VOLUME_RATIO,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("volumeRatio"),
                                p("period", "20"));

                put(definitions, INDICATORS.VOLUME_SPIKE,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("volumeSpike"),
                                p("thresholdMult", "2.0"),
                                p("period", "20"));

                put(definitions, INDICATORS.CMF,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("cmf"),
                                p("period", "20"));

                put(definitions, INDICATORS.ADL,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("adl"));

                put(definitions, INDICATORS.CHAIKIN_AD_OSCILLATOR,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("chaikinOscillator"),
                                p("fastPeriod", "3"),
                                p("slowPeriod", "10"));

                put(definitions, INDICATORS.ELDER_RAY,
                                IndicatorRenderTarget.SEPARATE_PANE,
                                outputs("bullPower", "bearPower"),
                                p("period", "13"));

                for (INDICATORS indicator : INDICATORS.values()) {
                        definitions.putIfAbsent(indicator, fallback(indicator));
                }

                return Map.copyOf(definitions);
        }

        private static void put(
                        Map<INDICATORS, IndicatorDefinition> definitions,
                        INDICATORS indicator,
                        IndicatorRenderTarget renderTarget,
                        List<String> outputs,
                        IndicatorParameterDefinition... parameters) {
                if (indicator == null) {
                        return;
                }

                definitions.put(indicator, new IndicatorDefinition(
                                indicator,
                                indicator.name(),
                                indicator.getDisplayName(),
                                safeDescription(indicator),
                                indicator.getCategory(),
                                renderTarget,
                                List.of(parameters),
                                outputs));
        }

        public static IndicatorDefinition fallback(INDICATORS indicator) {
                INDICATORS safeIndicator = indicator == null ? INDICATORS.UNKNOWN : indicator;

                return new IndicatorDefinition(
                                safeIndicator,
                                safeIndicator.name(),
                                safeIndicator.getDisplayName(),
                                safeDescription(safeIndicator),
                                safeIndicator.getCategory(),
                                IndicatorRenderTarget.SEPARATE_PANE,
                                List.of(),
                                outputs(defaultOutput(safeIndicator)));
        }

        private static IndicatorParameterDefinition p(String name, String defaultValue) {
                return new IndicatorParameterDefinition(
                                name,
                                display(name),
                                defaultValue,
                                inferType(defaultValue),
                                display(name),
                                true);
        }

        private static List<String> outputs(String... values) {
                return List.of(values);
        }

        private static String defaultOutput(INDICATORS indicator) {
                return indicator == null ? "unknown" : indicator.name().toLowerCase(Locale.ROOT);
        }

        private static String safeDescription(INDICATORS indicator) {
                if (indicator == null) {
                        return "";
                }

                String description = indicator.getDescription();

                if (description == null || description.isBlank()) {
                        return indicator.getDisplayName();
                }

                return description;
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