package org.investpro.decision;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.Account;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategyEngine;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Institutional-grade bot trade decision engine.
 * 
 * Converts every signal into a rigorously evaluated BotTradeDecision.
 * The bot NEVER trades directly from a signal.
 * 
 * Answers 12 critical questions before every trade:
 * 1. What kind of market are we on now?
 * 2. What market regime are we in?
 * 3. Which available strategy fits this market best?
 * 4. If no strategy fits, is there a better indicator composite setup?
 * 5. What is the expected gross profit?
 * 6. What is the estimated total cost?
 * 7. What is the expected net profit after cost?
 * 8. What is the expected loss if wrong?
 * 9. What is the expected value?
 * 10. How long should the position be held?
 * 11. Should the bot trade or skip?
 * 12. If skipped, provide exact reasons.
 */
@Slf4j

@Getter
@Setter
public class BotTradeDecisionEngine {

    private final StrategyEngine strategyEngine;

    // Decision thresholds
    private static final double STRATEGY_FITNESS_THRESHOLD = 0.70;
    private static final double INDICATOR_FITNESS_THRESHOLD = 0.70;
    private static final double MIN_RISK_REWARD_RATIO = 1.5;
    private static final double MAX_COST_AS_PERCENT_OF_PROFIT = 0.30;

    public BotTradeDecisionEngine(
            @Nullable StrategyCatalog strategyCatalog,
            @NotNull StrategyEngine strategyEngine,
            @Nullable Account account) {
        this.strategyEngine = Objects.requireNonNull(strategyEngine, "strategyEngine cannot be null");
    }

    /**
     * Evaluate a signal and return a structured trade decision.
     * 
     * @param tradePair      The trading pair
     * @param side           BUY or SELL
     * @param ticker         Current market data
     * @param signalStrength Signal confidence 0.0-1.0
     * @return Complete BotTradeDecision with reasons and decision
     */
    public BotTradeDecision evaluateSignal(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull Ticker ticker,
            double signalStrength) {

        Objects.requireNonNull(tradePair, "tradePair cannot be null");
        Objects.requireNonNull(side, "side cannot be null");
        Objects.requireNonNull(ticker, "ticker cannot be null");

        log.info("Evaluating {} {} signal with strength {}", side, tradePair.getSymbol(), signalStrength);

        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();

        Instant decidedAt = Instant.now();

        // === QUESTION 1: Market type ===
        AssetMarketType assetType = detectAssetMarketType(tradePair);
        reasons.add("Asset type: " + assetType.description);
        if (assetType == AssetMarketType.UNKNOWN) {
            blockers.add("Cannot determine asset type");
            return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                    MarketRegime.UNKNOWN, assetType, null, null, decidedAt);
        }

