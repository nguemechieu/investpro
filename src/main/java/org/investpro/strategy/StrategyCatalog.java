package org.investpro.strategy;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.investpro.spi.PluginRegistry;
import org.investpro.spi.StrategyProvider;

import java.util.*;

public final class StrategyCatalog {

    public static final String DEFAULT_STRATEGY_NAME = "Trend Following";


    public static final List<String> CORE_STRATEGY_NAMES = List.of(
            // Core defaults
            "Trend Following",
            "Mean Reversion",
            "Breakout",
            "AI Hybrid",
            "ML Model",

            // Moving average / trend systems
            "EMA Cross",
            "SMA Cross",
            "Triple EMA Trend",
            "Adaptive Moving Average",
            "Keltner Trend",
            "SuperTrend",
            "Parabolic SAR Trend",
            "Ichimoku Cloud Trend",
            "Donchian Trend",
            "ADX Trend Strength",

            // Momentum systems
            "Momentum Continuation",
            "Adaptive Momentum Pullback",
            "RSI Momentum",
            "MACD Trend",
            "Stochastic Momentum",
            "Rate Of Change Momentum",
            "CCI Momentum",
            "Williams R Momentum",

            // Pullback systems
            "Pullback Trend",
            "EMA Pullback",
            "VWAP Pullback",
            "Fibonacci Pullback",
            "Support Resistance Pullback",
            "Trendline Pullback",

            // Volatility systems
            "Volatility Breakout",
            "ATR Compression Breakout",
            "Bollinger Squeeze",
            "Keltner Squeeze",
            "Opening Range Breakout",
            "Session Range Breakout",
            "High Low Breakout",
            "Volatility Expansion",
            "ATR Channel Breakout",

            // Reversion / range systems
            "Range Fade",
            "Bollinger Mean Reversion",
            "RSI Mean Reversion",
            "Stochastic Mean Reversion",
            "VWAP Mean Reversion",
            "Z Score Reversion",
            "Support Resistance Fade",
            "Liquidity Sweep Reversal",

            // Volume / order-flow inspired
            "Volume Spike Reversal",
            "Volume Breakout",
            "OBV Trend",
            "Money Flow Index",
            "Accumulation Distribution",
            "VWAP Trend",
            "Volume Weighted Momentum",

            // Price action
            "Inside Bar Breakout",
            "Outside Bar Reversal",
            "Pin Bar Reversal",
            "Engulfing Candle Reversal",
            "Higher High Higher Low Trend",
            "Lower High Lower Low Trend",
            "Market Structure Break",
            "Fair Value Gap Continuation",
            "Supply Demand Bounce",

            // Session / forex-focused
            "London Breakout",
            "New York Reversal",
            "Asian Range Breakout",
            "London New York Continuation",
            "Session VWAP Reversion",
            "Carry Trend",

            // Crypto-focused
            "Crypto Momentum Expansion",
            "Crypto Volatility Scalper",
            "Funding Rate Bias",
            "Perpetual Trend Following",
            "Altcoin Rotation",
            "Bitcoin Dominance Rotation",

            // Equity / index-focused
            "Gap Fill",
            "Gap And Go",
            "Index Momentum Rotation",
            "Sector Rotation",
            "Earnings Momentum",
            "Opening Drive",
            "Power Hour Continuation",

            // Risk / portfolio-aware
            "Low Volatility Trend",
            "Risk Parity Rotation",
            "Correlation Breakout",
            "Drawdown Recovery",
            "Defensive Rotation",

            // Advanced / AI hybrid ideas
            "Regime Adaptive Strategy",
            "Consensus Multi Signal",
            "Ensemble Strategy",
            "AI Risk Filtered Momentum",
            "AI Volatility Regime",
            "Hybrid Trend Reversion",
            "News Sentiment Momentum",
            "Macro Momentum"
    );
    public static final Map<String, StrategyDefinition> STRATEGY_DEFINITIONS =
            Collections.synchronizedMap(new LinkedHashMap<>(buildCatalog()));

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
        aliases.put("SMA", "SMA Cross");
        aliases.put("SMA_CROSS", "SMA Cross");

