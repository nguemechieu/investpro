package org.investpro.strategy.position;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;

/**
 * Result of a position size calculation.
 * Produced by {@link PositionSizingEngine}.
 * The RiskEngine must review and approve {@link #finalSize} before execution.
 */
@Getter
@Builder
@ToString
public class PositionSizeResult {

    /** Identifier matching the originating {@link PositionSizeRequest#getRequestId()}. */
    private final String requestId;

    /** Trading symbol. */
    private final String symbol;

    /** Strategy identifier. */
    private final String strategyId;

    /** Sizing method that was applied. */
    private final PositionSizingMethod method;

    /** Raw recommended size in base currency units (before RiskEngine review). */
    private final double recommendedSize;

    /** Raw recommended size as a percentage of account equity. */
    private final double recommendedSizePercent;

    /** Maximum allowable risk amount in account currency. */
    private final double maxRiskAmount;

    /**
     * Final size after RiskEngine review.
     * Initially equals {@link #recommendedSize}; the RiskEngine may reduce it.
     */
    private final double finalSize;

    /** True if the RiskEngine adjusted the size from the recommended value. */
    private final boolean riskEngineAdjusted;

    /** Human-readable reason for any RiskEngine adjustment. */
    private final String adjustmentReason;

    /** Timestamp when this result was computed. */
    private final Instant calculatedAt;
}
