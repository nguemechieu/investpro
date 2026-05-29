package org.investpro.strategy.management;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyRankScore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Computes multi-factor ranking scores for strategy assignments.
 * Rankings are used by {@link StrategyAssignmentManager} to select
 * replacement candidates and prioritise assignments.
 */
@Slf4j
public class StrategyLifecycleRankingEngine {

    private static volatile StrategyLifecycleRankingEngine instance;

    private StrategyLifecycleRankingEngine() {
        log.info("StrategyLifecycleRankingEngine initialised");
    }

    /**
     * Returns the singleton instance.
     *
     * @return singleton StrategyLifecycleRankingEngine
     */
    public static StrategyLifecycleRankingEngine getInstance() {
        StrategyLifecycleRankingEngine local = instance;
        if (local == null) {
            synchronized (StrategyLifecycleRankingEngine.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyLifecycleRankingEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /**
     * Ranks a list of strategy lifecycle records and returns sorted StrategyRankScores.
     *
     * @param records list of lifecycle records to rank
     * @return sorted list of StrategyRankScore (rank 1 = best)
     */
    public List<StrategyRankScore> rankStrategies(List<StrategyLifecycleRecord> records) {
        if (records == null || records.isEmpty()) return List.of();

        List<StrategyRankScore> scores = new ArrayList<>();
        for (StrategyLifecycleRecord record : records) {
            scores.add(scoreRecord(record));
        }

        // Sort descending by composite score, then assign rank
        scores.sort(Comparator.comparingDouble(StrategyRankScore::getCompositeScore).reversed());
        AtomicInteger rank = new AtomicInteger(1);
        List<StrategyRankScore> ranked = new ArrayList<>();
        for (StrategyRankScore score : scores) {
            ranked.add(StrategyRankScore.builder()
                    .strategyId(score.getStrategyId())
                    .symbol(score.getSymbol())
                    .timeframe(score.getTimeframe())
                    .backtestScore(score.getBacktestScore())
                    .paperTradingScore(score.getPaperTradingScore())
                    .aiReviewScore(score.getAiReviewScore())
                    .regimeFitScore(score.getRegimeFitScore())
                    .riskScore(score.getRiskScore())
                    .consistencyScore(score.getConsistencyScore())
                    .stabilityScore(score.getStabilityScore())
                    .compositeScore(score.getCompositeScore())
                    .rank(rank.getAndIncrement())
                    .computedAt(Instant.now())
                    .build());
        }

        log.debug("Ranked {} strategies", ranked.size());
        return ranked;
    }

    /**
     * Computes the multi-factor score for a single lifecycle record.
     *
     * @param record the lifecycle record to score
     * @return StrategyRankScore (rank field is 0, set by rankStrategies)
     */
    public StrategyRankScore scoreRecord(StrategyLifecycleRecord record) {
        if (record == null) return buildZeroScore(null, null, null);

        // --- Backtest score: from last AI review ---
        double backtestScore = 0.0;
        if (record.getLastAIReview() != null) {
            backtestScore = record.getLastAIReview().getAiConfidence() * 100.0;
            if (record.getLastAIReview().isApproved()) backtestScore = Math.max(backtestScore, 50.0);
        }

        // --- Paper trading score: from last validation report ---
        double paperTradingScore = 0.0;
        if (record.getLastValidationReport() != null) {
            double pf = record.getLastValidationReport().getPaperProfitFactor();
            double wr = record.getLastValidationReport().getPaperWinRate();
            paperTradingScore = (Math.min(wr, 1.0) * 50.0) + (Math.min(pf / 3.0, 1.0) * 50.0);
        } else {
            paperTradingScore = record.getValidationScore() * 100.0;
        }

        // --- AI review score: aiConfidence * 100 ---
        double aiReviewScore = record.getAiConfidence() * 100.0;

        // --- Regime fit: from learning profile ---
        double regimeFitScore = 0.0;
        if (record.getLearningProfile() != null) {
            int bestCount = record.getLearningProfile().getBestRegimes().size();
            regimeFitScore = Math.min(bestCount * 20.0, 100.0); // up to 5 regimes = 100
        }

        // --- Risk score: inverse of max drawdown ---
        double riskScore = 100.0;
        if (record.getLastHealthReport() != null) {
            double dd = record.getLastHealthReport().getMaxDrawdown();
            riskScore = Math.max((1.0 - (dd / 0.25)) * 100.0, 0.0);
        }

        // --- Consistency score: from profit factor ---
        double consistencyScore = 0.0;
        if (record.getLastHealthReport() != null) {
            double pf = record.getLastHealthReport().getProfitFactor();
            consistencyScore = Math.min(pf / 3.0, 1.0) * 100.0;
        }

        // --- Stability score: from health level ---
        double stabilityScore = 50.0;
        if (record.getLastHealthReport() != null) {
            stabilityScore = record.getLastHealthReport().getHealthLevel().priority * 20.0;
        }

        double composite = backtestScore * 0.20
                + paperTradingScore * 0.25
                + aiReviewScore * 0.20
                + regimeFitScore * 0.10
                + riskScore * 0.10
                + consistencyScore * 0.10
                + stabilityScore * 0.05;

        return StrategyRankScore.builder()
                .strategyId(record.getStrategyId())
                .symbol(record.getSymbol())
                .timeframe(record.getTimeframe())
                .backtestScore(backtestScore)
                .paperTradingScore(paperTradingScore)
                .aiReviewScore(aiReviewScore)
                .regimeFitScore(regimeFitScore)
                .riskScore(riskScore)
                .consistencyScore(consistencyScore)
                .stabilityScore(stabilityScore)
                .compositeScore(composite)
                .rank(0)
                .computedAt(Instant.now())
                .build();
    }

    private StrategyRankScore buildZeroScore(String strategyId, String symbol, String timeframe) {
        return StrategyRankScore.builder()
                .strategyId(strategyId != null ? strategyId : "UNKNOWN")
                .symbol(symbol != null ? symbol : "UNKNOWN")
                .timeframe(timeframe != null ? timeframe : "UNKNOWN")
                .compositeScore(0.0)
                .rank(Integer.MAX_VALUE)
                .computedAt(Instant.now())
                .build();
    }
}
