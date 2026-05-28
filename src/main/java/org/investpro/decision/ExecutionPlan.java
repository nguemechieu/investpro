package org.investpro.decision;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;

/**
 * Complete execution plan for a trade decision.
 *
 * <p>Separates <em>how</em> a trade is executed from <em>why</em> it was decided.
 * {@link ExecutionPlan} contains all parameters required to submit a live order:
 * price levels, size, venue, timing, and risk constraints.</p>
 *
 * <p>Price and monetary fields use {@link BigDecimal} for accounting accuracy.
 * Non-monetary parameters (leverage, slippage, priority) use primitives.</p>
 */
public record ExecutionPlan(

        /** Entry price for the order. Use BigDecimal for exact accounting. */
        @NotNull BigDecimal entryPrice,

        /** Absolute stop-loss price level. */
        @NotNull BigDecimal stopLoss,

        /** Absolute take-profit price level. */
        @NotNull BigDecimal takeProfit,

        /** Number of units/contracts to trade. */
        @NotNull BigDecimal positionSize,

        /** Position leverage multiplier (1.0 = no leverage). */
        double leverage,

        /** Maximum acceptable slippage as a fraction of entry price (e.g., 0.001 = 0.1%). */
        double maxSlippageFraction,

        /** Target execution venue. */
        @NotNull ExecutionVenueType executionVenue,

        /** Time-in-force instruction for the order. */
        @NotNull TimeInForce timeInForce,

        /** Execution priority (higher = more aggressive, may accept wider spread). */
        @NotNull ExecutionPriority priority,

        /** Order type to submit. */
        @NotNull OrderType orderType,

        /** Risk as a percentage of account equity for this trade (0.0–100.0). */
        double riskPercent,

        /** Risk amount in account currency. BigDecimal for accounting precision. */
        @NotNull BigDecimal riskAmount,

        /** Expected reward amount if take-profit is hit. */
        @NotNull BigDecimal rewardAmount,

        /** Risk/reward ratio (reward / risk). */
        double riskRewardRatio

) {

    /** Time-in-force options for submitted orders. */
    public enum TimeInForce {
        /** Good till cancelled. */
        GTC,
        /** Immediate or cancel. */
        IOC,
        /** Fill or kill. */
        FOK,
        /** Day order (expires end of session). */
        DAY,
        /** Good till time — expires at specific timestamp. */
        GTT
    }

    /** Execution priority options. */
    public enum ExecutionPriority {
        /** Accept best available price; may wait for limit fill. */
        PASSIVE,
        /** Balance between fill speed and price quality. */
        NORMAL,
        /** Prioritize fill speed over price; wider slippage accepted. */
        AGGRESSIVE,
        /** Market order — immediate fill at any available price. */
        IMMEDIATE
    }

    /** Order type options. */
    public enum OrderType {
        MARKET,
        LIMIT,
        STOP_MARKET,
        STOP_LIMIT,
        TRAILING_STOP
    }

    /** Returns true if all required price levels are positive and internally consistent. */
    public boolean isValid() {
        if (entryPrice == null || entryPrice.signum() <= 0) return false;
        if (stopLoss == null || stopLoss.signum() <= 0) return false;
        if (takeProfit == null || takeProfit.signum() <= 0) return false;
        if (positionSize == null || positionSize.signum() <= 0) return false;
        if (riskAmount == null || riskAmount.signum() < 0) return false;
        if (rewardAmount == null || rewardAmount.signum() < 0) return false;
        return riskRewardRatio >= 0.0;
    }

    /**
     * Returns a formatted summary for logging and audit trails.
     */
    public String toSummary() {
        return String.format(
                "ExecutionPlan[entry=%.4f stop=%.4f tp=%.4f size=%.4f lev=%.1f RR=%.2f venue=%s type=%s]",
                entryPrice, stopLoss, takeProfit, positionSize,
                leverage, riskRewardRatio, executionVenue.name(), orderType.name());
    }

    /**
     * Compact factory using {@link TradePlan} bridge for backward compatibility.
     *
     * @param plan      existing TradePlan to wrap
     * @param venue     target execution venue
     * @param riskPct   risk percentage of account
     */
    public static ExecutionPlan from(
            @NotNull TradePlan plan,
            @NotNull ExecutionVenueType venue,
            double riskPct) {

        return new ExecutionPlan(
                plan.entryPrice(), plan.stopLoss(), plan.takeProfit(), plan.positionSize(),
                1.0, 0.001,
                venue,
                TimeInForce.GTC, ExecutionPriority.NORMAL, OrderType.LIMIT,
                riskPct, plan.riskAmount(), plan.rewardAmount(), plan.riskRewardRatio());
    }

    /**
     * Returns a no-op plan used when a decision is skipped or rejected.
     * Prefer {@link #EMPTY} for type-safe null-object usage.
     */
    @Nullable
    public static ExecutionPlan none() {
        return null;
    }

    /**
     * Null-object constant representing an absent or inapplicable execution plan.
     * Use instead of {@code null} to avoid null-pointer chains in pipeline code.
     * {@link #isValid()} returns {@code false} for this constant.
     */
    public static final ExecutionPlan EMPTY = new ExecutionPlan(
            BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
            1.0, 0.0,
            ExecutionVenueType.SIMULATED,
            TimeInForce.GTC, ExecutionPriority.NORMAL, OrderType.MARKET,
            0.0, BigDecimal.ZERO, BigDecimal.ZERO, 0.0);
}
