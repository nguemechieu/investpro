package org.investpro.strategy.ai;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.StrategyBacktestResult;
import org.investpro.strategy.lifecycle.AIReviewDecision;
import org.investpro.strategy.lifecycle.AIStrategyReview;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Evaluates strategy backtest results using rule-based AI heuristics.
 *
 * <p><strong>CRITICAL:</strong> This engine reviews and validates strategies only.
 * It NEVER places orders, bypasses the RiskEngine, or makes final lifecycle decisions.</p>
 *
 * <p>All review results are advisory. The final lifecycle transition is performed
 * by {@link org.investpro.strategy.management.StrategyAssignmentManager}.</p>
 */
@Slf4j
public class AIStrategyReviewEngine {

    private static volatile AIStrategyReviewEngine instance;

    private final AtomicBoolean enabled;
    private final double minProfitFactor;
    private final double maxDrawdown;
    private final double minWinRate;
    private final int minSampleSize;

    private static final String SOURCE = "AIStrategyReviewEngine";

    private AIStrategyReviewEngine() {
        this.enabled = new AtomicBoolean(
                Boolean.parseBoolean(System.getProperty("ai.strategy.review.enabled", "true")));
        this.minProfitFactor = Double.parseDouble(
                System.getProperty("strategy.assignment.minProfitFactor", "1.3"));
        this.maxDrawdown = Double.parseDouble(
                System.getProperty("strategy.assignment.maxDrawdown", "0.10"));
        this.minWinRate = Double.parseDouble(
                System.getProperty("strategy.assignment.minWinRate", "0.45"));
        this.minSampleSize = Integer.parseInt(
                System.getProperty("strategy.assignment.minSampleSize", "30"));
        log.info("AIStrategyReviewEngine initialised: enabled={}, minPF={}, maxDD={}, minWR={}, minN={}",
                enabled.get(), minProfitFactor, maxDrawdown, minWinRate, minSampleSize);
    }

