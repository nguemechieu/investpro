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
 * <p>
 * Implements 17+ core strategies with variant support through parameter
 * profiles.
 * Uses a feature pipeline to compute technical indicators and selects strategy
 * logic based on catalog definition.
 * <p>
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
    private @NotNull StrategySignal smaCross(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return emaCross(context, features);
    }

    private @NotNull StrategySignal tripleEmaTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        boolean bullish = features.getClose() > features.getEmaFast()
                && features.getEmaFast() > features.getEmaSlow()
                && features.getRsi() > 50;

        boolean bearish = features.getClose() < features.getEmaFast()
                && features.getEmaFast() < features.getEmaSlow()
                && features.getRsi() < 50;

        if (bullish) {
            return signal(context, BUY, 0.64, "Triple EMA bullish trend alignment", features);
        }

        if (bearish) {
            return signal(context, SELL, 0.64, "Triple EMA bearish trend alignment", features);
        }

        return noSignal(context, "No triple EMA alignment");
    }

    private @NotNull StrategySignal adaptiveMovingAverage(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.isTrending()) {
            return trendFollowing(context, features);
        }

        return rangeFade(context, features);
    }

    private @NotNull StrategySignal keltnerTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        double upper = features.getEmaFast() + features.getAtr() * 1.5;
        double lower = features.getEmaFast() - features.getAtr() * 1.5;

        if (features.getClose() > upper && features.trendUp()) {
            return signal(context, BUY, 0.63, "Close above Keltner upper channel in uptrend", features);
        }

        if (features.getClose() < lower && features.trendDown()) {
            return signal(context, SELL, 0.63, "Close below Keltner lower channel in downtrend", features);
        }

        return noSignal(context, "No Keltner trend breakout");
    }

    private @NotNull StrategySignal superTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return features.getClose() > features.getEmaSlow() && features.trendUp()
                ? signal(context, BUY, 0.63, "SuperTrend proxy bullish", features)
                : features.getClose() < features.getEmaSlow() && features.trendDown()
                ? signal(context, SELL, 0.63, "SuperTrend proxy bearish", features)
                : noSignal(context, "No SuperTrend direction");
    }

    private @NotNull StrategySignal parabolicSarTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return pullbackTrend(context, features);
    }

    private @NotNull StrategySignal ichimokuCloudTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        boolean bullishCloud = features.getClose() > features.getEmaFast()
                && features.getEmaFast() > features.getEmaSlow()
                && features.getMomentum() > 0;

        boolean bearishCloud = features.getClose() < features.getEmaFast()
                && features.getEmaFast() < features.getEmaSlow()
                && features.getMomentum() < 0;

        if (bullishCloud) {
            return signal(context, BUY, 0.65, "Ichimoku proxy bullish cloud breakout", features);
        }

        if (bearishCloud) {
            return signal(context, SELL, 0.65, "Ichimoku proxy bearish cloud breakdown", features);
        }

        return noSignal(context, "No Ichimoku cloud confirmation");
    }

    private @NotNull StrategySignal adxTrendStrength(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.getTrendStrength() < 0.45) {
            return noSignal(context, "Trend strength too weak");
        }

        return trendFollowing(context, features);
    }

    private @NotNull StrategySignal rsiMomentum(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.getRsi() > 58 && features.trendUp()) {
            return signal(context, BUY, 0.62, "RSI momentum bullish", features);
        }

        if (features.getRsi() < 42 && features.trendDown()) {
            return signal(context, SELL, 0.62, "RSI momentum bearish", features);
        }

        return noSignal(context, "No RSI momentum");
    }

    private @NotNull StrategySignal stochasticMomentum(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rsiMomentum(context, features);
    }

    private @NotNull StrategySignal rateOfChangeMomentum(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.getMomentum() > 0.025 && features.getVolumeRatio() > 1.05) {
            return signal(context, BUY, 0.63, "Positive rate-of-change momentum", features);
        }

        if (features.getMomentum() < -0.025 && features.getVolumeRatio() > 1.05) {
            return signal(context, SELL, 0.63, "Negative rate-of-change momentum", features);
        }

        return noSignal(context, "Rate of change not strong enough");
    }

    private @NotNull StrategySignal cciMomentum(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rateOfChangeMomentum(context, features);
    }

    private @NotNull StrategySignal williamsRMomentum(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rsiMomentum(context, features);
    }

    private @NotNull StrategySignal emaPullback(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return pullbackTrend(context, features);
    }

    private @NotNull StrategySignal vwapPullback(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return pullbackTrend(context, features);
    }

    private @NotNull StrategySignal fibonacciPullback(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return pullbackTrend(context, features);
    }

    private @NotNull StrategySignal supportResistancePullback(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return pullbackTrend(context, features);
    }

    private @NotNull StrategySignal trendlinePullback(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return pullbackTrend(context, features);
    }

    private @NotNull StrategySignal keltnerSqueeze(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return bollingerSqueeze(context, features);
    }

    private @NotNull StrategySignal openingRangeBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal sessionRangeBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal highLowBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal volatilityExpansion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return volatilityBreakout(context, features);
    }

    private @NotNull StrategySignal atrChannelBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return volatilityBreakout(context, features);
    }

    private @NotNull StrategySignal bollingerMeanReversion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rangeFade(context, features);
    }

    private @NotNull StrategySignal rsiMeanReversion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return meanReversion(context, features);
    }

    private @NotNull StrategySignal stochasticMeanReversion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return meanReversion(context, features);
    }

    private @NotNull StrategySignal vwapMeanReversion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rangeFade(context, features);
    }

    private @NotNull StrategySignal zScoreReversion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rangeFade(context, features);
    }

    private @NotNull StrategySignal supportResistanceFade(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rangeFade(context, features);
    }

    private @NotNull StrategySignal liquiditySweepReversal(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.getVolumeRatio() > 1.8 && features.getBandPosition() < 0.20) {
            return signal(context, BUY, 0.62, "Liquidity sweep below range with reversal pressure", features);
        }

        if (features.getVolumeRatio() > 1.8 && features.getBandPosition() > 0.80) {
            return signal(context, SELL, 0.62, "Liquidity sweep above range with reversal pressure", features);
        }

        return noSignal(context, "No liquidity sweep reversal");
    }

    private @NotNull StrategySignal volumeBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal obvTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.getVolumeRatio() > 1.1) {
            return trendFollowing(context, features);
        }

        return noSignal(context, "Volume confirmation too weak");
    }

    private @NotNull StrategySignal moneyFlowIndex(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return meanReversion(context, features);
    }

    private @NotNull StrategySignal accumulationDistribution(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return obvTrend(context, features);
    }

    private @NotNull StrategySignal vwapTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return trendFollowing(context, features);
    }

    private @NotNull StrategySignal volumeWeightedMomentum(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.getVolumeRatio() > 1.2) {
            return momentumContinuation(context, features);
        }

        return noSignal(context, "Volume-weighted momentum not confirmed");
    }

    private @NotNull StrategySignal insideBarBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        CandleData current = context.getCurrentCandle();
        CandleData previous = context.getPreviousCandle(1);

        if (current == null || previous == null) {
            return noSignal(context, "Need current and previous candle");
        }

        boolean inside = current.highPrice() < previous.highPrice()
                && current.lowPrice() > previous.lowPrice();

        if (!inside) {
            return noSignal(context, "No inside bar compression");
        }

        if (features.trendUp()) {
            return signal(context, BUY, 0.59, "Inside bar in bullish trend", features);
        }

        if (features.trendDown()) {
            return signal(context, SELL, 0.59, "Inside bar in bearish trend", features);
        }

        return noSignal(context, "Inside bar without trend bias");
    }

    private @NotNull StrategySignal outsideBarReversal(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        CandleData current = context.getCurrentCandle();
        CandleData previous = context.getPreviousCandle(1);

        if (current == null || previous == null) {
            return noSignal(context, "Need current and previous candle");
        }

        boolean outside = current.highPrice() > previous.highPrice()
                && current.lowPrice() < previous.lowPrice();

        if (!outside) {
            return noSignal(context, "No outside bar");
        }

        if (current.closePrice() > previous.closePrice()) {
            return signal(context, BUY, 0.60, "Bullish outside bar reversal", features);
        }

        if (current.closePrice() < previous.closePrice()) {
            return signal(context, SELL, 0.60, "Bearish outside bar reversal", features);
        }

        return noSignal(context, "Outside bar neutral close");
    }

    private @NotNull StrategySignal pinBarReversal(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        CandleData candle = context.getCurrentCandle();

        if (candle == null) {
            return noSignal(context, "No current candle");
        }

        double range = Math.max(candle.highPrice() - candle.lowPrice(), 0.0001);
        double upperWick = candle.highPrice() - Math.max(candle.openPrice(), candle.closePrice());
        double lowerWick = Math.min(candle.openPrice(), candle.closePrice()) - candle.lowPrice();

        if (lowerWick / range > 0.55 && features.getBandPosition() < 0.35) {
            return signal(context, BUY, 0.60, "Bullish pin bar near lower zone", features);
        }

        if (upperWick / range > 0.55 && features.getBandPosition() > 0.65) {
            return signal(context, SELL, 0.60, "Bearish pin bar near upper zone", features);
        }

        return noSignal(context, "No pin bar reversal");
    }

    private @NotNull StrategySignal engulfingCandleReversal(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        CandleData current = context.getCurrentCandle();
        CandleData previous = context.getPreviousCandle(1);

        if (current == null || previous == null) {
            return noSignal(context, "Need current and previous candle");
        }

        boolean bullishEngulfing = current.openPrice() < previous.closePrice()
                && current.closePrice() > previous.openPrice();

        boolean bearishEngulfing = current.openPrice() > previous.closePrice()
                && current.closePrice() < previous.openPrice();

        if (bullishEngulfing && features.getRsi() < 55) {
            return signal(context, BUY, 0.61, "Bullish engulfing candle", features);
        }

        if (bearishEngulfing && features.getRsi() > 45) {
            return signal(context, SELL, 0.61, "Bearish engulfing candle", features);
        }

        return noSignal(context, "No engulfing reversal");
    }

    private @NotNull StrategySignal higherHighHigherLowTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return features.trendUp()
                ? signal(context, BUY, 0.62, "Higher-high higher-low trend proxy", features)
                : noSignal(context, "No bullish market structure");
    }

    private @NotNull StrategySignal lowerHighLowerLowTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return features.trendDown()
                ? signal(context, SELL, 0.62, "Lower-high lower-low trend proxy", features)
                : noSignal(context, "No bearish market structure");
    }

    private @NotNull StrategySignal marketStructureBreak(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal fairValueGapContinuation(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return momentumContinuation(context, features);
    }

    private @NotNull StrategySignal supplyDemandBounce(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rangeFade(context, features);
    }
    private @NotNull StrategySignal londonBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal newYorkReversal(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rangeFade(context, features);
    }

    private @NotNull StrategySignal asianRangeBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal londonNewYorkContinuation(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return momentumContinuation(context, features);
    }

    private @NotNull StrategySignal sessionVwapReversion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return rangeFade(context, features);
    }

    private @NotNull StrategySignal carryTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return trendFollowing(context, features);
    }

    private @NotNull StrategySignal cryptoMomentumExpansion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.getAtrPct() > 0.02 && features.getVolumeRatio() > 1.2) {
            return momentumContinuation(context, features);
        }

        return noSignal(context, "Crypto expansion not confirmed");
    }

    private @NotNull StrategySignal cryptoVolatilityScalper(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.isHighVolatility()) {
            return rangeFade(context, features);
        }

        return noSignal(context, "Crypto volatility too low");
    }

    private @NotNull StrategySignal perpetualTrendFollowing(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return trendFollowing(context, features);
    }

    private @NotNull StrategySignal altcoinRotation(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return momentumContinuation(context, features);
    }

    private @NotNull StrategySignal gapFill(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return meanReversion(context, features);
    }

    private @NotNull StrategySignal gapAndGo(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal indexMomentumRotation(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return momentumContinuation(context, features);
    }

    private @NotNull StrategySignal sectorRotation(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return momentumContinuation(context, features);
    }

    private @NotNull StrategySignal openingDrive(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal powerHourContinuation(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return momentumContinuation(context, features);
    }

    private @NotNull StrategySignal lowVolatilityTrend(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.getAtrPct() < 0.02) {
            return trendFollowing(context, features);
        }

        return noSignal(context, "Volatility too high for low-volatility trend");
    }

    private @NotNull StrategySignal correlationBreakout(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return breakout(context, features);
    }

    private @NotNull StrategySignal regimeAdaptiveStrategy(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return adaptiveMomentumPullback(context, features);
    }

    private @NotNull StrategySignal consensusMultiSignal(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        StrategySignal trend = trendFollowing(context, features);
        StrategySignal momentum = momentumContinuation(context, features);
        StrategySignal breakoutSignal = breakout(context, features);

        int buyVotes = 0;
        int sellVotes = 0;

        for (StrategySignal signal : List.of(trend, momentum, breakoutSignal)) {
            if (signal.getSide() == BUY) {
                buyVotes++;
            } else if (signal.getSide() == SELL) {
                sellVotes++;
            }
        }

        if (buyVotes >= 2) {
            return signal(context, BUY, 0.68, "Consensus bullish signal", features);
        }

        if (sellVotes >= 2) {
            return signal(context, SELL, 0.68, "Consensus bearish signal", features);
        }

        return noSignal(context, "No consensus signal");
    }

    private @NotNull StrategySignal ensembleStrategy(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return consensusMultiSignal(context, features);
    }

    private @NotNull StrategySignal hybridTrendReversion(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        if (features.isTrending()) {
            return trendFollowing(context, features);
        }

        return meanReversion(context, features);
    }

    private @NotNull StrategySignal macroMomentum(@NotNull StrategyContext context, @NotNull FeatureRow features) {
        return momentumContinuation(context, features);
    }
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
            // Existing core strategies
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

            // Moving average / trend systems
            case "SMA Cross" -> smaCross(context, features);
            case "Triple EMA Trend" -> tripleEmaTrend(context, features);
            case "Adaptive Moving Average" -> adaptiveMovingAverage(context, features);
            case "Keltner Trend" -> keltnerTrend(context, features);
            case "SuperTrend" -> superTrend(context, features);
            case "Parabolic SAR Trend" -> parabolicSarTrend(context, features);
            case "Ichimoku Cloud Trend" -> ichimokuCloudTrend(context, features);
            case "ADX Trend Strength" -> adxTrendStrength(context, features);

            // Momentum systems
            case "RSI Momentum" -> rsiMomentum(context, features);
            case "Stochastic Momentum" -> stochasticMomentum(context, features);
            case "Rate Of Change Momentum" -> rateOfChangeMomentum(context, features);
            case "CCI Momentum" -> cciMomentum(context, features);
            case "Williams R Momentum" -> williamsRMomentum(context, features);

            // Pullback systems
            case "EMA Pullback" -> emaPullback(context, features);
            case "VWAP Pullback" -> vwapPullback(context, features);
            case "Fibonacci Pullback" -> fibonacciPullback(context, features);
            case "Support Resistance Pullback" -> supportResistancePullback(context, features);
            case "Trendline Pullback" -> trendlinePullback(context, features);

            // Volatility systems
            case "Keltner Squeeze" -> keltnerSqueeze(context, features);
            case "Opening Range Breakout" -> openingRangeBreakout(context, features);
            case "Session Range Breakout" -> sessionRangeBreakout(context, features);
            case "High Low Breakout" -> highLowBreakout(context, features);
            case "Volatility Expansion" -> volatilityExpansion(context, features);
            case "ATR Channel Breakout" -> atrChannelBreakout(context, features);

            // Reversion / range systems
            case "Bollinger Mean Reversion" -> bollingerMeanReversion(context, features);
            case "RSI Mean Reversion" -> rsiMeanReversion(context, features);
            case "Stochastic Mean Reversion" -> stochasticMeanReversion(context, features);
            case "VWAP Mean Reversion" -> vwapMeanReversion(context, features);
            case "Z Score Reversion" -> zScoreReversion(context, features);
            case "Support Resistance Fade" -> supportResistanceFade(context, features);
            case "Liquidity Sweep Reversal" -> liquiditySweepReversal(context, features);

            // Volume / order-flow inspired
            case "Volume Breakout" -> volumeBreakout(context, features);
            case "OBV Trend" -> obvTrend(context, features);
            case "Money Flow Index" -> moneyFlowIndex(context, features);
            case "Accumulation Distribution" -> accumulationDistribution(context, features);
            case "VWAP Trend" -> vwapTrend(context, features);
            case "Volume Weighted Momentum" -> volumeWeightedMomentum(context, features);

            // Price action
            case "Inside Bar Breakout" -> insideBarBreakout(context, features);
            case "Outside Bar Reversal" -> outsideBarReversal(context, features);
            case "Pin Bar Reversal" -> pinBarReversal(context, features);
            case "Engulfing Candle Reversal" -> engulfingCandleReversal(context, features);
            case "Higher High Higher Low Trend" -> higherHighHigherLowTrend(context, features);
            case "Lower High Lower Low Trend" -> lowerHighLowerLowTrend(context, features);
            case "Market Structure Break" -> marketStructureBreak(context, features);
            case "Fair Value Gap Continuation" -> fairValueGapContinuation(context, features);
            case "Supply Demand Bounce" -> supplyDemandBounce(context, features);

            // Session / market-specific strategies
            case "London Breakout" -> londonBreakout(context, features);
            case "New York Reversal" -> newYorkReversal(context, features);
            case "Asian Range Breakout" -> asianRangeBreakout(context, features);
            case "London New York Continuation" -> londonNewYorkContinuation(context, features);
            case "Session VWAP Reversion" -> sessionVwapReversion(context, features);
            case "Carry Trend" -> carryTrend(context, features);

            // Crypto / equities / risk-aware / AI hybrid
            case "Crypto Momentum Expansion" -> cryptoMomentumExpansion(context, features);
            case "Crypto Volatility Scalper" -> cryptoVolatilityScalper(context, features);
            case "Perpetual Trend Following" -> perpetualTrendFollowing(context, features);
            case "Altcoin Rotation" -> altcoinRotation(context, features);
            case "Gap Fill" -> gapFill(context, features);
            case "Gap And Go" -> gapAndGo(context, features);
            case "Index Momentum Rotation" -> indexMomentumRotation(context, features);
            case "Sector Rotation" -> sectorRotation(context, features);
            case "Opening Drive" -> openingDrive(context, features);
            case "Power Hour Continuation" -> powerHourContinuation(context, features);
            case "Low Volatility Trend" -> lowVolatilityTrend(context, features);
            case "Correlation Breakout" -> correlationBreakout(context, features);
            case "Regime Adaptive Strategy" -> regimeAdaptiveStrategy(context, features);
            case "Consensus Multi Signal" -> consensusMultiSignal(context, features);
            case "Ensemble Strategy" -> ensembleStrategy(context, features);
            case "Hybrid Trend Reversion" -> hybridTrendReversion(context, features);
            case "Macro Momentum" -> macroMomentum(context, features);

            // Requires external/non-price data
            case "AI Hybrid",
                 "ML Model",
                 "Funding Rate Bias",
                 "Bitcoin Dominance Rotation",
                 "Earnings Momentum",
                 "Risk Parity Rotation",
                 "Drawdown Recovery",
                 "Defensive Rotation",
                 "AI Risk Filtered Momentum",
                 "AI Volatility Regime",
                 "News Sentiment Momentum" ->
                    noSignal(context, baseName + " requires external model/data service");

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

        double ema = sum / period;
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
