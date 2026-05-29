package org.investpro.decision;

import lombok.extern.slf4j.Slf4j;
import org.investpro.execution.TradeCostEstimator;
import org.investpro.execution.TradePlanGenerator;
import org.investpro.indicators.IndicatorSetupScoringService;
import org.investpro.market.AssetMarketTypeDetector;
import org.investpro.market.MarketRegimeAnalysis;
import org.investpro.market.MarketRegimeAnalyzer;
import org.investpro.market.TickerValidationResult;
import org.investpro.market.TickerValidationService;
import org.investpro.models.Account;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.risk.AccountTradeValidator;
import org.investpro.risk.AccountValidationResult;
import org.investpro.risk.SpreadValidationResult;
import org.investpro.risk.SpreadValidationService;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyFitScoringService;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Service-driven pre-trade decision pipeline.
 * Signal and decision outcomes are not trades. Only broker-confirmed fills are trades.
 */
@Slf4j
public class BotTradeDecisionEngine {

    private static final double SIGNAL_STRENGTH_MIN = 0.3;
    private static final double SIGNAL_STRENGTH_MAX = 1.0;
    private static final double STRATEGY_FITNESS_THRESHOLD = 0.70;
    private static final double MIN_RISK_REWARD_RATIO = 1.5;

    private final Account account;

    private final TickerValidationService tickerValidationService;
    private final SpreadValidationService spreadValidationService;
    private final AssetMarketTypeDetector assetTypeDetector;
    private final MarketRegimeAnalyzer marketRegimeAnalyzer;
    private final StrategyFitScoringService strategyFitScoringService;
    private final IndicatorSetupScoringService indicatorSetupScoringService;
    private final TradePlanGenerator tradePlanGenerator;
    private final TradeCostEstimator tradeCostEstimator;
    private final TradeExpectationEstimator tradeExpectationEstimator;
    private final AccountTradeValidator accountTradeValidator;

    public BotTradeDecisionEngine(@Nullable Account account) {
        this.account = account;
        this.tickerValidationService = new TickerValidationService();
        this.spreadValidationService = new SpreadValidationService();
        this.assetTypeDetector = new AssetMarketTypeDetector();
        this.marketRegimeAnalyzer = new MarketRegimeAnalyzer();
        this.strategyFitScoringService = new StrategyFitScoringService();
        this.indicatorSetupScoringService = new IndicatorSetupScoringService();
        this.tradePlanGenerator = new TradePlanGenerator();
        this.tradeCostEstimator = new TradeCostEstimator();
        this.tradeExpectationEstimator = new TradeExpectationEstimator();
        this.accountTradeValidator = new AccountTradeValidator();
    }

    @NotNull
    public BotTradeDecision evaluateSignal(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            @NotNull Ticker ticker,
            double signalStrength) {
        MarketContext context = MarketContext.of(tradePair, side, ticker, signalStrength, account);
        SignalDecision signalDecision = SignalDecision.of(side, signalStrength);
        return evaluateSignal(context, signalDecision);
    }

