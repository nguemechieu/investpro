package org.investpro.strategy.execution;

import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A fully constructed execution plan ready for routing to an exchange venue.
 * Created by {@link ExecutionPlanningEngine} and routed by {@link ExecutionRouter}.
 *
 * <p><strong>CRITICAL:</strong> AI may contribute advisory notes via
 * {@link #aiReasoningSummary} and an approval flag via {@link #aiApproved},
 * but does NOT set or override mandatory execution parameters
 * (entry, stop-loss, take-profit, or position size).
 * The ExecutionEngine makes all final trading decisions.</p>
 */
@Getter
@Builder
@ToString
public class ExecutionPlan {

    /** Unique plan identifier (UUID). */
    @Builder.Default
    private final String planId = UUID.randomUUID().toString();

    /** Assignment identifier from the lifecycle record. */
    private final String assignmentId;

    /** Trading symbol. */
    private final String symbol;

    /** Timeframe code (e.g. "1h"). */
    private final String timeframe;

    /** Strategy identifier that generated the originating signal. */
    private final String strategyId;

    /** Target execution venue. */
    private final ExecutionVenue venue;

    /** Trade direction: BUY, SELL, or HOLD. */
    private final String side;

    /** Intended entry price. */
    private final double entryPrice;

    /** Stop-loss price. */
    private final double stopLoss;

    /** Take-profit price. */
    private final double takeProfit;

    /** Position size in base currency units. */
    private final double units;

    /** Notional value = units x entryPrice. */
    private final double notionalValue;

    /** Dollar risk amount. */
    private final double riskAmount;

    /** Risk as a fraction of equity. */
    private final double riskPercent;

    /** Risk-to-reward ratio = |takeProfit - entry| / |entry - stopLoss|. */
    private final double riskRewardRatio;

    /** Leverage multiplier (1.0 = no leverage). */
    @Builder.Default
    private final double leverage = 1.0;

    /** Maximum acceptable slippage as a fraction (e.g. 0.001 = 0.1%). */
    @Builder.Default
    private final double slippageTolerance = 0.001;

    /** Order type (e.g. MARKET, LIMIT). */
    private final String orderType;

    /** Time in force instruction (e.g. GTC, IOC, FOK). */
    @Builder.Default
    private final String timeInForce = "GTC";

    /**
     * Whether the AI signal review approved this plan.
     * Advisory only - the RiskEngine has final authority.
     */
    private final boolean aiApproved;

    /** AI confidence from the signal review (0.0-1.0). */
    private final double aiConfidence;

    /**
     * Human-readable summary from the AI signal review.
     * Informational only; does NOT override any execution parameter.
     */
    private final String aiReasoningSummary;

    /**
     * Validation notes collected during execution plan creation.
     * Each entry is a human-readable note.
     */
    @Singular("validationNote")
    private final List<String> validationNotes;

    /**
     * True if this plan has passed all internal validation checks and is
     * safe to route to the execution engine (subject to RiskEngine approval).
     */
    private final boolean isValid;

    /** True if the RiskEngine has formally approved this plan for execution. */
    private final boolean riskApproved;

    /** Timestamp when this plan was created. */
    @Builder.Default
    private final Instant createdAt = Instant.now();

    /** Timestamp after which this plan should NOT be executed. */
    private final Instant planValidUntil;

    /** @return true if this plan has passed its validity window and must not be executed. */
    public boolean isExpired() {
        return planValidUntil != null && Instant.now().isAfter(planValidUntil);
    }

    /** @return true if this plan is internally valid and passed all checks. */
    public boolean isValid() {
        return isValid;
    }
}
