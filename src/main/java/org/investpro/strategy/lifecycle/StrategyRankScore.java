package org.investpro.strategy.lifecycle;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Multi-factor ranking score for a strategy on a specific symbol/timeframe.
 * Computed by {@link org.investpro.strategy.management.StrategyLifecycleRankingEngine}.
 */
@Getter
@Builder
@ToString
public class StrategyRankScore {

    /** Strategy identifier. */
    private final String strategyId;

    /** Trading symbol. */
    private final String symbol;

    /** Timeframe. */
    private final String timeframe;

    /** Backtest performance score (0-100). */
    private final double backtestScore;

    /** Paper trading performance score (0-100). */
    private final double paperTradingScore;

    /** AI review quality score (0-100). */
    private final double aiReviewScore;

    /** Regime compatibility score (0-100). */
    private final double regimeFitScore;

    /** Risk management quality score (0-100, higher = lower risk). */
    private final double riskScore;

    /** Performance consistency score (0-100). */
    private final double consistencyScore;

    /** Strategy stability score (0-100). */
    private final double stabilityScore;

    /** Weighted composite score (0-100). */
    private final double compositeScore;

    /** Rank among all evaluated strategies (1 = best). */
    private final int rank;

    /** Timestamp when this score was computed. */
    private final Instant computedAt;

    /**
     * Computes the weighted composite score from component scores.
     * Weights: backtest 20%, paperTrading 25%, aiReview 20%,
     * regimeFit 10%, risk 10%, consistency 10%, stability 5%.
     * (Totals 100%.)
     *
     * @return composite score in range 0-100
     */
    public double computeComposite() {
        return backtestScore * 0.20
                + paperTradingScore * 0.25
                + aiReviewScore * 0.20
                + regimeFitScore * 0.10
                + riskScore * 0.10
                + consistencyScore * 0.10
                + stabilityScore * 0.05;
    }
}
