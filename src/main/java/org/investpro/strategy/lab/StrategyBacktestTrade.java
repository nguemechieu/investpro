package org.investpro.strategy.lab;

import lombok.Builder;
import lombok.Getter;
import org.investpro.utils.Side;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Represents a single trade executed during a backtest.
 *
 * Records entry and exit prices, P&L, confidence, and reasons for both entry
 * and exit.
 */
@Getter
@Builder(toBuilder = true)
public class StrategyBacktestTrade {

    /**
     * Strategy name that generated this trade.
     */
    @NotNull
    private final String strategyName;

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
     * Trade side: BUY or SELL.
     */
    @NotNull
    private final Side side;

    /**
     * Entry price.
     */
    private final double entryPrice;

    /**
     * Exit price (set when trade is closed).
     */
    private final double exitPrice;

    /**
     * Quantity traded.
     */
    private final double quantity;

    /**
     * Profit/loss in absolute units (set when trade is closed).
     */
    private final double profitLoss;

    /**
     * Profit/loss as percentage (set when trade is closed).
     */
    private final double profitLossPercent;

    /**
     * Signal confidence when trade was entered (0.0 to 1.0).
     */
    @Builder.Default
    private final double confidence = 0.5;

    /**
     * Reason for entry (from signal reason).
     */
    @Nullable
    private final String entryReason;

    /**
     * Reason for exit (hit stop, hit target, timeout, etc.).
     */
    @Nullable
    private final String exitReason;

    /**
     * When this trade was entered.
     */
    @NotNull
    private final Instant entryTime;

    /**
     * When this trade was exited (null until trade is closed).
     */
    @Nullable
    private final Instant exitTime;

    /**
     * Number of candles held (set when trade is closed).
     */
    private final int barsHeld;

    /**
     * True if trade was profitable.
     */
    public boolean isWin() {
        return profitLoss > 0;
    }

    /**
     * True if trade was a loss.
     */
    public boolean isLoss() {
        return profitLoss < 0;
    }

    /**
     * True if trade broke even.
     */
    public boolean isBreakEven() {
        return profitLoss == 0;
    }

    /**
     * Risk/reward ratio for this trade.
     * Positive if profitable, negative if loss.
     */
    public double getRiskRewardRatio() {
        if (entryPrice == 0)
            return 0;
        double riskPercentage = Math.abs(profitLossPercent);
        return isWin() ? riskPercentage : -riskPercentage;
    }
}
