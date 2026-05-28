package org.investpro.decision;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Score for an indicator composite setup's suitability.
 * Used when no single strategy reaches the minimum fitness threshold.
 * 
 * Score >= 0.70 is considered "good fit" for trading.
 */
public record IndicatorSetupScore(
        @NotNull IndicatorSetupType setupType,
        double regimeFitScore, // 0.0-1.0: how well indicators suit current regime
        double signalClarity, // 0.0-1.0: how clear and unambiguous the signals are
        double historicalWinRate, // 0.0-1.0: historical win rate in backtests
        double volatilityAlignment, // 0.0-1.0: how well suited to current volatility
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
     * Creates a neutral indicator score with only the final fitness score set.
     * Used by {@link BotTradeDecisionAssembler} when bridging from the institutional pipeline.
     */
    public static IndicatorSetupScore of(double finalFitnessScore) {
        return new IndicatorSetupScore(
                IndicatorSetupType.MOMENTUM,
                finalFitnessScore, finalFitnessScore, finalFitnessScore,
                finalFitnessScore, finalFitnessScore,
                "Derived from pipeline score", null, Instant.now());
    }
}
