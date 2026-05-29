package org.investpro.strategy.ai;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.event.EventBusManager;
import org.investpro.strategy.lifecycle.AIReviewDecision;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyPerformanceMetrics;
import org.investpro.strategy.lifecycle.StrategyValidationReport;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Validates whether a strategy in the paper trading phase is ready for live deployment.
 *
 * <p><strong>CRITICAL:</strong> This engine is a validator/supervisor only.
 * It NEVER places orders or bypasses RiskEngine controls.</p>
 */
@Slf4j
public class AIStrategyValidationEngine {

    private static volatile AIStrategyValidationEngine instance;

    private final AtomicBoolean enabled;
    private final int minPaperTrades;
    private final double minPaperWinRate;
    private final double minPaperProfitFactor;

    private static final String SOURCE = "AIStrategyValidationEngine";

    private AIStrategyValidationEngine() {
        this.enabled = new AtomicBoolean(true);
        this.minPaperTrades = Integer.parseInt(
                System.getProperty("strategy.assignment.paperTradeTrades", "50"));
        this.minPaperWinRate = Double.parseDouble(
                System.getProperty("strategy.assignment.minWinRate", "0.45"));
        this.minPaperProfitFactor = Double.parseDouble(
                System.getProperty("strategy.assignment.minProfitFactor", "1.3"));
        log.info("AIStrategyValidationEngine initialised: minTrades={}, minWR={}, minPF={}",
                minPaperTrades, minPaperWinRate, minPaperProfitFactor);
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton AIStrategyValidationEngine
     */
    public static AIStrategyValidationEngine getInstance() {
        AIStrategyValidationEngine local = instance;
        if (local == null) {
            synchronized (AIStrategyValidationEngine.class) {
                local = instance;
                if (local == null) {
                    local = new AIStrategyValidationEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Validates paper trading results for a strategy assignment.
     *
     * @param record       the lifecycle record for the strategy being validated
     * @param paperMetrics list of performance metrics snapshots from paper trading
     * @return StrategyValidationReport with the AI's assessment
     */
    public StrategyValidationReport validatePaperTrades(
            StrategyLifecycleRecord record,
            List<StrategyPerformanceMetrics> paperMetrics) {

        if (record == null || paperMetrics == null || paperMetrics.isEmpty()) {
            log.warn("validatePaperTrades called with null/empty input");
            return buildRejectReport(null, "NULL", "NULL", "NULL",
                    List.of("Invalid input: record or metrics were null/empty"), 0.0);
        }

        log.debug("Validating paper trades for assignment={} strategy={}",
                record.getAssignmentId(), record.getStrategyId());

        // Use the most recent metrics snapshot
        StrategyPerformanceMetrics latest = paperMetrics.get(paperMetrics.size() - 1);

        List<String> rejectionReasons = new ArrayList<>();

        int totalTrades = latest.getTotalTrades();
        if (totalTrades < minPaperTrades) {
            rejectionReasons.add("Paper trades " + totalTrades + " below minimum " + minPaperTrades);
        }

        boolean wrOk = latest.getWinRate() >= minPaperWinRate;
        if (!wrOk) {
            rejectionReasons.add(String.format("Win rate %.1f%% below minimum %.1f%%",
                    latest.getWinRate() * 100, minPaperWinRate * 100));
        }

        boolean pfOk = latest.getProfitFactor() >= minPaperProfitFactor;
        if (!pfOk) {
            rejectionReasons.add(String.format("Profit factor %.2f below minimum %.2f",
                    latest.getProfitFactor(), minPaperProfitFactor));
        }

        boolean ddOk = latest.getMaxDrawdown() <= 0.15;
        if (!ddOk) {
            rejectionReasons.add(String.format("Drawdown %.1f%% exceeds limit 15%%",
                    latest.getMaxDrawdown() * 100));
        }

        boolean behavingAsExpected = wrOk && pfOk;
        boolean performanceDegrading = latest.getWinRate() < minPaperWinRate * 0.9;
        boolean regimeChanging = latest.getRegimePerformance() != null
                && latest.getRegimePerformance().values().stream().anyMatch(v -> v < 0.3);
        boolean confidenceStillValid = latest.getConfidenceAccuracy() >= 0.50;

        double signalQualityScore = Math.min(latest.getSignalAccuracy(), 1.0);
        double executionQualityScore = Math.min(latest.getProfitFactor() / 3.0, 1.0);
        double riskBehaviorScore = 1.0 - Math.min(latest.getMaxDrawdown() / 0.15, 1.0);
        double consistencyScore = Math.min(latest.getSharpeRatio() / 2.0, 1.0);
        consistencyScore = Math.max(consistencyScore, 0.0);

        boolean approvedForLive = rejectionReasons.isEmpty()
                && behavingAsExpected
                && !performanceDegrading
                && confidenceStillValid;

        AIReviewDecision decision;
        double confidence;
        String reasoning;

        if (totalTrades < minPaperTrades) {
            decision = AIReviewDecision.NEEDS_MORE_DATA;
            confidence = 0.40;
            reasoning = String.format("Only %d paper trades completed; %d required for validation.",
                    totalTrades, minPaperTrades);
        } else if (approvedForLive) {
            decision = AIReviewDecision.APPROVE;
            confidence = 0.75 + (consistencyScore * 0.15);
            reasoning = String.format(
                    "Paper trading validated: WR=%.1f%%, PF=%.2f, DD=%.1f%%. Strategy is behaving as expected.",
                    latest.getWinRate() * 100, latest.getProfitFactor(), latest.getMaxDrawdown() * 100);
        } else {
            decision = AIReviewDecision.REJECT;
            confidence = 0.75;
            reasoning = "Strategy failed paper trading validation: " + String.join("; ", rejectionReasons);
        }

        // Estimate period in days (approximation from trades and duration)
        int periodDays = totalTrades > 0
                ? (int) Math.ceil(totalTrades * latest.getAverageTradeDuration() / 60.0 / 24.0)
                : 0;

        StrategyValidationReport report = StrategyValidationReport.builder()
                .reportId(UUID.randomUUID().toString())
                .assignmentId(record.getAssignmentId())
                .strategyId(record.getStrategyId())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .paperTradePeriodDays(periodDays)
                .totalPaperTrades(totalTrades)
                .paperWinRate(latest.getWinRate())
                .paperProfitFactor(latest.getProfitFactor())
                .paperDrawdown(latest.getMaxDrawdown())
                .paperExpectancy(latest.getExpectancy())
                .signalQualityScore(signalQualityScore)
                .executionQualityScore(executionQualityScore)
                .riskBehaviorScore(riskBehaviorScore)
                .consistencyScore(consistencyScore)
                .aiDecision(decision)
                .aiConfidence(confidence)
                .aiReasoningSummary(reasoning)
                .behavingAsExpected(behavingAsExpected)
                .performanceDegrading(performanceDegrading)
                .regimeChanging(regimeChanging)
                .confidenceStillValid(confidenceStillValid)
                .approvedForLive(approvedForLive)
                .rejectionReasons(rejectionReasons)
                .validatedAt(Instant.now())
                .build();

        log.info("Validation complete: assignment={} decision={} approvedForLive={}",
                record.getAssignmentId(), decision, approvedForLive);

        EventBusManager.getInstance().publish(
                AgentEvent.of(AgentEvent.AI_STRATEGY_VALIDATION_REVIEWED, SOURCE, report));

        return report;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private StrategyValidationReport buildRejectReport(String assignmentId,
                                                       String strategyId, String symbol, String timeframe,
                                                       List<String> reasons, double confidence) {
        return StrategyValidationReport.builder()
                .reportId(UUID.randomUUID().toString())
                .assignmentId(assignmentId != null ? assignmentId : "UNKNOWN")
                .strategyId(strategyId)
                .symbol(symbol)
                .timeframe(timeframe)
                .aiDecision(AIReviewDecision.REJECT)
                .aiConfidence(confidence)
                .aiReasoningSummary("Validation failed: " + String.join("; ", reasons))
                .rejectionReasons(reasons)
                .approvedForLive(false)
                .validatedAt(Instant.now())
                .build();
    }
}
