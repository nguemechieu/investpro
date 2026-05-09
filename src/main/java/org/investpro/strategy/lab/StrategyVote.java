package org.investpro.strategy.lab;

import lombok.Builder;
import lombok.Getter;
import org.investpro.strategy.StrategySignal;
import org.investpro.utils.Side;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A single vote from a strategy in the voting/consensus process.
 *
 * Represents what signal a strategy generated and how much weight it has.
 */
@Getter
@Builder(toBuilder = true)
public class StrategyVote {

    /**
     * Strategy name that generated this vote.
     */
    @NotNull
    private final String strategyName;

    /**
     * Base strategy name.
     */
    @NotNull
    private final String baseStrategyName;

    /**
     * Symbol being voted on.
     */
    @NotNull
    private final String symbol;

    /**
     * Timeframe being voted on.
     */
    @NotNull
    private final Timeframe timeframe;

    /**
     * Vote side: BUY, SELL, or HOLD.
     */
    @NotNull
    private final Side side;

    /**
     * Signal confidence (0.0 to 1.0).
     */
    private final double confidence;

    /**
     * Strategy performance score.
     */
    private final double score;

    /**
     * Reason for this vote.
     */
    @Nullable
    private final String reason;

    /**
     * The strategy signal that generated this vote.
     */
    @Nullable
    private final StrategySignal signal;

    /**
     * Performance report for this strategy.
     */
    @Nullable
    private final StrategyPerformanceReport performanceReport;

    /**
     * Weight of this vote in consensus calculation.
     * Typically: score * confidence.
     */
    public double getWeight() {
        return score * confidence;
    }

    /**
     * True if this vote is for a BUY action.
     */
    public boolean isBuy() {
        return side == Side.BUY;
    }

    /**
     * True if this vote is for a SELL action.
     */
    public boolean isSell() {
        return side == Side.SELL;
    }

    /**
     * True if this vote is for HOLD (no action).
     */
    public boolean isHold() {
        return side == Side.HOLD;
    }
}