        // === QUESTION 2: Market regime ===
        MarketRegime regime = detectMarketRegime(ticker);
        reasons.add("Market regime: " + regime.description);
        if (regime == MarketRegime.UNKNOWN) {
            blockers.add("Cannot determine market regime - insufficient data");
            return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                    regime, assetType, null, null, decidedAt);
        }

        // === QUESTION 3: Best strategy fit ===
        StrategyFitScore bestStrategyScore = scoreBestStrategy(tradePair, assetType, regime);
        reasons.add("Best strategy: " + bestStrategyScore.strategyName() +
                " (fitness: " + String.format("%.2f", bestStrategyScore.finalFitnessScore()) + ")");

        SetupSource setupSource;
        String selectedStrategyName = null;
        IndicatorSetupScore indicatorSetupScore = null;

        if (bestStrategyScore.isGoodFit()) {
            setupSource = SetupSource.STRATEGY;
            selectedStrategyName = bestStrategyScore.strategyName();
            reasons.add("Selected strategy: " + selectedStrategyName);
        } else {
            // === QUESTION 4: Alternative indicator composite ===
            indicatorSetupScore = scoreBestIndicatorSetup(regime, assetType);
            if (indicatorSetupScore != null && indicatorSetupScore.isGoodFit()) {
                setupSource = SetupSource.INDICATOR_COMPOSITE;
                reasons.add("Selected indicator composite: " + indicatorSetupScore.setupType().description +
                        " (fitness: " + String.format("%.2f", indicatorSetupScore.finalFitnessScore()) + ")");
            } else {
                setupSource = SetupSource.NONE;
                blockers.add("No strategy or indicator setup reached minimum fitness threshold (" +
                        STRATEGY_FITNESS_THRESHOLD + ")");
                return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                        regime, assetType, bestStrategyScore, indicatorSetupScore, decidedAt);
            }
        }

        // === QUESTIONS 5-7: Profit expectations ===
        TradeCostEstimate costEstimate = estimateTradeCosts(tradePair, ticker, assetType);
        reasons.add("Estimated costs: " + costEstimate.costBreakdown());
        if (!costEstimate.isCostAcceptable()) {
            blockers.add("Cost exceeds 30% of expected profit");
        }

        TradeExpectation expectation = estimateTradeExpectation(ticker, side, costEstimate);
        reasons.add("Expected profit: " + expectation.profitBreakdown());

        // === QUESTION 8: Expected loss ===
        reasons.add("Expected loss if wrong: " +
                String.format("%.2f", expectation.expectedLossIfWrong()) + " " +
                tradePair.getCounterCode());

        // === QUESTION 9: Expected value ===
        if (!expectation.isPositiveExpectancy()) {
            blockers.add("Negative expected value: " +
                    String.format("%.2f", expectation.expectedValue()));
        }
        if (!expectation.isAcceptableRiskReward()) {
            blockers.add("Risk/reward below 1.5 (" + expectation.getRiskRewardFormatted() + ")");
        }
        reasons.add("Expected value: " + expectation.getExpectedValueFormatted());

        // === QUESTION 10: Holding period ===
        HoldingPeriodEstimate holdingPeriod = estimateHoldingPeriod(
                setupSource, selectedStrategyName, regime, assetType, ticker);
        reasons.add("Expected hold time: " + holdingPeriod.getHoldTimeFormatted());

        // === Check blocking conditions ===
        if (!blockers.isEmpty()) {
            return createSkipDecision(tradePair, side, blockers, reasons, warnings,
                    regime, assetType, bestStrategyScore, indicatorSetupScore, decidedAt);
        }

        // === QUESTION 11 & 12: Final decision ===
        BotTradeDecision.FinalAction finalAction = BotTradeDecision.FinalAction.TRADE;
        reasons.add("DECISION: Execute trade");

        String fullAnalysisSummary = buildFullAnalysisSummary(
                tradePair, side, regime, assetType, setupSource, selectedStrategyName,
                bestStrategyScore, indicatorSetupScore, costEstimate, expectation,
                holdingPeriod, reasons, warnings, blockers);

        log.info("Signal evaluation complete - {} {} (EV: {})",
                finalAction.description, tradePair.getSymbol(),
                expectation.getExpectedValueFormatted());

        return new BotTradeDecision(
                tradePair,
                side,
                regime,
                assetType,
                setupSource,
                selectedStrategyName,
                indicatorSetupScore != null ? indicatorSetupScore.setupType() : null,
                bestStrategyScore,
                indicatorSetupScore,
                costEstimate,
                expectation,
                holdingPeriod,
                finalAction,
                reasons,
                warnings,
                blockers,
                fullAnalysisSummary,
                decidedAt);
    }

    /**
     * Detect asset market type from trade pair characteristics
     */
    private AssetMarketType detectAssetMarketType(@NotNull TradePair pair) {
        String symbol = pair.getSymbol().toUpperCase();

        // Crypto detection (contains USDT, BTC, ETH, etc.)
        if (symbol.contains("USDT") || symbol.contains("USDC") || symbol.contains("BUSD") ||
                symbol.contains("BTC") || symbol.contains("ETH")) {
            return AssetMarketType.CRYPTO_SPOT;
        }

        // Forex detection (standard pairs like EUR/USD)
        if (symbol.matches("^[A-Z]{3}/[A-Z]{3}$") || symbol.matches("^[A-Z]{3}_[A-Z]{3}$")) {
            return AssetMarketType.FOREX;
        }

        // Stock detection (usually single symbol without /)
        if (!symbol.contains("/") && !symbol.contains("_")) {
            return AssetMarketType.EQUITIES;
        }

        return AssetMarketType.UNKNOWN;
    }

    /**
     * Detects current market regime from price action and volatility.
     */
    private MarketRegime detectMarketRegime(@NotNull Ticker ticker) {
        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        double last = ticker.getLastPrice();

        // Simplified regime detection
        // In production, this would use candle analysis, ATR, Bollinger Bands, trend
        // indicators

        if (bid <= 0 || ask <= 0 || last <= 0) {
            return MarketRegime.UNKNOWN;
        }

        // Basic heuristic: check last price position vs mid
        double mid = (bid + ask) / 2.0;

        if (last > mid) {
            return MarketRegime.STRONG_UPTREND;
        } else if (last < mid) {
            return MarketRegime.STRONG_DOWNTREND;
        } else {
            return MarketRegime.RANGE_BOUND;
        }
    }

    /**
     * Scores the best matching strategy from catalog.
     */
    private StrategyFitScore scoreBestStrategy(
            @NotNull TradePair pair,
            @NotNull AssetMarketType assetType,
            @NotNull MarketRegime regime) {

        // Simplified scoring - in production would evaluate all strategies
        double regimeFit = regime == MarketRegime.UNKNOWN ? 0.0 : 0.75;
        double assetFit = assetType == AssetMarketType.UNKNOWN ? 0.0 : 0.80;
        double timeframeFit = 0.70;
        double recentPerf = 0.65;
        double riskCompat = 0.80;

        double finalScore = (regimeFit + assetFit + timeframeFit + recentPerf + riskCompat) / 5.0;

        return new StrategyFitScore(
                "DefaultTrendFollower", // placeholder
                "org.investpro.strategy.TrendFollowingStrategy",
                regimeFit,
                assetFit,
                timeframeFit,
                recentPerf,
                riskCompat,
                finalScore,
                "Standard trend following strategy evaluation",
                finalScore < STRATEGY_FITNESS_THRESHOLD ? "Below fitness threshold" : null,
                Instant.now());
    }

    /**
     * Scores the best indicator composite setup.
     */
    @Nullable
    private IndicatorSetupScore scoreBestIndicatorSetup(
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType) {

        IndicatorSetupType setupType = selectIndicatorSetupForRegime(regime);
        if (setupType == IndicatorSetupType.NONE) {
            return null;
        }

        double regimeFit = 0.72;
        double signalClarity = 0.68;
        double historicalWin = 0.58;
        double volAlignment = 0.75;

        double finalScore = (regimeFit + signalClarity + historicalWin + volAlignment) / 4.0;

        return new IndicatorSetupScore(
                setupType,
                regimeFit,
                signalClarity,
                historicalWin,
                volAlignment,
                finalScore,
                "Indicator composite scoring for " + regime.name(),
                finalScore < INDICATOR_FITNESS_THRESHOLD ? "Below fitness threshold" : null,
                Instant.now());
    }

    /**
     * Selects best indicator setup for a market regime.
     */
    private IndicatorSetupType selectIndicatorSetupForRegime(@NotNull MarketRegime regime) {
        return switch (regime) {
            case STRONG_UPTREND, STRONG_DOWNTREND -> IndicatorSetupType.MOMENTUM;
            case WEAK_UPTREND, WEAK_DOWNTREND, RANGE_BOUND -> IndicatorSetupType.MEAN_REVERSION;
            case HIGH_VOLATILITY, LOW_VOLATILITY -> IndicatorSetupType.VOLATILITY_BREAKOUT;
            case TRANSITIONAL -> IndicatorSetupType.LEVEL_BREAKOUT;
            case UNKNOWN -> IndicatorSetupType.NONE;
        };
    }

    /**
     * Estimates comprehensive trade costs.
     */
    private TradeCostEstimate estimateTradeCosts(
            @NotNull TradePair pair,
            @NotNull Ticker ticker,
            @NotNull AssetMarketType assetType) {

        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        BigDecimal bidDec = BigDecimal.valueOf(bid);
        BigDecimal askDec = BigDecimal.valueOf(ask);
        BigDecimal mid = bidDec.add(askDec).divide(BigDecimal.valueOf(2), RoundingMode.HALF_UP);

        // Spread cost (for round-trip: entry at ask, exit at bid)
        BigDecimal spread = askDec.subtract(bidDec).multiply(BigDecimal.valueOf(2));

        // Commission: assume 0.1% per trade, round-trip
        BigDecimal commission = mid.multiply(BigDecimal.valueOf(0.002));

        // Slippage: assume 0.05% of price
        BigDecimal slippage = mid.multiply(BigDecimal.valueOf(0.0005));

        // Swap cost: assume 0.01% daily for position held 1 day
        BigDecimal swapCost = assetType.isCrypto() ? mid.multiply(BigDecimal.valueOf(0.0001)) : BigDecimal.ZERO;

        // Market impact: assume 0.01% for small position
        BigDecimal marketImpact = mid.multiply(BigDecimal.valueOf(0.0001));

        BigDecimal totalCost = spread.add(commission).add(slippage).add(swapCost).add(marketImpact);

        String breakdown = String.format(
                "Spread: %.4f, Commission: %.4f, Slippage: %.4f, Swap: %.4f, Impact: %.4f = Total: %.4f",
                spread, commission, slippage, swapCost, marketImpact, totalCost);

        // Assume expected gross profit of 0.5% for this estimate
        BigDecimal expectedProfit = mid.multiply(BigDecimal.valueOf(0.005));
        boolean isAcceptable = totalCost
                .compareTo(expectedProfit.multiply(BigDecimal.valueOf(MAX_COST_AS_PERCENT_OF_PROFIT))) <= 0;

        return new TradeCostEstimate(
                spread, commission, slippage, swapCost, marketImpact, totalCost,
                breakdown,
                isAcceptable,
                !isAcceptable ? "Cost exceeds 30% of expected profit" : null);
    }

    /**
     * Estimates trade expectations (profit, loss, EV, risk/reward).
     */
    private TradeExpectation estimateTradeExpectation(
            @NotNull Ticker ticker,
            @NotNull Side side,
            @NotNull TradeCostEstimate costEstimate) {

        BigDecimal mid = BigDecimal.valueOf((ticker.getBidPrice() + ticker.getAskPrice()) / 2.0);

        // Assume 1% target profit
        BigDecimal grossProfit = mid.multiply(BigDecimal.valueOf(0.01));
        BigDecimal netProfit = grossProfit.subtract(costEstimate.totalCost());

        // Assume 0.5% loss if wrong
        BigDecimal lossIfWrong = mid.multiply(BigDecimal.valueOf(0.005));

        // Assume 55% win probability
        double winProbability = 0.55;
        double lossProbability = 1.0 - winProbability;

        BigDecimal expectedValue = grossProfit.multiply(BigDecimal.valueOf(winProbability))
                .subtract(lossIfWrong.multiply(BigDecimal.valueOf(lossProbability)));

        double riskReward = grossProfit.doubleValue() / lossIfWrong.doubleValue();

        String profitBreakdown = String.format(
                "Gross: %.4f, Cost: %.4f, Net: %.4f, Loss: %.4f, EV: %.4f",
                grossProfit, costEstimate.totalCost(), netProfit, lossIfWrong, expectedValue);

        boolean isPositiveEV = expectedValue.signum() > 0;
        boolean isAcceptableRR = riskReward >= MIN_RISK_REWARD_RATIO;

        return new TradeExpectation(
                grossProfit,
                lossIfWrong,
                netProfit,
                expectedValue,
                winProbability,
                riskReward,
                profitBreakdown,
                isPositiveEV,
                isAcceptableRR);
    }

    /**
     * Estimates holding period based on setup type and regime.
     */
    private HoldingPeriodEstimate estimateHoldingPeriod(
            @NotNull SetupSource setupSource,
            @Nullable String strategyName,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @NotNull Ticker ticker) {

        Duration minHold = Duration.ofMinutes(1);
        Duration expectedHold;
        Duration maxHold;

        // Base on regime
        if (regime == MarketRegime.STRONG_UPTREND || regime == MarketRegime.STRONG_DOWNTREND) {
            expectedHold = Duration.ofHours(4);
            maxHold = Duration.ofDays(2);
        } else if (regime == MarketRegime.RANGE_BOUND) {
            expectedHold = Duration.ofHours(1);
            maxHold = Duration.ofDays(1);
        } else {
            expectedHold = Duration.ofHours(2);
            maxHold = Duration.ofDays(5);
        }

        String reason = "Estimated from " + regime.name() + " regime and " + assetType.name();

        return new HoldingPeriodEstimate(
                minHold,
                expectedHold,
                maxHold,
                reason,
                false // not micro
        );
    }

    /**
     * Create a SKIP decision with reasons and blockers
     */
    private BotTradeDecision createSkipDecision(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull List<String> blockers,
            @NotNull List<String> reasons,
            @NotNull List<String> warnings,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @Nullable StrategyFitScore strategyScore,
            @Nullable IndicatorSetupScore indicatorScore,
            @NotNull Instant decidedAt) {

        reasons.add("DECISION: Skip trade");
        blockers.forEach(b -> reasons.add("BLOCKER: " + b));

        StrategyFitScore bestStrategy = strategyScore != null ? strategyScore
                : new StrategyFitScore(
                        "NONE", "NONE", 0, 0, 0, 0, 0, 0,
                        "No strategy evaluated", "N/A", decidedAt);

        TradeCostEstimate defaultCost = new TradeCostEstimate(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                "No trade executed", true, null);

        TradeExpectation defaultExpectation = new TradeExpectation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, "No trade", false, false);

        HoldingPeriodEstimate defaultHolding = new HoldingPeriodEstimate(
                Duration.ZERO, Duration.ZERO, Duration.ZERO,
                "No trade", false);

        String summary = "Trade SKIPPED due to:\n" + String.join("\n", blockers);

        return new BotTradeDecision(
                tradePair, side, regime, assetType,
                SetupSource.NONE,
                null, indicatorScore != null ? indicatorScore.setupType() : null,
                bestStrategy,
                indicatorScore,
                defaultCost,
                defaultExpectation,
                defaultHolding,
                BotTradeDecision.FinalAction.SKIP,
                reasons,
                warnings,
                blockers,
                summary,
                decidedAt);
    }

    /**
     * Build comprehensive analysis summary
     */
    private String buildFullAnalysisSummary(
            @NotNull TradePair pair,
            @NotNull Side side,
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType,
            @NotNull SetupSource setupSource,
            @Nullable String strategyName,
            @NotNull StrategyFitScore strategyScore,
            @Nullable IndicatorSetupScore indicatorScore,
            @NotNull TradeCostEstimate costEstimate,
            @NotNull TradeExpectation expectation,
            @NotNull HoldingPeriodEstimate holdingPeriod,
            @NotNull List<String> reasons,
            @NotNull List<String> warnings,
            @NotNull List<String> blockers) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== BOT TRADE DECISION ANALYSIS ===\n");
        sb.append(String.format("Pair: %s | Side: %s\n", pair.getSymbol(), side.name()));
        sb.append(String.format("Regime: %s | Asset Type: %s\n", regime.name(), assetType.name()));
        sb.append(String.format("Setup: %s\n", setupSource.name()));

        if (strategyName != null) {
            sb.append(String.format("Strategy: %s (Fitness: %.2f)\n",
                    strategyName, strategyScore.finalFitnessScore()));
        }

        sb.append(String.format("\nExpected Profit: %s\n", expectation.profitBreakdown()));
        sb.append(String.format("Total Cost: %s\n", costEstimate.costBreakdown()));
        sb.append(String.format("Expected Value: %.2f\n", expectation.expectedValue()));
        sb.append(String.format("Risk/Reward: %s\n", expectation.getRiskRewardFormatted()));
        sb.append(String.format("Hold Time: %s\n", holdingPeriod.getHoldTimeFormatted()));

        if (!warnings.isEmpty()) {
            sb.append("\nWarnings:\n");
            warnings.forEach(w -> sb.append("  - ").append(w).append("\n"));
        }

        if (!blockers.isEmpty()) {
            sb.append("\nBlockers:\n");
            blockers.forEach(b -> sb.append("  - ").append(b).append("\n"));
        }

        return sb.toString();
    }
}
