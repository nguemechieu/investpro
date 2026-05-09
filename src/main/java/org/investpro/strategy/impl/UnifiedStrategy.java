package org.investpro.strategy.impl;

import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.enums.StrategyCategory;
import org.investpro.enums.AssetClass;
import org.investpro.enums.ContractType;
import org.investpro.strategy.*;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.*;

import static org.investpro.utils.Side.*;

/**
 * Unified Strategy Engine - Catalog-driven multi-strategy support.
 *
 * Implements 17+ core strategies with variant support through parameter
 * profiles.
 * Uses a feature pipeline to compute technical indicators and selects strategy
 * logic based on catalog definition.
 *
 * Inspired by Python strategy.py design but unified into single engine.
 */
@Slf4j
public class UnifiedStrategy extends BaseStrategy {

    public static final String STRATEGY_ID = "unified-strategy";
    private static final int DEFAULT_MINIMUM_BARS = 100;

    private final FeaturePipeline featurePipeline;
    private String strategyName;
    private StrategyParameters parameters;

    // =========================================================================
    // Constructors
    // =========================================================================

    public UnifiedStrategy() {
        this("Trend Following");
    }

    public UnifiedStrategy(@NotNull String strategyName) {
        this(strategyName, new FeaturePipeline());
    }

    public UnifiedStrategy(@NotNull String strategyName, @NotNull FeaturePipeline featurePipeline) {
        super(buildMetadata());
        this.featurePipeline = featurePipeline;
        this.strategyName = StrategyCatalog.normalizeStrategyName(strategyName);
        this.parameters = StrategyCatalog.definition(this.strategyName).getParameters();
    }

    // =========================================================================
    // Metadata
    // =========================================================================

    private static @NotNull StrategyMetadata buildMetadata() {
        Set<AssetClass> assets = EnumSet.of(
                AssetClass.CRYPTO_ASSET,
                AssetClass.FIAT_CURRENCY,
                AssetClass.EQUITY);

        Set<ContractType> contracts = EnumSet.of(
                ContractType.SPOT,
                ContractType.PERPETUAL);

        Set<Timeframe> timeframes = EnumSet.allOf(Timeframe.class);

        return StrategyMetadata.builder()
                .strategyId(STRATEGY_ID)
                .displayName("Unified Strategy Catalog")
                .description("Catalog-driven strategy engine with variants, aliases, and feature pipeline. " +
                        "Supports 17+ core strategies with 10+ style/risk/context profiles each.")
                .category(StrategyCategory.HYBRID)
                .supportedAssetClasses(assets)
                .supportedContractTypes(contracts)
                .supportedTimeframes(timeframes)
                .minimumBarsRequired(DEFAULT_MINIMUM_BARS)
                .expectedHoldingPeriod("adaptive")
                .riskLevel(StrategyMetadata.RiskLevel.MEDIUM)
                .version("1.0.0")
                .author("InvestPro")
                .enabled(true)
                .build();
    }

    // =========================================================================
    // Configuration
    // =========================================================================

    public void setStrategyName(@NotNull String strategyName) {
        String normalized = StrategyCatalog.normalizeStrategyName(strategyName);
        StrategyDefinition def = StrategyCatalog.definition(normalized);
        this.strategyName = normalized;
        this.parameters = def.getParameters();
        log.info("Strategy switched to: {} with parameters: {}", normalized, parameters);
    }

    public @NotNull String getStrategyName() {
        return strategyName;
    }

    // =========================================================================
    // Signal Generation
    // =========================================================================