        aliases.put("TRIPLE EMA", "Triple EMA Trend");
        aliases.put("TRIPLE_EMA", "Triple EMA Trend");

        aliases.put("AMA", "Adaptive Moving Average");
        aliases.put("ADAPTIVE MA", "Adaptive Moving Average");

        aliases.put("KELTNER", "Keltner Trend");
        aliases.put("SUPERTREND", "SuperTrend");
        aliases.put("SAR", "Parabolic SAR Trend");
        aliases.put("PARABOLIC SAR", "Parabolic SAR Trend");
        aliases.put("ICHIMOKU", "Ichimoku Cloud Trend");
        aliases.put("ADX", "ADX Trend Strength");

        aliases.put("RSI MOMENTUM", "RSI Momentum");
        aliases.put("STOCHASTIC", "Stochastic Momentum");
        aliases.put("ROC", "Rate Of Change Momentum");
        aliases.put("CCI", "CCI Momentum");
        aliases.put("WILLIAMS", "Williams R Momentum");

        aliases.put("EMA PULLBACK", "EMA Pullback");
        aliases.put("VWAP PULLBACK", "VWAP Pullback");
        aliases.put("FIB PULLBACK", "Fibonacci Pullback");
        aliases.put("FIBONACCI", "Fibonacci Pullback");
        aliases.put("SUPPORT RESISTANCE PULLBACK", "Support Resistance Pullback");
        aliases.put("TRENDLINE PULLBACK", "Trendline Pullback");

        aliases.put("KELTNER SQUEEZE", "Keltner Squeeze");
        aliases.put("OPENING RANGE", "Opening Range Breakout");
        aliases.put("ORB", "Opening Range Breakout");
        aliases.put("SESSION BREAKOUT", "Session Range Breakout");
        aliases.put("HIGH LOW BREAKOUT", "High Low Breakout");
        aliases.put("ATR CHANNEL", "ATR Channel Breakout");

        aliases.put("BOLLINGER MEAN", "Bollinger Mean Reversion");
        aliases.put("RSI REVERSION", "RSI Mean Reversion");
        aliases.put("STOCHASTIC REVERSION", "Stochastic Mean Reversion");
        aliases.put("VWAP REVERSION", "VWAP Mean Reversion");
        aliases.put("Z SCORE", "Z Score Reversion");
        aliases.put("LIQUIDITY SWEEP", "Liquidity Sweep Reversal");

        aliases.put("VOLUME BREAKOUT", "Volume Breakout");
        aliases.put("OBV", "OBV Trend");
        aliases.put("MFI", "Money Flow Index");
        aliases.put("ADL", "Accumulation Distribution");
        aliases.put("VWAP TREND", "VWAP Trend");

        aliases.put("INSIDE BAR", "Inside Bar Breakout");
        aliases.put("OUTSIDE BAR", "Outside Bar Reversal");
        aliases.put("PIN BAR", "Pin Bar Reversal");
        aliases.put("ENGULFING", "Engulfing Candle Reversal");
        aliases.put("MARKET STRUCTURE", "Market Structure Break");
        aliases.put("MSB", "Market Structure Break");
        aliases.put("FVG", "Fair Value Gap Continuation");
        aliases.put("SUPPLY DEMAND", "Supply Demand Bounce");

        aliases.put("LONDON", "London Breakout");
        aliases.put("NY REVERSAL", "New York Reversal");
        aliases.put("ASIAN RANGE", "Asian Range Breakout");
        aliases.put("CARRY", "Carry Trend");

        aliases.put("CRYPTO MOMENTUM", "Crypto Momentum Expansion");
        aliases.put("CRYPTO SCALPER", "Crypto Volatility Scalper");
        aliases.put("FUNDING", "Funding Rate Bias");
        aliases.put("PERP TREND", "Perpetual Trend Following");
        aliases.put("ALTCOIN", "Altcoin Rotation");
        aliases.put("BTC DOMINANCE", "Bitcoin Dominance Rotation");

