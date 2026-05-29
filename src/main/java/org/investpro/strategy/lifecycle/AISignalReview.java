package org.investpro.strategy.lifecycle;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable AI review result for an individual trading signal.
 * Produced by {@link org.investpro.strategy.ai.AISignalReviewEngine}.
 *
 * <p><strong>CRITICAL:</strong> This review is advisory only. AI never places orders.
 * The ExecutionEngine makes all final trading decisions.</p>
 */
@Getter
@Builder
@ToString
public class AISignalReview {

    /** Unique review identifier (UUID). */
    @Builder.Default
    private final String reviewId = UUID.randomUUID().toString();

    /** Identifier of the signal being reviewed. */
    private final String signalId;

    /** Strategy identifier that generated the signal. */
    private final String strategyId;

    /** Trading symbol. */
    private final String symbol;

    /** Timeframe. */
    private final String timeframe;

    /** AI decision for this signal. */
    private final AISignalDecision decision;

    /** AI confidence in this review (0.0-1.0). */
    private final double aiConfidence;

    /** Human-readable summary of the AI reasoning. */
    private final String reasoningSummary;

    /** Overall market condition score (0.0-1.0, higher = better conditions). */
    private final double marketConditionScore;

    /** Volatility score (0.0-1.0, higher = more volatile). */
    private final double volatilityScore;

    /** Estimated liquidity score (0.0-1.0, higher = more liquid). */
    private final double liquidityScore;

    /** Spread tightness score (0.0-1.0, higher = tighter spread). */
    private final double spreadScore;

    /** News/event risk score (0.0-1.0, higher = riskier). */
    private final double newsRiskScore;

    /** How compatible the signal is with the current market regime (0.0-1.0). */
    private final double regimeCompatibility;

    /**
     * Suggested position size multiplier (0.1-1.0).
     * Only meaningful when decision == REDUCE_SIZE.
     * Default is 1.0 (no change).
     */
    @Builder.Default
    private final double suggestedSizeMultiplier = 1.0;

    /** Warning flags raised during AI review. */
    private final List<String> warningFlags;

    /** Timestamp when this review was generated. */
    private final Instant reviewedAt;
}