    @NotNull
    public BotTradeDecision evaluateSignal(@NotNull MarketContext context, @NotNull SignalDecision signalDecision) {
        List<String> reasons = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> blockers = new ArrayList<>();
        Instant decidedAt = Instant.now();

        if (!isValidSignalStrength(signalDecision.signalStrength())) {
            blockers.add(String.format(
                    "Invalid signal strength: %.2f (must be %.2f-%.2f)",
                    signalDecision.signalStrength(), SIGNAL_STRENGTH_MIN, SIGNAL_STRENGTH_MAX));
            return createSkipDecision(context.tradePair(), context.side(), blockers, reasons, warnings,
                    MarketRegime.UNKNOWN, AssetMarketType.UNKNOWN, null, null, decidedAt);
        }
        reasons.add(String.format("Signal strength: %.2f (VALID)", signalDecision.signalStrength()));

        TickerValidationResult tickerValidation = tickerValidationService.validate(context.ticker());
        warnings.addAll(tickerValidation.warnings());
        if (!tickerValidation.valid()) {
            blockers.addAll(tickerValidation.errors());
            return createSkipDecision(context.tradePair(), context.side(), blockers, reasons, warnings,
                    MarketRegime.UNKNOWN, AssetMarketType.UNKNOWN, null, null, decidedAt);
        }

        AssetMarketType assetType = assetTypeDetector.detect(context.tradePair());
        reasons.add("Asset type: " + assetType.name() + " - " + assetType.description);

        SpreadValidationResult spreadValidation = spreadValidationService.validate(context.ticker(), assetType);
        warnings.addAll(spreadValidation.warnings());
        if (!spreadValidation.acceptable()) {
            blockers.add(spreadValidation.reason());
        } else {
            reasons.add(spreadValidation.reason());
        }

        MarketRegimeAnalysis regimeAnalysis = marketRegimeAnalyzer.analyze(context.ticker(), assetType);
        warnings.addAll(regimeAnalysis.warnings());
        reasons.addAll(regimeAnalysis.reasons());
        reasons.add(String.format(
                "Market regime: %s (confidence: %.0f%%)",
                regimeAnalysis.regime().name(), regimeAnalysis.confidence() * 100));

        StrategyFitScore bestStrategyScore = scoreBestStrategyAcrossCatalog(context, assetType, regimeAnalysis.regime(),
                signalDecision.signalStrength());
        reasons.add(String.format(
                "Best strategy: %s (fitness: %.2f)",
                bestStrategyScore.strategyName(), bestStrategyScore.finalFitnessScore()));

        SetupSource setupSource;
        String selectedStrategyName = null;
        IndicatorSetupScore indicatorSetupScore = null;

        if (bestStrategyScore.isGoodFit()) {
            setupSource = SetupSource.STRATEGY;
            selectedStrategyName = bestStrategyScore.strategyName();
            reasons.add("Selected setup: Strategy - " + selectedStrategyName);
        } else {
            indicatorSetupScore = indicatorSetupScoringService.score(context.side(), assetType, regimeAnalysis.regime());
            if (indicatorSetupScore.isGoodFit()) {
                setupSource = SetupSource.INDICATOR_COMPOSITE;
                reasons.add(String.format(
                        "Selected setup: Indicator - %s (fitness: %.2f)",
                        indicatorSetupScore.setupType().description,
                        indicatorSetupScore.finalFitnessScore()));
            } else {
                setupSource = SetupSource.NONE;
                blockers.add(String.format(
                        "No setup reached minimum fitness (strategy: %.2f, indicator: %.2f vs threshold: %.2f)",
                        bestStrategyScore.finalFitnessScore(),
                        indicatorSetupScore.finalFitnessScore(),
                        STRATEGY_FITNESS_THRESHOLD));
                return createSkipDecision(context.tradePair(), context.side(), blockers, reasons, warnings,
                        regimeAnalysis.regime(), assetType, bestStrategyScore, indicatorSetupScore, decidedAt);
            }
        }

        double accountBalance = account == null
                ? 10_000.0
                : Math.max(1_000.0, Math.max(account.getEquity(), account.getTotalBalance()));
        TradePlan tradePlan = tradePlanGenerator.generate(
                context.tradePair(), context.side(), context.ticker(), signalDecision.signalStrength(), accountBalance);

        if (!tradePlan.isValid()) {
            blockers.add("Failed to generate valid trade plan");
            return createSkipDecision(context.tradePair(), context.side(), blockers, reasons, warnings,
                    regimeAnalysis.regime(), assetType, bestStrategyScore, indicatorSetupScore, decidedAt);
        }
        reasons.add("Trade plan: " + tradePlan.getSummary());

        TradeCostEstimate costEstimate = tradeCostEstimator.estimate(context.ticker(), tradePlan.positionSize().doubleValue());
        reasons.add("Estimated costs: " + costEstimate.costBreakdown());

        TradeExpectation expectation = tradeExpectationEstimator.estimate(tradePlan, costEstimate, assetType);
        reasons.add("Expected profit: " + expectation.profitBreakdown());

        if (!expectation.isPositiveExpectancy()) {
            blockers.add(String.format("Negative expected value: %.4f", expectation.expectedValue()));
        }
        if (!expectation.isAcceptableRiskReward()) {
            blockers.add(String.format(
                    "Unacceptable risk/reward: %.2f:1 (need >= %.1f:1)",
                    expectation.riskRewardRatio(), MIN_RISK_REWARD_RATIO));
        }

        AccountValidationResult accountValidation = accountTradeValidator.validate(account, tradePlan);
        reasons.addAll(accountValidation.reasons());
        warnings.addAll(accountValidation.warnings());
        blockers.addAll(accountValidation.blockers());

        if (!blockers.isEmpty()) {
            return createSkipDecision(context.tradePair(), context.side(), blockers, reasons, warnings,
                    regimeAnalysis.regime(), assetType, bestStrategyScore, indicatorSetupScore, decidedAt);
        }

        BotTradeDecision.FinalAction finalAction = context.side() == Side.HOLD
                ? BotTradeDecision.FinalAction.HOLD
                : BotTradeDecision.FinalAction.TRADE;
        reasons.add("DECISION: " + finalAction.description);

        String fullAnalysisSummary = buildFullAnalysisSummary(
                context.tradePair(), context.side(), regimeAnalysis.regime(), assetType,
                setupSource, selectedStrategyName, bestStrategyScore, indicatorSetupScore,
                costEstimate, expectation, tradePlan, reasons, warnings, blockers);

        return new BotTradeDecision(
                context.tradePair(),
                context.side(),
                regimeAnalysis.regime(),
                assetType,
                setupSource,
                selectedStrategyName,
                indicatorSetupScore != null ? indicatorSetupScore.setupType() : null,
                bestStrategyScore,
                indicatorSetupScore,
                costEstimate,
                expectation,
                createHoldingPeriodEstimate(regimeAnalysis.regime(), assetType),
                finalAction,
                reasons,
                warnings,
                blockers,
                fullAnalysisSummary,
                decidedAt);
    }