    @Override
    public @NotNull StrategySignal generateSignal(@NotNull StrategyContext context) {
        // Validate sufficient data
        if (!context.hasEnoughBars(metadata.getMinimumBarsRequired())) {
            return noSignal(context, "Insufficient bars (minimum " +
                    metadata.getMinimumBarsRequired() + " required)");
        }

        // Compute features
        FeaturePipelineConfig config = FeaturePipelineConfig.from(parameters);
        FeatureRow features = featurePipeline.computeLatest(context.getCandles(), config);

        if (features == null) {
            return noSignal(context, "Unable to compute features");
        }

        // Resolve base strategy
        String baseName = StrategyCatalog.resolveBaseStrategyName(strategyName);

        // Execute appropriate strategy logic
        StrategySignal signal = switch (baseName) {
            case "Trend Following" -> trendFollowing(context, features);
            case "Mean Reversion" -> meanReversion(context, features);
            case "Breakout" -> breakout(context, features);
            case "EMA Cross" -> emaCross(context, features);
            case "Momentum Continuation" -> momentumContinuation(context, features);
            case "Pullback Trend" -> pullbackTrend(context, features);
            case "Volatility Breakout" -> volatilityBreakout(context, features);
            case "MACD Trend" -> macdTrend(context, features);
            case "Range Fade" -> rangeFade(context, features);
            case "Donchian Trend" -> donchianTrend(context, features);
            case "Bollinger Squeeze" -> bollingerSqueeze(context, features);
            case "ATR Compression Breakout" -> atrCompressionBreakout(context, features);
            case "RSI Failure Swing" -> rsiFailureSwing(context, features);
            case "Volume Spike Reversal" -> volumeSpikeReversal(context, features);
            case "Adaptive Momentum Pullback" -> adaptiveMomentumPullback(context, features);
            case "AI Hybrid", "ML Model" -> noSignal(context, baseName + " requires AI service (not attached)");
            default -> noSignal(context, "Unknown base strategy: " + baseName);
        };

        // Add metadata if actionable
        if (signal.isActionable()) {
            signal = signal.toBuilder()
                    .strategyName(strategyName)
                    .metadata("selectedBaseStrategy", baseName)
                    .metadata("rsi", features.getRsi())
                    .metadata("emaFast", features.getEmaFast())
                    .metadata("emaSlow", features.getEmaSlow())
                    .metadata("atr", features.getAtr())
                    .metadata("atrPct", features.getAtrPct())
                    .metadata("regime", features.getRegime())
                    .metadata("volumeRatio", features.getVolumeRatio())
                    .metadata("bandPosition", features.getBandPosition())
                    .metadata("trendStrength", features.getTrendStrength())
                    .build();
        }

        return signal;
    }

    @Override
    public @NotNull Object getId() {
        return STRATEGY_ID;
    }

    // =========================================================================
    // Strategy Implementations
    // =========================================================================

    private @NotNull StrategySignal trendFollowing(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        boolean bullish = features.trendUp() && features.emasAlignedBullish();
        boolean bearish = features.trendDown() && features.emasAlignedBearish();

        if (bullish) {
            return signal(context, BUY, 0.65, "Price > EMA20 > EMA50", features);
        } else if (bearish) {
            return signal(context, SELL, 0.65, "Price < EMA20 < EMA50", features);
        }

        return noSignal(context, "No trend alignment");
    }

    private @NotNull StrategySignal meanReversion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        boolean oversold = features.getRsi() < parameters.getOversoldThreshold();
        boolean overbought = features.getRsi() > parameters.getOverboughtThreshold();

        if (oversold && features.trendDown()) {
            return signal(context, BUY, 0.62, "Oversold (RSI=" + String.format("%.0f", features.getRsi()) + ")",
                    features);
        } else if (overbought && features.trendUp()) {
            return signal(context, SELL, 0.62, "Overbought (RSI=" + String.format("%.0f", features.getRsi()) + ")",
                    features);
        }

