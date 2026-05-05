package org.investpro.research;

import lombok.Builder;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * Risk-adjusted score for a strategy based on backtest results.
 * Used to rank and compare strategies fairly.
 */
@Getter
@Builder
public class StrategyScore {
    private final String strategyId;

    @Builder.Default
    private final double totalScore = 0.0; // 0-100

    @Builder.Default
    private final double profitabilityScore = 0.0; // 0-100

    @Builder.Default
    private final double riskScore = 0.0; // 0-100, higher is better (lower risk)

    @Builder.Default
    private final double consistencyScore = 0.0; // 0-100

    @Builder.Default
    private final double executionScore = 0.0; // 0-100

    @Builder.Default
    private final double stabilityScore = 0.0; // 0-100 (out-of-sample performance)

    @Builder.Default
    private final double overfittingPenalty = 0.0; // -0 to -50

    @Builder.Default
    private final List<String> reasons = new ArrayList<>();

    @Builder.Default
    private final List<String> warnings = new ArrayList<>();

    /**
     * Calculates total score from component scores.
     * This is a utility method; scores are stored as-is in the builder.
     */
    public static double calculateTotalScore(double profitability, double risk, double consistency, double execution, double stability, double overfittingPenalty) {
        double calculated = (profitability * 0.35 +
                risk * 0.25 +
                consistency * 0.20 +
                execution * 0.10 +
                stability * 0.10) +
                overfittingPenalty;
        return Math.max(0, Math.min(100, calculated));
    }

    public boolean isHighQuality() {
        return totalScore >= 70 && stabilityScore >= 65 && overfittingPenalty > -20;
    }

    public boolean isAcceptable() {
        return totalScore >= 55 && riskScore >= 50;
    }

    public boolean hasRedFlags() {
        return totalScore < 50 || stabilityScore < 40 || riskScore < 30;
    }

    @Override
    public String toString() {
        return String.format("StrategyScore{id='%s', total=%.1f, profit=%.1f, risk=%.1f, consistency=%.1f, stability=%.1f, overfit=%.1f}",
                strategyId, totalScore, profitabilityScore, riskScore, consistencyScore, stabilityScore, overfittingPenalty);
    }
}
