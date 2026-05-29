package org.investpro.strategy.performance;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.lifecycle.StrategyPerformanceMetrics;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe tracker for strategy performance metrics.
 * Records trade outcomes and maintains running statistics per assignment.
 *
 * <p>Metrics are maintained in memory and can be persisted via
 * {@link org.investpro.strategy.persistence.StrategyLifecyclePersistenceService}.</p>
 */
@Slf4j
public class StrategyPerformanceTracker {

    private static volatile StrategyPerformanceTracker instance;

    /** Raw performance accumulators keyed by assignmentId. */
    private final ConcurrentHashMap<String, MetricsAccumulator> accumulators =
            new ConcurrentHashMap<>();

    private StrategyPerformanceTracker() {
        log.info("StrategyPerformanceTracker initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton StrategyPerformanceTracker
     */
    public static StrategyPerformanceTracker getInstance() {
        StrategyPerformanceTracker local = instance;
        if (local == null) {
            synchronized (StrategyPerformanceTracker.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyPerformanceTracker();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Records a completed trade outcome and updates running metrics.
     *
     * @param assignmentId    the strategy assignment identifier
     * @param won             true if the trade was profitable
     * @param pnl             profit or loss in account currency (negative = loss)
     * @param confidence      signal confidence that triggered the trade (0.0-1.0)
     * @param aiApproved      true if the AI signal review approved this trade
     * @param durationMinutes trade duration in minutes
     */
    public void recordTradeOutcome(String assignmentId, boolean won, double pnl,
                                   double confidence, boolean aiApproved, long durationMinutes) {
        MetricsAccumulator acc = accumulators.computeIfAbsent(assignmentId, k -> new MetricsAccumulator());
        synchronized (acc) {
            acc.totalTrades++;
            if (won) {
                acc.winningTrades++;
                acc.grossProfit += pnl;
                acc.totalConfidenceOnWins += confidence;
                if (aiApproved) acc.aiApprovedWins++;
            } else {
                acc.losingTrades++;
                acc.grossLoss += Math.abs(pnl);
                acc.totalConfidenceOnLosses += confidence;
            }
            if (aiApproved) acc.aiApprovedTotal++;
            acc.totalDurationMinutes += durationMinutes;
            acc.netProfit += pnl;
            acc.pnlHistory.add(pnl);

            // Running drawdown (peak-to-trough)
            acc.equity += pnl;
            if (acc.equity > acc.peakEquity) acc.peakEquity = acc.equity;
            double currentDD = (acc.peakEquity - acc.equity) / Math.max(acc.peakEquity, 1.0);
            if (currentDD > acc.maxDrawdown) acc.maxDrawdown = currentDD;

            log.debug("Trade recorded for assignment={}: won={}, pnl={}, totalTrades={}",
                    assignmentId, won, pnl, acc.totalTrades);
        }
    }

    /**
     * Returns a snapshot of current performance metrics for an assignment.
     *
     * @param assignmentId the assignment identifier
     * @return StrategyPerformanceMetrics snapshot, or zero metrics if no trades recorded
     */
    public StrategyPerformanceMetrics getMetrics(String assignmentId) {
        MetricsAccumulator acc = accumulators.get(assignmentId);
        if (acc == null) {
            return buildZeroMetrics(assignmentId);
        }
        synchronized (acc) {
            return buildMetrics(assignmentId, acc);
        }
    }

    /**
     * Updates Sharpe and Sortino ratios for an assignment from a return series.
     *
     * @param assignmentId the assignment identifier
     * @param returns      list of per-trade returns (as fractions of equity)
     */
    public void updateSharpeAndSortino(String assignmentId, List<Double> returns) {
        if (returns == null || returns.size() < 2) return;
        MetricsAccumulator acc = accumulators.computeIfAbsent(assignmentId, k -> new MetricsAccumulator());
        synchronized (acc) {
            double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double riskFreeRate = 0.0; // assume 0 for strategy comparison
            double variance = returns.stream()
                    .mapToDouble(r -> (r - mean) * (r - mean))
                    .average().orElse(0.0);
            double stdDev = Math.sqrt(variance);
            acc.sharpeRatio = stdDev > 0 ? (mean - riskFreeRate) / stdDev * Math.sqrt(252) : 0.0;

            // Sortino: downside deviation only
            double downsideVariance = returns.stream()
                    .filter(r -> r < riskFreeRate)
                    .mapToDouble(r -> (r - riskFreeRate) * (r - riskFreeRate))
                    .average().orElse(0.0);
            double downsideDeviation = Math.sqrt(downsideVariance);
            acc.sortinoRatio = downsideDeviation > 0 ? (mean - riskFreeRate) / downsideDeviation * Math.sqrt(252) : 0.0;
        }
    }

    /**
     * Returns all metrics snapshots for all tracked assignments.
     *
     * @return list of StrategyPerformanceMetrics
     */
    public List<StrategyPerformanceMetrics> getAllMetrics() {
        List<StrategyPerformanceMetrics> result = new ArrayList<>();
        for (Map.Entry<String, MetricsAccumulator> entry : accumulators.entrySet()) {
            synchronized (entry.getValue()) {
                result.add(buildMetrics(entry.getKey(), entry.getValue()));
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Clears all accumulated metrics for an assignment (e.g. after demotion).
     *
     * @param assignmentId the assignment identifier to clear
     */
    public void clearMetrics(String assignmentId) {
        accumulators.remove(assignmentId);
        log.info("Metrics cleared for assignment={}", assignmentId);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private StrategyPerformanceMetrics buildMetrics(String assignmentId, MetricsAccumulator acc) {
        double winRate = acc.totalTrades > 0 ? (double) acc.winningTrades / acc.totalTrades : 0.0;
        double profitFactor = acc.grossLoss > 0 ? acc.grossProfit / acc.grossLoss : acc.grossProfit > 0 ? 9.99 : 1.0;
        double avgDuration = acc.totalTrades > 0 ? (double) acc.totalDurationMinutes / acc.totalTrades : 0L;
        double expectancy = acc.totalTrades > 0 ? acc.netProfit / acc.totalTrades : 0.0;
        double signalAccuracy = winRate;
        double confidenceAcc = acc.totalTrades > 0
                ? acc.totalConfidenceOnWins / Math.max(acc.totalTrades, 1) : 0.0;
        double aiApprovalAcc = acc.aiApprovedTotal > 0
                ? (double) acc.aiApprovedWins / acc.aiApprovedTotal : 0.0;

        return StrategyPerformanceMetrics.builder()
                .assignmentId(assignmentId)
                .symbol("")
                .timeframe("")
                .netProfit(acc.netProfit)
                .winRate(winRate)
                .profitFactor(profitFactor)
                .maxDrawdown(acc.maxDrawdown)
                .expectancy(expectancy)
                .sharpeRatio(acc.sharpeRatio)
                .sortinoRatio(acc.sortinoRatio)
                .averageTradeDuration((long) avgDuration)
                .averageTradeSize(0.0)
                .totalTrades(acc.totalTrades)
                .winningTrades(acc.winningTrades)
                .losingTrades(acc.losingTrades)
                .signalAccuracy(signalAccuracy)
                .confidenceAccuracy(confidenceAcc)
                .aiApprovalAccuracy(aiApprovalAcc)
                .regimePerformance(new HashMap<>())
                .volatilitySensitivity(0.0)
                .lastUpdated(Instant.now())
                .build();
    }

    private StrategyPerformanceMetrics buildZeroMetrics(String assignmentId) {
        return StrategyPerformanceMetrics.builder()
                .assignmentId(assignmentId)
                .symbol("").timeframe("")
                .regimePerformance(new HashMap<>())
                .lastUpdated(Instant.now())
                .build();
    }

    // =========================================================================
    // Inner accumulator (mutable, access synchronized externally)
    // =========================================================================

    private static final class MetricsAccumulator {
        int totalTrades;
        int winningTrades;
        int losingTrades;
        double grossProfit;
        double grossLoss;
        double netProfit;
        double maxDrawdown;
        double equity = 10_000.0;  // baseline equity
        double peakEquity = 10_000.0;
        double totalConfidenceOnWins;
        double totalConfidenceOnLosses;
        int aiApprovedTotal;
        int aiApprovedWins;
        long totalDurationMinutes;
        double sharpeRatio;
        double sortinoRatio;
        final List<Double> pnlHistory = new ArrayList<>();
    }
}
