package org.investpro.strategy.lab;

import lombok.Builder;
import lombok.Getter;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Performance report for a strategy backtest.
 *
 * Aggregates results from running one strategy on one symbol/timeframe,
 * including win rate, profit factor, drawdown, Sharpe approximation, and score.
 */
@Getter
@Builder(toBuilder = true)
public class StrategyPerformanceReport {

    /**
     * Full strategy name (possibly catalog variant).
     */
    @NotNull
    private final String strategyName;

    /**
     * Base strategy name (from catalog or simple strategy ID).
     */
    @NotNull
    private final String baseStrategyName;

    /**
     * Trading symbol.
     */
    @NotNull
    private final String symbol;

    /**
     * Trading timeframe.
     */
    @NotNull
    private final Timeframe timeframe;

    /**
     * Total trades executed.
     */
    @Builder.Default
    private final int totalTrades = 0;

    /**
     * Trades that were profitable.
     */
    @Builder.Default
    private final int winningTrades = 0;

    /**
     * Trades that were losers.
     */
    @Builder.Default
    private final int losingTrades = 0;

    /**
     * Win rate as percentage (0.0 to 1.0).
     * Calculated as winningTrades / totalTrades.
     */
    @Builder.Default
    private final double winRate = 0.0;

    /**
     * Total return percentage (profit as % of initial capital).
     */
    @Builder.Default
    private final double totalReturn = 0.0;

    /**
     * Net profit in absolute units.
     */
    @Builder.Default
    private final double netProfit = 0.0;

    /**
     * Maximum drawdown during backtest (peak to trough).
     */
    @Builder.Default
    private final double maxDrawdown = 0.0;

    /**
     * Profit factor: (sum of wins) / (sum of losses).
     * > 1.5 is good, > 2.0 is excellent.
     */
    @Builder.Default
    private final double profitFactor = 0.0;

    /**
     * Average profit per winning trade.
     */
    @Builder.Default
    private final double averageWin = 0.0;

    /**
     * Average loss per losing trade.
     */
    @Builder.Default
    private final double averageLoss = 0.0;

    /**
     * Average risk/reward ratio across all trades.
     */
    @Builder.Default
    private final double averageRiskReward = 1.0;

    /**
     * Average confidence of signals.
     */
    @Builder.Default
    private final double averageConfidence = 0.5;

    /**
     * Approximate Sharpe ratio (return / volatility).
     * Rough approximation, not precise.
     */
    @Builder.Default
    private final double sharpeApproximation = 0.0;

    /**
     * Overall performance score (0-100+).
     * Used for ranking strategies.
     */
    @Builder.Default
    private final double score = 0.0;

    /**
     * Individual trades executed.
     */
    @Builder.Default
    private final List<StrategyBacktestTrade> trades = new ArrayList<>();

    /**
     * Warnings captured during backtest.
     */
    @Builder.Default
    private final List<String> warnings = new ArrayList<>();

    /**
     * When this report was generated.
     */
    @Builder.Default
    private final Instant generatedAt = Instant.now();

    /**
     * True if there are enough trades for meaningful statistics.
     * Minimum: 5 trades.
     */
    public boolean hasEnoughTrades() {
        return totalTrades >= 5;
    }

    /**
     * True if the strategy was profitable.
     */
    public boolean isProfitable() {
        return netProfit > 0 && totalReturn > 0;
    }

    /**
     * True if the strategy is suitable for trading.
     * Requires: enough trades, positive return, acceptable drawdown.
     */
    public boolean isTradable() {
        return hasEnoughTrades()
                && isProfitable()
                && maxDrawdown < 0.30 // Less than 30% drawdown
                && profitFactor > 1.2 // Decent profit factor
                && score > 40.0; // Minimum score threshold
    }

    /**
     * Risk/reward quality.
     */
    public String getRiskRewardQuality() {
        if (averageRiskReward < 0.8)
            return "Poor";
        if (averageRiskReward < 1.2)
            return "Fair";
        if (averageRiskReward < 1.8)
            return "Good";
        return "Excellent";
    }

    /**
     * Consistency assessment.
     */
    public String getConsistency() {
        if (winRate < 0.40)
            return "Low";
        if (winRate < 0.55)
            return "Moderate";
        if (winRate < 0.65)
            return "High";
        return "Very High";
    }
}