        aliases.put("GAP FILL", "Gap Fill");
        aliases.put("GAP AND GO", "Gap And Go");
        aliases.put("INDEX ROTATION", "Index Momentum Rotation");
        aliases.put("SECTOR ROTATION", "Sector Rotation");
        aliases.put("EARNINGS", "Earnings Momentum");
        aliases.put("OPENING DRIVE", "Opening Drive");
        aliases.put("POWER HOUR", "Power Hour Continuation");

        aliases.put("LOW VOL", "Low Volatility Trend");
        aliases.put("RISK PARITY", "Risk Parity Rotation");
        aliases.put("CORRELATION", "Correlation Breakout");
        aliases.put("DRAWDOWN RECOVERY", "Drawdown Recovery");
        aliases.put("DEFENSIVE", "Defensive Rotation");

        aliases.put("REGIME", "Regime Adaptive Strategy");
        aliases.put("CONSENSUS", "Consensus Multi Signal");
        aliases.put("ENSEMBLE", "Ensemble Strategy");
        aliases.put("AI MOMENTUM", "AI Risk Filtered Momentum");
        aliases.put("AI REGIME", "AI Volatility Regime");
        aliases.put("HYBRID", "Hybrid Trend Reversion");
        aliases.put("NEWS", "News Sentiment Momentum");
        aliases.put("MACRO", "Macro Momentum");
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
                ? DEFAULT_STRATEGY_NAME
                : strategyName.trim();

        return STRATEGY_ALIASES.getOrDefault(label.toUpperCase(), label);
    }

    public static @NotNull String defaultStrategyName() {
        return DEFAULT_STRATEGY_NAME;
    }

    public static StrategyDefinition definition(String strategyName) {
        String normalized = normalizeStrategyName(strategyName);

        StrategyDefinition definition = STRATEGY_DEFINITIONS.get(normalized);

        if (definition != null) {
            return definition;
        }

        Optional<StrategyDefinition> caseInsensitive = STRATEGY_DEFINITIONS.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(normalized))
                .map(Map.Entry::getValue)
                .findFirst();

        return caseInsensitive.orElseGet(() -> StrategyDefinition.builder()
                .name(normalized)
                .baseName(resolveBaseStrategyName(normalized))
                .parameters(StrategyParameters.builder().build())
                .build());

    }

    /**
     * Registers a runtime strategy definition created by the desktop Strategy Builder
     * or loaded from user extensions. Runtime definitions are intentionally kept in
     * the same catalog map so Strategy Lab, Backtesting, and assignment screens see
     * the strategy immediately without restarting the application.
     */
    public static synchronized void registerRuntimeDefinition(@NotNull StrategyDefinition definition) {
        if (definition.getName() == null || definition.getName().isBlank()) {
            return;
        }

        String normalized = normalizeStrategyName(definition.getName());
        STRATEGY_DEFINITIONS.put(normalized, StrategyDefinition.builder()
                .name(normalized)
                .baseName(definition.getBaseName() == null || definition.getBaseName().isBlank()
                        ? normalized
                        : definition.getBaseName())
                .parameters(definition.getParameters() == null
                        ? StrategyParameters.builder().build()
                        : definition.getParameters())
                .build());
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
        LinkedHashSet<String> names = new LinkedHashSet<>(STRATEGY_DEFINITIONS.keySet());
        names.addAll(providerStrategyNames());
        if (names.isEmpty()) {
            names.add(DEFAULT_STRATEGY_NAME);
        }
        return new ArrayList<>(names);
    }

    public static @NotNull List<String> providerStrategyNames() {
        try {
            return PluginRegistry.loadDefault().strategyProviders().stream()
                    .filter(StrategyProvider::enabledByDefault)
                    .map(StrategyProvider::displayName)
                    .filter(name -> name != null && !name.isBlank())
                    .toList();
        } catch (Exception exception) {
            return List.of();
        }
    }

    public static int definitionCount() {
        return STRATEGY_DEFINITIONS.size();
    }

    private record VariantProfile(String label, StrategyParameters parameters) {
    }
}
