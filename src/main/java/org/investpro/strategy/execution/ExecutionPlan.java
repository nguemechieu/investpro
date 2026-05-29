package org.investpro.strategy.execution;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.investpro.utils.Side;

import java.time.Instant;
import java.util.UUID;

/**
 * A fully constructed execution plan ready for routing to an exchange venue.
 * Created by {@link ExecutionPlanningEngine} and routed by {@link ExecutionRouter}.
 *
 * <p><strong>CRITICAL:</strong> AI may contribute advisory notes via
 * {@link #aiGuidanceNotes} but does NOT set or override mandatory fields
 * such as entry price, stop loss, take profit, or position size.
 * The ExecutionEngine makes all final trading decisions.</p>
 */
@Getter
@Builder
@ToString
public class ExecutionPlan {

    /** Unique plan identifier (UUID). */
    @Builder.Default
    private final String planId = UUID.randomUUID().toString();

    /** Trading symbol. */
    private final String symbol;

    /** Timeframe. */
    private final String timeframe;

    /** Strategy identifier that generated the originating signal. */
    private final String strategyId;

    /** Target execution venue. */
    private final ExecutionVenue venue;

    /** Trade direction (BUY / SELL). */
    private final Side side;

    /** Intended entry price. */
    private final double entryPrice;

    /** Stop-loss price. */
    private final double stopLossPrice;

    /** Take-profit price. */
    private final double takeProfitPrice;

    /** Position size in base currency units. */
    private final double positionSize;

    /** Leverage multiplier (1.0 = no leverage). */
    @Builder.Default
    private final double leverage = 1.0;

    /** Maximum acceptable slippage as a fraction (e.g. 0.001 = 0.1%). */
    private final double slippageTolerance;

    /** Order type (e.g. MARKET, LIMIT, STOP_LIMIT). */
    private final String orderType;

    /** Time in force instruction (e.g. GTC, IOC, FOK). */
    private final String timeInForce;

    /**
     * Optional advisory notes from AI review.
     * These are informational only and do NOT override any execution parameters.
     */
    private final String aiGuidanceNotes;

    /** True if the RiskEngine has approved this plan for execution. */
    private final boolean riskApproved;

    /** Timestamp when this plan was created. */
    private final Instant planCreatedAt;

    /** Timestamp after which this plan should not be executed. */
    private final Instant planValidUntil;

    /**
     * @return true if this plan has passed its validity window and must not be executed.
     */
    public boolean isExpired() {
        return planValidUntil != null && Instant.now().isAfter(planValidUntil);
    }
}