    private boolean isValidSignalStrength(double signalStrength) {
        return signalStrength >= SIGNAL_STRENGTH_MIN && signalStrength <= SIGNAL_STRENGTH_MAX;
    }

    @NotNull
    private StrategyFitScore scoreBestStrategyAcrossCatalog(
            @NotNull MarketContext context,
            @NotNull AssetMarketType assetType,
            @NotNull MarketRegime regime,
            double signalStrength) {
        List<String> availableStrategies = StrategyCatalog.availableStrategyNames();
        if (availableStrategies.isEmpty()) {
            return new StrategyFitScore(
                    "Default Trend Following",
                    "org.investpro.strategy.TrendFollowing",
                    0.50,
                    0.50,
                    0.70,
                    0.60,
                    signalStrength,
                    0.56,
                    "Fallback strategy used because catalog is empty",
                    "Strategy catalog is empty",
                    Instant.now());
        }

        StrategyFitScore bestScore = null;
        for (String strategyName : availableStrategies) {
            StrategyFitScore score = strategyFitScoringService.score(
                    strategyName, context.side(), assetType, regime, signalStrength);
            if (bestScore == null || score.finalFitnessScore() > bestScore.finalFitnessScore()) {
                bestScore = score;
            }
        }
        return bestScore;
    }

    private HoldingPeriodEstimate createHoldingPeriodEstimate(
            @NotNull MarketRegime regime,
            @NotNull AssetMarketType assetType) {
        Duration expectedHold = switch (regime) {
            case STRONG_UPTREND, STRONG_DOWNTREND -> Duration.ofHours(4);
            case RANGE_BOUND -> Duration.ofHours(1);
            default -> Duration.ofHours(2);
        };

        return new HoldingPeriodEstimate(
                Duration.ofMinutes(1),
                expectedHold,
                Duration.ofDays(assetType.is24Hour ? 3 : 5),
                "Estimated from " + regime.name(),
                false);
    }

    @NotNull
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
                        "No strategy evaluated", "Trade skipped", decidedAt);

        TradeCostEstimate defaultCost = new TradeCostEstimate(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, "No trade executed", true, null);

        TradeExpectation defaultExpectation = new TradeExpectation(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                0, 0, "No trade", false, false);

        HoldingPeriodEstimate defaultHolding = new HoldingPeriodEstimate(
                Duration.ZERO, Duration.ZERO, Duration.ZERO,
                "No trade", false);

        String summary = "Trade SKIPPED due to:\n" + String.join("\n", blockers);

        return new BotTradeDecision(
                tradePair,
                side,
                regime,
                assetType,
                SetupSource.NONE,
                null,
                indicatorScore != null ? indicatorScore.setupType() : null,
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

    @NotNull
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
            @NotNull TradePlan tradePlan,
            @NotNull List<String> reasons,
            @NotNull List<String> warnings,
            @NotNull List<String> blockers) {

        StringBuilder sb = new StringBuilder();
        sb.append("=== INSTITUTIONAL BOT TRADE DECISION ANALYSIS ===\n");
        sb.append(String.format("Pair: %s | Side: %s\n", pair.getSymbol(), side.name()));
        sb.append(String.format("Regime: %s | Asset Type: %s\n", regime.name(), assetType.name()));
        sb.append(String.format("Setup: %s\n", setupSource.name()));

        if (strategyName != null) {
            sb.append(String.format("Strategy: %s (Fitness: %.2f)\n",
                    strategyName, strategyScore.finalFitnessScore()));
        }

        if (indicatorScore != null) {
            sb.append(String.format("Indicator setup: %s (Fitness: %.2f)\n",
                    indicatorScore.setupType().description,
                    indicatorScore.finalFitnessScore()));
        }

        sb.append("\n--- TRADE PLAN ---\n");
        sb.append(tradePlan.getSummary()).append("\n");

        sb.append("\n--- COSTS & EXPECTATIONS ---\n");
        sb.append(String.format("Cost Breakdown: %s\n", costEstimate.costBreakdown()));
        sb.append(String.format("Profit Breakdown: %s\n", expectation.profitBreakdown()));
        sb.append(String.format("Expected Value: %.4f\n", expectation.expectedValue()));
        sb.append(String.format("Risk/Reward: %s\n", expectation.getRiskRewardFormatted()));

        if (!warnings.isEmpty()) {
            sb.append("\n--- WARNINGS ---\n");
            warnings.forEach(w -> sb.append("! ").append(w).append("\n"));
        }

        if (!blockers.isEmpty()) {
            sb.append("\n--- BLOCKERS ---\n");
            blockers.forEach(b -> sb.append("X ").append(b).append("\n"));
        }

        return sb.toString();
    }
}
