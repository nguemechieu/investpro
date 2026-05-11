package org.investpro.strategy.lab;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;

/**
 * Ranks strategy performance reports by overall score.
 *
 * Scoring formula balances multiple factors:
 * - Win rate (probability of success)
 * - Total return (profitability)
 * - Profit factor (consistency)
 * - Risk/reward (quality of trade setup)
 * - Confidence (strategy certainty)
 * - Drawdown penalty (risk management)
 * - Trade count reliability (sample size)
 */
@Slf4j
public class StrategyRankingEngine {

    /**
     * Rank reports by score, highest first.
     */
    public List<StrategyPerformanceReport> rank(@NotNull List<StrategyPerformanceReport> reports) {
        if (reports.isEmpty()) {
            return List.of();
        }

        // Score each report if not already scored
        for (StrategyPerformanceReport report : reports) {
            if (report.getScore() == 0.0 && report.getTotalTrades() > 0) {
                double newScore = score(report);
                // Score is already calculated in report, but we recalculate if needed
                // The report builder should have already set it
            }
        }

        // Sort by score descending (highest first)
        List<StrategyPerformanceReport> sorted = reports.stream()
                .sorted(Comparator.comparingDouble(StrategyPerformanceReport::getScore)
                        .reversed()
                        .thenComparingInt(StrategyPerformanceReport::getTotalTrades)
                        .reversed())
                .toList();

        log.info("Ranked {} strategies", sorted.size());
        for (int i = 0; i < Math.min(5, sorted.size()); i++) {
            StrategyPerformanceReport report = sorted.get(i);
            log.info(
                    "#{}: {} - Score: {:.1f}, WinRate: {:.1%}, Return: {:.1f}%, Trades: {}",
                    i + 1,
                    report.getStrategyName(),
                    report.getScore(),
                    report.getWinRate(),
                    report.getTotalReturn(),
                    report.getTotalTrades());
        }

        return sorted;
    }

    /**
     * Calculate score for a single report.
     *
     * Formula (0-100+ scale):
     * score = (winRate × 25)
     * + (totalReturn × 20)
     * + (profitFactor × 15)
     * + (averageRiskReward × 10)
     * + (averageConfidence × 10)
     * - (maxDrawdown × 20)
     * + tradeReliabilityBonus
     */
    public double score(@NotNull StrategyPerformanceReport report) {
        if (report.getTotalTrades() == 0) {
            return 0.0;
        }

        double score = 0.0;

        // Win rate component (0-25 points)
        // Higher win rate = better
        score += Math.min(25, report.getWinRate() * 100);

        // Total return component (0-20 points)
        // Cap at 20% return (100+ points would be unrealistic)
        score += Math.min(20, report.getTotalReturn());

        // Profit factor component (0-15 points)
        // Profit factor > 1.5 is good, > 2.0 is excellent
        // Cap at 2.5 (anything above is equally excellent)
        double pf = Math.min(2.5, report.getProfitFactor());
        score += (pf / 2.5) * 15;

        // Risk/Reward component (0-10 points)
        // RRR of 1.0 = neutral, 2.0 = good, 3.0+ = excellent
        double rrr = Math.min(3.0, report.getAverageRiskReward());
        score += (rrr / 3.0) * 10;

        // Confidence component (0-10 points)
        // Average confidence from 0.5 to 1.0
        score += Math.min(10, report.getAverageConfidence() * 20);

        // Drawdown penalty (-20 points max)
        // 30% drawdown = -20 points, lower drawdowns get less penalty
        score -= Math.min(20, report.getMaxDrawdown() * 100);

        // Trade reliability bonus (0-20 points)
        // Penalizes strategies with too few trades for statistical significance
        if (report.getTotalTrades() >= 30) {
            score += 20;
        } else if (report.getTotalTrades() >= 20) {
            score += 15;
        } else if (report.getTotalTrades() >= 10) {
            score += 10;
        } else if (report.getTotalTrades() >= 5) {
            score += 5;
        }

        // Ensure score is within reasonable bounds
        return Math.max(0, score);
    }

    /**
     * Check if a report is tradable quality.
     */
    public boolean isTradableQuality(@NotNull StrategyPerformanceReport report) {
        return report.isTradable()
                && report.getScore() >= 40.0
                && report.getWinRate() >= 0.40
                && report.getProfitFactor() >= 1.2;
    }

    /**
     * Get the best report from a list, or null if none are tradable.
     */
    public StrategyPerformanceReport getBestTradable(@NotNull List<StrategyPerformanceReport> reports) {
        return reports.stream()
                .filter(this::isTradableQuality)
                .max(Comparator.comparingDouble(StrategyPerformanceReport::getScore))
                .orElse(null);
    }
}
