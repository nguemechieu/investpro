package org.investpro.strategy.lifecycle;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.investpro.decision.MarketRegime;

import java.time.Instant;
import java.util.List;

/**
 * Extended lifecycle record that wraps and enriches a strategy assignment with
 * AI reviews, health reports, validation results, and learning profiles.
 * Managed by {@link org.investpro.strategy.management.StrategyAssignmentManager}.
 */
@Getter
@Builder
@ToString
public class StrategyLifecycleRecord {

    // =========================================================================
    // Identity
    // =========================================================================

    /** Unique assignment identifier. */
    private final String assignmentId;

    /** Trading symbol (e.g. BTC/USD). */
    private final String symbol;

    /** Timeframe (e.g. 1h). */
    private final String timeframe;

    /** Strategy identifier. */
    private final String strategyId;

    /** Human-readable strategy name. */
    private final String strategyName;

    // =========================================================================
    // Assignment Metadata
    // =========================================================================

    /** Composite score at the time of assignment. */
    private final double assignmentScore;

    /** Confidence score at time of assignment (0.0-1.0). */
    private final double confidence;

    /** Timestamp when this assignment was created. */
    @Builder.Default
    private final Instant assignedAt = Instant.now();

    /** User or system component that created this assignment. */
    private final String assignedBy;

    /** Human-readable reason for this assignment. */
    private final String assignmentReason;

    /** Market regime active at the time of assignment. */
    private final MarketRegime marketRegime;

    /** Assignment mode: AUTO, MANUAL, or AI_ASSISTED. */
    private final String assignmentMode;

    // =========================================================================
    // Lifecycle State
    // =========================================================================

    /** Current lifecycle stage. */
    private final StrategyLifecycleStatus lifecycleStatus;

    // =========================================================================
    // AI Review Data
    // =========================================================================

    /** Most recent AI review decision (null if not yet reviewed). */
    private final AIReviewDecision aiApprovalStatus;

    /** AI confidence in the most recent review (0.0-1.0). */
    private final double aiConfidence;

    /** Human-readable summary from the most recent AI review. */
    private final String aiReasoningSummary;

    /** Validation score from paper trading or backtest validation. */
    private final double validationScore;

    // =========================================================================
    // Reports
    // =========================================================================

    /** Most recent health report (null if not yet assessed). */
    private final StrategyHealthReport lastHealthReport;

    /** Most recent AI strategy review (null if not yet reviewed). */
    private final AIStrategyReview lastAIReview;

    /** Most recent validation report (null if not yet validated). */
    private final StrategyValidationReport lastValidationReport;

    /** Rank score for this strategy (null if not yet ranked). */
    private final StrategyRankScore rankScore;

    /** Learning profile accumulated from observed trades. */
    private final StrategyLearningProfile learningProfile;

    // =========================================================================
    // History
    // =========================================================================

    /** List of promotion events (each entry = ISO timestamp + reason). */
    private final List<String> promotionHistory;

    /** List of demotion events (each entry = ISO timestamp + reason). */
    private final List<String> demotionHistory;

    // =========================================================================
    // Timestamps
    // =========================================================================

    /** Timestamp when this record was first created. */
    @Builder.Default
    private final Instant createdAt = Instant.now();

    /** Timestamp when this record was last updated. */
    @Builder.Default
    private final Instant updatedAt = Instant.now();

    // =========================================================================
    // Convenience methods
    // =========================================================================

    /** @return true if this strategy is currently in a live trading state. */
    public boolean isLive() {
        return lifecycleStatus != null && lifecycleStatus.isLive();
    }

    /** @return true if this strategy is currently in paper trading. */
    public boolean isInPaperTrading() {
        return lifecycleStatus == StrategyLifecycleStatus.PAPER_TRADING;
    }

    /**
     * @return true if this record has sufficient validation data to be considered
     *         for promotion to live trading.
     */
    public boolean canBePromoted() {
        return lastValidationReport != null
                && lastValidationReport.isApprovedForLive()
                && validationScore >= 0.60
                && lifecycleStatus != null
                && lifecycleStatus.hasPaperApproval();
    }

    /**
     * @return true if the last health report indicates that this strategy needs
     *         replacement or demotion.
     */
    public boolean needsReplacement() {
        return lastHealthReport != null
                && lastHealthReport.getHealthLevel().requiresIntervention();
    }
}
