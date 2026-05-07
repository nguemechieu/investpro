package org.investpro.research;

import lombok.Builder;
import lombok.Getter;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Note: StrategyScore is in this same package, so no import needed but declared below

/**
 * Results and metrics from backtesting a strategy on a specific
 * symbol/timeframe.
 */
@Getter
@Builder
public class StrategyBacktestResult {
    private final String strategyId;
    private final String symbol;
    private final Timeframe timeframe;

    @Builder.Default
    private final int totalTrades = 0;

    @Builder.Default
    private final int winningTrades = 0;

    @Builder.Default
    private final int losingTrades = 0;

    @Builder.Default
    private final double winRate = 0.0; // 0-1

    @Builder.Default
    private final double profitFactor = 0.0; // totalWins / totalLosses

    @Builder.Default
    private final double expectancy = 0.0; // (winRate * avgWin) - ((1-winRate) * avgLoss)

    @Builder.Default
    private final double averageWin = 0.0;

    @Builder.Default
    private final double averageLoss = 0.0;

    @Builder.Default
    private final double maxDrawdown = 0.0; // in currency/points

    @Builder.Default
    private final double maxDrawdownPercent = 0.0; // percentage

    @Builder.Default
    private final double totalReturnPercent = 0.0;

    @Builder.Default
    private final double sharpeRatio = 0.0;

    @Builder.Default
    private final double sortinoRatio = 0.0;

    @Builder.Default
    private final double calmarRatio = 0.0;

    @Builder.Default
    private final double averageHoldingTime = 0.0; // in bars/candles

    @Builder.Default
    private final double largestWin = 0.0;

    @Builder.Default
    private final double largestLoss = 0.0;

    @Builder.Default
    private final int consecutiveWins = 0;

    @Builder.Default
    private final int consecutiveLosses = 0;

    @Builder.Default
    private final double feesPaid = 0.0;

    @Builder.Default
    private final double slippageCost = 0.0;

    @Builder.Default
    private final double exposureTime = 0.0; // % of time invested

    @Builder.Default
    private final Instant createdAt = Instant.now();

    @Nullable
    private final StrategyScore score; // Calculated score

    @Builder.Default
    private final List<String> warnings = new ArrayList<>();

    @Builder.Default
    private final boolean approvedForLiveTrading = false;

    public boolean isProfitable() {
        return totalReturnPercent > 0 && profitFactor > 1.0;
    }

    public boolean hasGoodWinRate() {
        return winRate >= 0.55; // > 55%
    }

    public boolean hasAcceptableDrawdown() {
        return maxDrawdownPercent < 30; // Less than 30%
    }

    public boolean hasEnoughTrades() {
        return totalTrades >= 20; // At least 20 trades for statistical significance
    }

    public boolean meetsQualityStandards() {
        return isProfitable() &&
                hasGoodWinRate() &&
                hasAcceptableDrawdown() &&
                hasEnoughTrades() &&
                expectancy > 0;
    }

    @Override
    public String toString() {
        return String.format("BacktestResult{%s/%s: trades=%d, wr=%.1f%%, pf=%.2f, ret=%.2f%%, dd=%.2f%%, exp=%.2f}",
                symbol, timeframe.getCode(), totalTrades, winRate * 100, profitFactor, totalReturnPercent,
                maxDrawdownPercent, expectancy);
    }
}
