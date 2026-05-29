package org.investpro.strategy.position;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Result of a position size calculation produced by {@link PositionSizingEngine}.
 *
 * <p>The RiskEngine must review and potentially reduce {@link #positionUnits}
 * before any order is submitted to the execution layer.</p>
 *
 * <p><strong>CRITICAL:</strong> This result is advisory. AI engines contributed
 * the {@link #aiMultiplierApplied} multiplier but NEVER directly submit orders.</p>
 */
@Getter
@Builder
@ToString
public class PositionSizeResult {

    /** Identifier matching the originating {@link PositionSizeRequest#getRequestId()}. */
    private final String requestId;

    /** Assignment identifier from the lifecycle record. */
    private final String assignmentId;

    /** Trading symbol. */
    private final String symbol;

    /** Strategy identifier. */
    private final String strategyId;

    /** Sizing method that was applied. */
    private final PositionSizingMethod method;

    /** Final position size in base currency units (after AI multiplier and max-cap enforcement). */
    private final double positionUnits;

    /** Notional value = positionUnits x entryPrice. */
    private final double notionalValue;

    /** Dollar risk amount = positionUnits x stopLossDistance x pipValue. */
    private final double riskAmount;

    /** Risk as a fraction of equity = riskAmount / equity. */
    private final double riskPercent;

    /** Raw units before the AI multiplier was applied. */
    private final double rawUnits;

    /** The AI multiplier that was applied (from aiSizeMultiplier in the request). */
    private final double aiMultiplierApplied;

    /** True if the calculated size was capped at the maximum allowed position size. */
    private final boolean cappedByMax;

    /** Maximum allowed units at the time of calculation. */
    private final double maxAllowedUnits;

    /**
     * True if this result is safe to proceed through the pipeline.
     * False if the computed size was zero, negative, or exceeded risk limits.
     */
    private final boolean valid;

    /** Human-readable reason explaining validation status. */
    private final String validationReason;

    /** Timestamp when this result was computed. */
    @Builder.Default
    private final Instant calculatedAt = Instant.now();

    /** Convenience alias for {@link #positionUnits}. */
    public double getFinalSize() {
        return positionUnits;
    }

    /** Returns true if this result is valid and the size can proceed to execution planning. */
    public boolean isValid() {
        return valid;
    }

    /** Returns true if the position size was capped by the maximum size constraint. */
    public boolean isCappedByMax() {
        return cappedByMax;
    }
}
