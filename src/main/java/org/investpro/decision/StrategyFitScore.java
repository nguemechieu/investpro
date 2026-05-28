package org.investpro.decision;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Score for a single strategy's suitability to the current market.
 * Combines multiple scoring factors into a final fitness score (0.0 - 1.0).
 * 
 * Score >= 0.70 is considered "good fit" for trading.
 */
public record StrategyFitScore(
        @NotNull String strategyName,
        @NotNull String strategyClass,
        double regimeFitScore, // 0.0-1.0: how well strategy fits current market regime
        double assetFitScore, // 0.0-1.0: how well strategy fits asset type
        double timeframeFitScore, // 0.0-1.0: how well strategy fits current timeframe
        double recentPerformanceScore, // 0.0-1.0: recent backtest/paper performance
        double riskCompatibilityScore, // 0.0-1.0: how compatible with current risk settings
        double finalFitnessScore, // 0.0-1.0: weighted combination of all factors
        @NotNull String reasoning, // explanation of score
        @Nullable String warningMessage, // null if no warnings
        @NotNull Instant scoredAt) {

    public boolean isGoodFit() {
        return finalFitnessScore >= 0.70;
    }

    public boolean isExcellentFit() {
        return finalFitnessScore >= 0.85;
    }

    public boolean isPoorFit() {
        return finalFitnessScore < 0.50;
    }

    /**
     * Creates a neutral/unknown strategy fit score with only the final fitness score set.
     * Used by {@link BotTradeDecisionAssembler} when bridging from the institutional pipeline.
     */
    public static StrategyFitScore of(double finalFitnessScore) {
        return new StrategyFitScore(
                "unknown", "unknown",
                finalFitnessScore, finalFitnessScore, finalFitnessScore,
                finalFitnessScore, finalFitnessScore, finalFitnessScore,
                "Derived from pipeline score", null, Instant.now());
    }
}
