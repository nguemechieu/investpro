package org.investpro.strategy;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class StrategyCatalog {

    private StrategyCatalog() {
    }

    public static final List<String> CORE_STRATEGY_NAMES = List.of(
            "Trend Following",
            "Mean Reversion",
            "Breakout",
            "AI Hybrid",
            "EMA Cross",
            "Momentum Continuation",
            "Pullback Trend",
            "Volatility Breakout",
            "MACD Trend",
            "Range Fade",
            "Donchian Trend",
            "Bollinger Squeeze",
            "ATR Compression Breakout",
            "RSI Failure Swing",
            "Volume Spike Reversal",
            "ML Model",
            "Adaptive Momentum Pullback"
    );

    public static final Map<String, StrategyDefinition> STRATEGY_DEFINITIONS = buildCatalog();

    public static final Map<String, String> STRATEGY_VARIANT_BASE_MAP = buildVariantBaseMap();

    public static final Map<String, String> STRATEGY_ALIASES = buildAliases();

    private static @NotNull @Unmodifiable Map<String, StrategyDefinition> buildCatalog() {
        Map<String, StrategyDefinition> catalog = new LinkedHashMap<>();

        List<VariantProfile> styleProfiles = List.of(
                new VariantProfile(
                        "Scalp",
                        StrategyParameters.builder()
                                .rsiPeriod(7)
                                .emaFast(8)
                                .emaSlow(21)
                                .atrPeriod(7)
                                .breakoutLookback(8)
                                .build()
                ),
                new VariantProfile(
                        "Intraday",
                        StrategyParameters.builder()
                                .rsiPeriod(9)
                                .emaFast(12)
                                .emaSlow(26)
                                .atrPeriod(10)
                                .breakoutLookback(12)
                                .build()
                ),
                new VariantProfile(
                        "Swing",
                        StrategyParameters.builder()
                                .rsiPeriod(14)
                                .emaFast(20)
                                .emaSlow(50)
                                .atrPeriod(14)
                                .breakoutLookback(20)
                                .build()
                ),
                new VariantProfile(
                        "Position",
                        StrategyParameters.builder()
                                .rsiPeriod(21)
                                .emaFast(34)
                                .emaSlow(89)
                                .atrPeriod(21)
                                .breakoutLookback(34)
                                .build()
                ),
                new VariantProfile(
                        "Asia Session",
                        StrategyParameters.builder()
                                .rsiPeriod(10)
                                .emaFast(13)
                                .emaSlow(34)
                                .atrPeriod(10)
                                .breakoutLookback(10)
                                .build()
                ),
                new VariantProfile(
                        "London Session",
                        StrategyParameters.builder()
                                .rsiPeriod(11)
                                .emaFast(15)
                                .emaSlow(35)
                                .atrPeriod(12)
                                .breakoutLookback(14)
                                .build()
                ),
                new VariantProfile(
                        "New York Session",
                        StrategyParameters.builder()
                                .rsiPeriod(12)
                                .emaFast(17)
                                .emaSlow(40)
                                .atrPeriod(12)
                                .breakoutLookback(16)
                                .build()
                ),
                new VariantProfile(
                        "Volatility Focus",
                        StrategyParameters.builder()
                                .rsiPeriod(10)
                                .emaFast(18)
                                .emaSlow(45)
                                .atrPeriod(20)
                                .breakoutLookback(18)
                                .build()
                ),
                new VariantProfile(
                        "Mean Revert Focus",
                        StrategyParameters.builder()
                                .rsiPeriod(6)
                                .emaFast(10)
                                .emaSlow(24)
                                .atrPeriod(9)
                                .breakoutLookback(12)
                                .oversoldThreshold(30)
                                .overboughtThreshold(70)
                                .build()
                ),
                new VariantProfile(
                        "Trend Strength",
                        StrategyParameters.builder()
                                .rsiPeriod(16)
                                .emaFast(24)
                                .emaSlow(55)
                                .atrPeriod(16)
                                .breakoutLookback(24)
                                .build()
                ),
                new VariantProfile(
                        "Multi Confirm",
                        StrategyParameters.builder()
                                .rsiPeriod(14)
                                .emaFast(21)
                                .emaSlow(55)
                                .atrPeriod(18)
                                .breakoutLookback(21)
                                .build()
                )
        );

        List<VariantProfile> riskProfiles = List.of(
                new VariantProfile(
                        "Conservative",
                        StrategyParameters.builder()
                                .oversoldThreshold(32)
                                .overboughtThreshold(68)
                                .minConfidence(0.64)
                                .signalAmount(0.50)
                                .build()
                ),
                new VariantProfile(
                        "Balanced",
                        StrategyParameters.builder()
                                .oversoldThreshold(35)
                                .overboughtThreshold(65)
                                .minConfidence(0.58)
                                .signalAmount(1.00)
                                .build()
                ),
                new VariantProfile(
                        "Aggressive",
                        StrategyParameters.builder()
                                .oversoldThreshold(38)
                                .overboughtThreshold(62)
                                .minConfidence(0.54)
                                .signalAmount(1.35)
                                .build()
                ),
                new VariantProfile(
                        "Institutional",
                        StrategyParameters.builder()
                                .oversoldThreshold(34)
                                .overboughtThreshold(66)
                                .minConfidence(0.60)
                                .signalAmount(0.85)
                                .build()
                ),
                new VariantProfile(
                        "Quant",
                        StrategyParameters.builder()
                                .oversoldThreshold(33)
                                .overboughtThreshold(67)
                                .minConfidence(0.57)
                                .signalAmount(1.15)
                                .build()
                )
        );

        List<VariantProfile> marketContextProfiles = List.of(
                new VariantProfile(
                        "FX Core",
                        StrategyParameters.builder()
                                .emaFast(13)
                                .emaSlow(34)
                                .atrPeriod(10)
                                .breakoutLookback(12)
                                .minConfidence(0.60)
                                .build()
                ),
                new VariantProfile(
                        "Crypto Expansion",
                        StrategyParameters.builder()
                                .emaFast(21)
                                .emaSlow(55)
                                .atrPeriod(18)
                                .breakoutLookback(24)
                                .signalAmount(1.20)
                                .build()
                ),
                new VariantProfile(
                        "Equities Macro",
                        StrategyParameters.builder()
                                .emaFast(34)
                                .emaSlow(89)
                                .atrPeriod(20)
                                .breakoutLookback(34)
                                .minConfidence(0.62)
                                .build()
                ),
                new VariantProfile(
                        "Futures Carry",
                        StrategyParameters.builder()
                                .emaFast(18)
                                .emaSlow(48)
                                .atrPeriod(16)
                                .breakoutLookback(20)
                                .signalAmount(1.10)
                                .build()
                ),
                new VariantProfile(
                        "Commodities Trend",
                        StrategyParameters.builder()
                                .emaFast(21)
                                .emaSlow(60)
                                .atrPeriod(22)
                                .breakoutLookback(28)
                                .minConfidence(0.61)
                                .build()
                ),
                new VariantProfile(
                        "Index Rotation",
                        StrategyParameters.builder()
                                .emaFast(26)
                                .emaSlow(65)
                                .atrPeriod(18)
                                .breakoutLookback(26)
                                .signalAmount(0.95)
                                .build()
                )
        );

        for (String baseName : CORE_STRATEGY_NAMES) {
            put(catalog, baseName, baseName, StrategyParameters.builder().build());

            for (VariantProfile style : styleProfiles) {
                for (VariantProfile risk : riskProfiles) {
                    String variantName = "%s | %s %s".formatted(
                            baseName,
                            style.label(),
                            risk.label()
                    );

                    StrategyParameters params = merge(
                            style.parameters(),
                            risk.parameters()
                    );

                    put(catalog, variantName, baseName, params);

                    for (VariantProfile context : marketContextProfiles) {
                        String contextualVariantName = "%s | %s %s %s".formatted(
                                baseName,
                                style.label(),
                                risk.label(),
                                context.label()
                        );

                        StrategyParameters contextualParams = merge(
                                style.parameters(),
                                risk.parameters(),
                                context.parameters()
                        );

                        put(catalog, contextualVariantName, baseName, contextualParams);
                    }
                }
            }
        }

        put(
                catalog,
                "AI Hybrid | Institutional Prime",
                "AI Hybrid",
                StrategyParameters.builder()
                        .rsiPeriod(18)
                        .emaFast(34)
                        .emaSlow(89)
                        .atrPeriod(21)
                        .breakoutLookback(34)
                        .oversoldThreshold(34)
                        .overboughtThreshold(66)
                        .minConfidence(0.66)
                        .signalAmount(0.90)
                        .build()
        );

        return Map.copyOf(catalog);
    }

    private static void put(
            Map<String, StrategyDefinition> catalog,
            String name,
            String baseName,
            StrategyParameters parameters
    ) {
        if (name == null || name.isBlank()) {
            return;
        }

        if (baseName == null || baseName.isBlank()) {
            return;
        }

        catalog.putIfAbsent(
                name,
                StrategyDefinition.builder()
                        .name(name)
                        .baseName(baseName)
                        .parameters(parameters == null ? StrategyParameters.builder().build() : parameters)
                        .build()
        );
    }

    private static StrategyParameters merge(StrategyParameters... profiles) {
        StrategyParameters result = StrategyParameters.builder().build();

        if (profiles == null) {
            return result;
        }

        for (StrategyParameters profile : profiles) {
            if (profile != null) {
                result = result.merge(profile);
            }
        }

        return result;
    }

    private static Map<String, String> buildVariantBaseMap() {
        Map<String, String> map = new LinkedHashMap<>();

        for (StrategyDefinition definition : STRATEGY_DEFINITIONS.values()) {
            map.put(definition.getName(), definition.getBaseName());
        }

        return Map.copyOf(map);
    }

    private static Map<String, String> buildAliases() {
        Map<String, String> aliases = new LinkedHashMap<>();

        aliases.put("DEFAULT", "Trend Following");
        aliases.put("EMA_RSI", "Trend Following");
        aliases.put("TREND", "Trend Following");
        aliases.put("TREND FOLLOWING", "Trend Following");

        aliases.put("MEAN REVERSION", "Mean Reversion");
        aliases.put("MEAN_REVERSION", "Mean Reversion");
        aliases.put("RSI_MEAN_REVERSION", "Mean Reversion");

        aliases.put("BREAKOUT", "Breakout");

        aliases.put("EMA CROSS", "EMA Cross");
        aliases.put("EMA_CROSS", "EMA Cross");

        aliases.put("MOMENTUM", "Momentum Continuation");
        aliases.put("MOMENTUM CONTINUATION", "Momentum Continuation");

        aliases.put("PULLBACK", "Pullback Trend");
        aliases.put("PULLBACK TREND", "Pullback Trend");

        aliases.put("VOLATILITY", "Volatility Breakout");
        aliases.put("VOLATILITY BREAKOUT", "Volatility Breakout");

        aliases.put("MACD", "MACD Trend");
        aliases.put("MACD_TREND", "MACD Trend");
        aliases.put("MACD TREND", "MACD Trend");

        aliases.put("RANGE", "Range Fade");
        aliases.put("RANGE FADE", "Range Fade");

        aliases.put("DONCHIAN", "Donchian Trend");
        aliases.put("DONCHIAN TREND", "Donchian Trend");

        aliases.put("BOLLINGER", "Bollinger Squeeze");
        aliases.put("BOLLINGER SQUEEZE", "Bollinger Squeeze");

        aliases.put("ATR COMPRESSION", "ATR Compression Breakout");
        aliases.put("ATR_COMPRESSION", "ATR Compression Breakout");
        aliases.put("ATR COMPRESSION BREAKOUT", "ATR Compression Breakout");

        aliases.put("RSI FAILURE", "RSI Failure Swing");
        aliases.put("RSI FAILURE SWING", "RSI Failure Swing");

        aliases.put("VOLUME SPIKE", "Volume Spike Reversal");
        aliases.put("VOLUME SPIKE REVERSAL", "Volume Spike Reversal");

        aliases.put("AI", "AI Hybrid");
        aliases.put("AI HYBRID", "AI Hybrid");
        aliases.put("LSTM", "AI Hybrid");

        aliases.put("ML", "ML Model");
        aliases.put("ML MODEL", "ML Model");

        for (String strategyName : STRATEGY_DEFINITIONS.keySet()) {
            aliases.put(strategyName.toUpperCase(), strategyName);
        }

        return Map.copyOf(aliases);
    }

    public static String normalizeStrategyName(String strategyName) {
        String label = strategyName == null || strategyName.isBlank()
                ? "Trend Following"
                : strategyName.trim();

        return STRATEGY_ALIASES.getOrDefault(label.toUpperCase(), label);
    }

    public static StrategyDefinition definition(String strategyName) {
        String normalized = normalizeStrategyName(strategyName);

        StrategyDefinition definition = STRATEGY_DEFINITIONS.get(normalized);

        if (definition != null) {
            return definition;
        }

        return StrategyDefinition.builder()
                .name(normalized)
                .baseName(resolveBaseStrategyName(normalized))
                .parameters(StrategyParameters.builder().build())
                .build();
    }

    public static String resolveBaseStrategyName(String strategyName) {
        String normalized = normalizeStrategyName(strategyName);

        String baseName = STRATEGY_VARIANT_BASE_MAP.get(normalized);

        if (baseName != null && !baseName.isBlank()) {
            return baseName;
        }

        return normalized;
    }

    @Contract(" -> new")
    public static @NotNull List<String> availableStrategyNames() {
        return new ArrayList<>(STRATEGY_DEFINITIONS.keySet());
    }

    public static int definitionCount() {
        return STRATEGY_DEFINITIONS.size();
    }

    private record VariantProfile(String label, StrategyParameters parameters) {
    }
}