    /**
     * Returns the singleton instance (double-checked locking, Java 21 volatile).
     *
     * @return singleton AIStrategyReviewEngine
     */
    public static AIStrategyReviewEngine getInstance() {
        AIStrategyReviewEngine local = instance;
        if (local == null) {
            synchronized (AIStrategyReviewEngine.class) {
                local = instance;
                if (local == null) {
                    local = new AIStrategyReviewEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Reviews a strategy backtest result and returns an AI decision.
     *
     * @param backtest the backtest result to evaluate
     * @return AIStrategyReview containing the AI decision and reasoning
     */
    public AIStrategyReview reviewBacktest(StrategyBacktestResult backtest) {
        if (backtest == null) {
            log.warn("reviewBacktest called with null backtest result");
            return buildRejectReview(null, null, null, "NULL",
                    List.of("Backtest result was null"), 0.0);
        }
        log.debug("Reviewing backtest for strategy={} symbol={} timeframe={}",
                backtest.getStrategyId(), backtest.getSymbol(), backtest.getTimeframe());

        List<String> rejectionReasons = new ArrayList<>();

        // --- Statistical checks ---
        boolean sampleSufficient = backtest.getTotalTrades() >= minSampleSize;
        if (!sampleSufficient) {
            rejectionReasons.add("Sample size " + backtest.getTotalTrades()
                    + " below minimum " + minSampleSize);
        }

        boolean statMeaningful = sampleSufficient && backtest.getTotalTrades() >= 30;

        // --- Overfit heuristic: high profit factor with tiny sample ---
        boolean overfitWarning = backtest.getProfitFactor() > 3.0
                && backtest.getTotalTrades() < 50;
        if (overfitWarning) {
            rejectionReasons.add("Potential overfitting: profit factor "
                    + backtest.getProfitFactor() + " with only " + backtest.getTotalTrades() + " trades");
        }

        boolean underfitWarning = backtest.getProfitFactor() < 1.05
                && backtest.getWinRate() < 0.35;

        // --- Threshold checks ---
        boolean pfAcceptable = backtest.getProfitFactor() >= minProfitFactor;
        if (!pfAcceptable) {
            rejectionReasons.add("Profit factor " + backtest.getProfitFactor()
                    + " below minimum " + minProfitFactor);
        }

        boolean ddAcceptable = backtest.getMaxDrawdown() <= maxDrawdown;
        if (!ddAcceptable) {
            rejectionReasons.add("Max drawdown " + String.format("%.1f%%", backtest.getMaxDrawdown() * 100)
                    + " exceeds limit " + String.format("%.1f%%", maxDrawdown * 100));
        }

        boolean wrAcceptable = backtest.getWinRate() >= minWinRate;
        if (!wrAcceptable) {
            rejectionReasons.add("Win rate " + String.format("%.1f%%", backtest.getWinRate() * 100)
                    + " below minimum " + String.format("%.1f%%", minWinRate * 100));
        }

        // --- Regime compatibility (heuristic from Sharpe) ---
        double regimeCompatibility = Math.min(Math.max(backtest.getSharpeRatio() / 2.0, 0.0), 1.0);

        // --- Determine decision ---
        AIReviewDecision decision;
        double confidence;
        String recommendedNextStep;

        if (!sampleSufficient) {
            decision = AIReviewDecision.NEEDS_MORE_DATA;
            confidence = 0.3;
            recommendedNextStep = "Collect at least " + minSampleSize + " more backtest trades before re-review.";
        } else if (!rejectionReasons.isEmpty()) {
            if (pfAcceptable && ddAcceptable && wrAcceptable && overfitWarning) {
                decision = AIReviewDecision.PAPER_TRADE_FIRST;
                confidence = 0.55;
                recommendedNextStep = "Run paper trading to validate out-of-sample performance before live approval.";
            } else {
                decision = AIReviewDecision.REJECT;
                confidence = 0.80;
                recommendedNextStep = "Strategy does not meet minimum requirements. Optimise parameters and re-test.";
            }
        } else {
            decision = AIReviewDecision.APPROVE;
            confidence = 0.75 + (regimeCompatibility * 0.15) + (sampleSufficient ? 0.10 : 0.0);
            confidence = Math.min(confidence, 0.95);
            recommendedNextStep = "Proceed to paper trading validation phase.";
        }

        String reasoning = buildReasoning(backtest, sampleSufficient, pfAcceptable, ddAcceptable, wrAcceptable,
                overfitWarning, regimeCompatibility, decision);

        AIStrategyReview review = AIStrategyReview.builder()
                .reviewId(UUID.randomUUID().toString())
                .strategyId(backtest.getStrategyId())
                .symbol(backtest.getSymbol())
                .timeframe(backtest.getTimeframe())
                .decision(decision)
                .aiConfidence(confidence)
                .reasoningSummary(reasoning)
                .rejectionReasons(rejectionReasons)
                .overfitWarning(overfitWarning)
                .underfitWarning(underfitWarning)
                .sampleSizeSufficient(sampleSufficient)
                .profitFactorAcceptable(pfAcceptable)
                .drawdownAcceptable(ddAcceptable)
                .regimeCompatibilityScore(regimeCompatibility)
                .statisticallyMeaningful(statMeaningful)
                .recommendedNextStep(recommendedNextStep)
                .reviewedAt(Instant.now())
                .build();

        log.info("Backtest review complete: strategy={} decision={} confidence={}",
                backtest.getStrategyId(), decision, String.format("%.2f", confidence));

        EventBusManager.getInstance().publish(
                AgentEvent.of(AgentEvent.AI_STRATEGY_BACKTEST_REVIEWED, SOURCE, review));

        return review;
    }

    /** @return whether the AI review engine is currently active. */
    public boolean isEnabled() {
        return enabled.get();
    }

    /**
     * Enables or disables the review engine at runtime.
     *
     * @param value true to enable, false to disable
     */
    public void setEnabled(boolean value) {
        enabled.set(value);
        log.info("AIStrategyReviewEngine enabled={}", value);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private AIStrategyReview buildRejectReview(String strategyId, String symbol, String timeframe,
                                               String reason, List<String> reasons, double confidence) {
        return AIStrategyReview.builder()
                .reviewId(UUID.randomUUID().toString())
                .strategyId(strategyId != null ? strategyId : "UNKNOWN")
                .symbol(symbol != null ? symbol : "UNKNOWN")
                .timeframe(timeframe != null ? timeframe : "UNKNOWN")
                .decision(AIReviewDecision.REJECT)
                .aiConfidence(confidence)
                .reasoningSummary(reason)
                .rejectionReasons(reasons)
                .sampleSizeSufficient(false)
                .profitFactorAcceptable(false)
                .drawdownAcceptable(false)
                .statisticallyMeaningful(false)
                .reviewedAt(Instant.now())
                .build();
    }

    private String buildReasoning(StrategyBacktestResult bt, boolean sampleOk, boolean pfOk,
                                  boolean ddOk, boolean wrOk, boolean overfit,
                                  double regimeScore, AIReviewDecision decision) {
        return String.format(
                "Backtest review for %s on %s/%s: trades=%d, winRate=%.1f%%, PF=%.2f, maxDD=%.1f%%, "
                + "Sharpe=%.2f, regimeCompat=%.2f. "
                + "SampleOK=%b, PfOK=%b, DdOK=%b, WrOK=%b, overfitRisk=%b. Decision: %s.",
                bt.getStrategyId(), bt.getSymbol(), bt.getTimeframe(),
                bt.getTotalTrades(), bt.getWinRate() * 100,
                bt.getProfitFactor(), bt.getMaxDrawdown() * 100,
                bt.getSharpeRatio(), regimeScore,
                sampleOk, pfOk, ddOk, wrOk, overfit, decision);
    }
}
