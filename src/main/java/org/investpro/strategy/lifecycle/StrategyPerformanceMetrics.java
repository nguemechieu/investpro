package org.investpro.strategy.lifecycle;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.Map;

/**
 * Comprehensive performance metrics for a strategy assignment.
 * Updated on each trade outcome by {@link org.investpro.strategy.performance.StrategyPerformanceTracker}.
 */
@Getter
@Builder
@ToString
public class StrategyPerformanceMetrics {

    /** Assignment identifier. */
    private final String assignmentId;

    /** Trading symbol (e.g. BTC/USD). */
    private final String symbol;

    /** Timeframe code (e.g. 1h, 4h). */
    private final String timeframe;

    /** Net profit / loss in account currency. */
    private final double netProfit;

    /** Win rate as a fraction (0.0 to 1.0). */
    private final double winRate;

    /** Profit factor = gross profit / gross loss. */
    private final double profitFactor;

    /** Maximum drawdown as a fraction (0.0 to 1.0). */
    private final double maxDrawdown;

    /** Expected return per trade in account currency. */
    private final double expectancy;

    /** Sharpe ratio (annualised). */
    private final double sharpeRatio;

    /** Sortino ratio (annualised, downside deviation only). */
    private final double sortinoRatio;

    /** Average trade duration in minutes. */
    private final long averageTradeDuration;

    /** Average position size in base currency units. */
    private final double averageTradeSize;

    /** Total number of completed trades. */
    private final int totalTrades;

    /** Number of winning trades. */
    private final int winningTrades;

    /** Number of losing trades. */
    private final int losingTrades;

    /** Fraction of signals that resulted in winning trades (0.0-1.0). */
    private final double signalAccuracy;

    /** Fraction of high-confidence signals that were correct (0.0-1.0). */
    private final double confidenceAccuracy;

    /** Fraction of AI-approved signals that were correct (0.0-1.0). */
    private final double aiApprovalAccuracy;

    /**
     * Performance broken down by market regime name.
     * Key = MarketRegime.name(), Value = win rate for that regime.
     */
    private final Map<String, Double> regimePerformance;

    /**
     * Volatility sensitivity coefficient (-1.0 to 1.0).
     * Negative = performs worse in high-volatility environments.
     */
    private final double volatilitySensitivity;

    /** Timestamp of the last metrics update. */
    private final Instant lastUpdated;

    /**
     * Evaluates whether this strategy meets minimum acceptable thresholds.
     *
     * @param minWinRate      minimum acceptable win rate (fraction)
     * @param minProfitFactor minimum acceptable profit factor
     * @param maxDrawdown     maximum acceptable drawdown (fraction)
     * @return true if all thresholds are met
     */
    public boolean isAcceptable(double minWinRate, double minProfitFactor, double maxDrawdown) {
        return winRate >= minWinRate
                && profitFactor >= minProfitFactor
                && this.maxDrawdown <= maxDrawdown
                && totalTrades > 0;
    }

    /**
     * Computes a single 0-100 composite score for this strategy.
     * Weighted: win rate (30%), profit factor (25%), drawdown penalty (20%),
     * expectancy sign (15%), Sharpe (10%).
     *
     * @return composite score (0-100)
     */
    public double getOverallScore() {
        double wrScore = Math.min(winRate, 1.0) * 100.0 * 0.30;
        double pfScore = Math.min(profitFactor / 3.0, 1.0) * 100.0 * 0.25;
        double ddPenalty = (1.0 - Math.min(maxDrawdown / 0.25, 1.0)) * 100.0 * 0.20;
        double expScore = expectancy > 0 ? 100.0 * 0.15 : 0.0;
        double sharpeScore = Math.min(Math.max(sharpeRatio, 0.0) / 3.0, 1.0) * 100.0 * 0.10;
        return wrScore + pfScore + ddPenalty + expScore + sharpeScore;
    }
}