        return noSignal(context, "RSI neutral");
    }

    private @NotNull StrategySignal breakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        double close = features.getClose();
        boolean bullishBreakout = close > features.getBreakoutHigh() && features.getVolumeRatio() > 1.2;
        boolean bearishBreakout = close < features.getBreakoutLow() && features.getVolumeRatio() > 1.2;

        if (bullishBreakout) {
            return signal(context, BUY, 0.68, "Breakout above resistance with volume", features);
        } else if (bearishBreakout) {
            return signal(context, SELL, 0.68, "Breakout below support with volume", features);
        }

        return noSignal(context, "No breakout");
    }

    private @NotNull StrategySignal emaCross(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        double fastEMA = features.getEmaFast();
        double slowEMA = features.getEmaSlow();
        CandleData prev = context.getPreviousCandle(1);

        if (prev == null)
            return noSignal(context, "No previous candle");

        // Golden cross: fast EMA crosses above slow EMA
        boolean goldenCross = fastEMA > slowEMA && computeEMA(context.getCandles(),
                parameters.getEmaFast(), -1) <= slowEMA;

        // Death cross: fast EMA crosses below slow EMA
        boolean deathCross = fastEMA < slowEMA && computeEMA(context.getCandles(),
                parameters.getEmaFast(), -1) >= slowEMA;

        if (goldenCross) {
            return signal(context, BUY, 0.64, "Golden Cross (EMA20 > EMA50)", features);
        } else if (deathCross) {
            return signal(context, SELL, 0.64, "Death Cross (EMA20 < EMA50)", features);
        }

        return noSignal(context, "No EMA cross");
    }

    private @NotNull StrategySignal momentumContinuation(@NotNull StrategyContext context,
            @NotNull FeatureRow features) {
        double momentum = features.getMomentum();
        boolean strongUp = momentum > 0.03 && features.trendUp();
        boolean strongDown = momentum < -0.03 && features.trendDown();

        if (strongUp && features.getVolumeRatio() > 1.1) {
            return signal(context, BUY, 0.66, "Strong upside momentum", features);
        } else if (strongDown && features.getVolumeRatio() > 1.1) {
            return signal(context, SELL, 0.66, "Strong downside momentum", features);
        }

        return noSignal(context, "Momentum weak");
    }

    private @NotNull StrategySignal pullbackTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        double pullbackGap = features.getPullbackGap();
        boolean bullishPullback = features.emasAlignedBullish() && pullbackGap > 0.005 && pullbackGap < 0.05;
        boolean bearishPullback = features.emasAlignedBearish() && pullbackGap < -0.005 && pullbackGap > -0.05;

        if (bullishPullback && features.getRsi() < 60) {
            return signal(context, BUY, 0.60, "Pullback in uptrend near EMA20", features);
        } else if (bearishPullback && features.getRsi() > 40) {
            return signal(context, SELL, 0.60, "Pullback in downtrend near EMA20", features);
        }

        return noSignal(context, "No pullback");
    }

    private @NotNull StrategySignal volatilityBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.isHighVolatility()) {
            boolean bullish = features.trendUp() && features.getVolumeRatio() > 1.3;
            boolean bearish = features.trendDown() && features.getVolumeRatio() > 1.3;

            if (bullish) {
                return signal(context, BUY, 0.63, "High volatility + bullish trend", features);
            } else if (bearish) {
                return signal(context, SELL, 0.63, "High volatility + bearish trend", features);
            }
        }

        return noSignal(context, "Insufficient volatility");
    }

    private @NotNull StrategySignal macdTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        double macdLine = features.getMacdLine();
        double macdSignal = features.getMacdSignal();
        boolean bullish = macdLine > macdSignal && macdLine > 0;
        boolean bearish = macdLine < macdSignal && macdLine < 0;

        if (bullish && features.trendUp()) {
            return signal(context, BUY, 0.61, "MACD above signal in uptrend", features);
        } else if (bearish && features.trendDown()) {
            return signal(context, SELL, 0.61, "MACD below signal in downtrend", features);
        }

        return noSignal(context, "MACD neutral");
    }

    private @NotNull StrategySignal rangeFade(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.isRanging()) {
            boolean atUpper = features.getBandPosition() > 0.85;
            boolean atLower = features.getBandPosition() < 0.15;

            if (atUpper && !features.emasAlignedBullish()) {
                return signal(context, SELL, 0.58, "Near upper band in range", features);
            } else if (atLower && !features.emasAlignedBearish()) {
                return signal(context, BUY, 0.58, "Near lower band in range", features);
            }
        }

        return noSignal(context, "Not in range mode");
    }

    private @NotNull StrategySignal donchianTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        double close = features.getClose();
        boolean nearHigh = close > features.getBreakoutHigh() * 0.99;
        boolean nearLow = close < features.getBreakoutLow() * 1.01;

        if (nearHigh && features.emasAlignedBullish()) {
            return signal(context, BUY, 0.59, "Near Donchian high in uptrend", features);
        } else if (nearLow && features.emasAlignedBearish()) {
            return signal(context, SELL, 0.59, "Near Donchian low in downtrend", features);
        }

        return noSignal(context, "Not near Donchian levels");
    }

    private @NotNull StrategySignal bollingerSqueeze(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        double bandWidth = features.getUpperBand() - features.getLowerBand();
        double atr = features.getAtr();
        boolean squeezed = bandWidth < atr * 1.2;

        if (squeezed) {
            boolean bullish = features.getClose() > features.getUpperBand() && features.trendUp();
            boolean bearish = features.getClose() < features.getLowerBand() && features.trendDown();

            if (bullish) {
                return signal(context, BUY, 0.60, "Squeeze breakout bullish", features);
            } else if (bearish) {
                return signal(context, SELL, 0.60, "Squeeze breakout bearish", features);
            }
        }

        return noSignal(context, "Bands not squeezed");
    }

    private @NotNull StrategySignal atrCompressionBreakout(@NotNull StrategyContext context,
            @NotNull FeatureRow features) {
        double atrPct = features.getAtrPct();
        boolean compressed = atrPct < 0.015; // Low volatility
        boolean breaking = features.getVolumeRatio() > 1.4;

        if (compressed && breaking) {
            boolean bullish = features.getClose() > features.getEmaFast();
            if (bullish) {
                return signal(context, BUY, 0.62, "ATR compression breakout bullish", features);
            } else {
                return signal(context, SELL, 0.62, "ATR compression breakout bearish", features);
            }
        }

        return noSignal(context, "No ATR compression breakout");
    }

    private @NotNull StrategySignal rsiFailureSwing(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        double rsi = features.getRsi();
        CandleData prev = context.getPreviousCandle(1);

        if (prev == null)
            return noSignal(context, "No previous candle");

        // Failure swing: RSI diverges from price
        boolean bullishFail = rsi < parameters.getOversoldThreshold() && features.getClose() > prev.closePrice();
        boolean bearishFail = rsi > parameters.getOverboughtThreshold() && features.getClose() < prev.closePrice();

        if (bullishFail) {
            return signal(context, BUY, 0.59, "RSI failure swing bullish", features);
        } else if (bearishFail) {
            return signal(context, SELL, 0.59, "RSI failure swing bearish", features);
        }

        return noSignal(context, "No RSI failure");
    }

    private @NotNull StrategySignal volumeSpikeReversal(@NotNull StrategyContext context,
            @NotNull FeatureRow features) {
        double volumeRatio = features.getVolumeRatio();
        boolean spikeUp = volumeRatio > 2.0;

        if (spikeUp) {
            boolean bullish = features.getClose() > features.getEmaFast();
            if (bullish && features.getRsi() > 60) {
                return signal(context, BUY, 0.61, "Volume spike reversal bullish", features);
            } else if (!bullish && features.getRsi() < 40) {
                return signal(context, SELL, 0.61, "Volume spike reversal bearish", features);
            }
        }

        return noSignal(context, "No volume spike");
    }

    private @NotNull StrategySignal adaptiveMomentumPullback(@NotNull StrategyContext context,
            @NotNull FeatureRow features) {
        // Adaptive: change strategy based on regime
        if (features.isTrending()) {
            return pullbackTrend(context, features);
        } else if (features.isHighVolatility()) {
            return volatilityBreakout(context, features);
        } else {
            return rangeFade(context, features);
        }
    }

    // =========================================================================
    // Signal Creation Helper
    // =========================================================================

    private @NotNull StrategySignal signal(
            @NotNull StrategyContext context,
            @NotNull Side side,
            double confidence,
            @NotNull String reason,
            @NotNull FeatureRow features) {

        // Confidence filter
        if (confidence < parameters.getMinConfidence()) {
            return noSignal(context, "Confidence " + String.format("%.2f", confidence) +
                    " below threshold " + parameters.getMinConfidence());
        }

        double close = features.getClose();
        double atr = features.getAtr();

        // ATR-based stops and targets
        double stopLoss;
        double takeProfit;

        if (side == BUY) {
            stopLoss = close - (atr * 1.5);
            takeProfit = close + (atr * 3.0);
        } else {
            stopLoss = close + (atr * 1.5);
            takeProfit = close - (atr * 3.0);
        }

        double riskAmount = Math.abs(close - stopLoss);
        double rewardAmount = Math.abs(takeProfit - close);
        double riskRewardRatio = rewardAmount / Math.max(riskAmount, 0.0001);

        return StrategySignal.builder()
                .symbol(context.getSymbol() == null ? "" : context.getSymbol().toString())
                .timeframe(context.getTimeframe() == null ? "" : context.getTimeframe().getCode())
                .strategyId(STRATEGY_ID)
                .strategyName(strategyName)
                .side(side)
                .confidence(confidence)
                .entryPrice(close)
                .stopLossPrice(stopLoss)
                .takeProfitPrice(takeProfit)
                .riskRewardRatio(riskRewardRatio)
                .marketBehavior(context.getMarketBehavior())
                .sessionStatus(context.getTradingSessionStatus())
                .createdAt(LocalDateTime.now())
                .reason(reason)
                .metadata("strategyVariant", strategyName)
                .metadata("regime", features.getRegime())
                .metadata("amount", parameters.getSignalAmount())
                .build();
    }

    // =========================================================================
    // Helper Methods
    // =========================================================================

    private double computeEMA(@NotNull List<CandleData> candles, int period, int offset) {
        if (candles.size() < period)
            return 0;

        double sum = 0;
        int endIdx = offset < 0 ? candles.size() + offset : offset;

        for (int i = 0; i < period; i++) {
            sum += candles.get(endIdx - period + i).closePrice();
        }
        double sma = sum / period;

        double ema = sma;
        double multiplier = 2.0 / (period + 1);

        int maxIdx = offset < 0 ? candles.size() + offset : offset;
        for (int i = period; i < maxIdx; i++) {
            double close = candles.get(i).closePrice();
            ema = close * multiplier + ema * (1 - multiplier);
        }

        return ema;
    }

    @Override
    public boolean supportsMarketBehavior(@NotNull org.investpro.enums.MarketBehavior marketBehavior) {
        return true; // Adaptive to all behaviors
    }

    public void applyParameters(StrategyParameters parameters) {
        if (parameters != null) {
            this.parameters = parameters;
        }
    }
}
