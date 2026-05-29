package org.investpro.strategy.lifecycle;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable result of an AI review of a strategy backtest or paper trading
 * period.
 * Produced by {@link org.investpro.strategy.ai.AIStrategyReviewEngine}.
 * This is advisory only — the lifecycle manager makes the final decision.
 */
@Getter
@Builder
@ToString
public class AIStrategyReview {

    /** Unique review identifier (UUID). */
    @Builder.Default
    private final String reviewId = UUID.randomUUID().toString();

    /** Strategy identifier. */
    private final String strategyId;

    /** Trading symbol. */
    private final String symbol;

    /** Timeframe. */
    private final String timeframe;

    /** The AI decision outcome. */
    private final AIReviewDecision decision;

    /** AI confidence in this review (0.0-1.0). */
    private final double aiConfidence;

    /** Human-readable summary of the AI reasoning. */
    private final String reasoningSummary;

    /** Specific reasons for rejection (empty if approved). */
    private final List<String> rejectionReasons;

    /** True if the strategy shows signs of curve fitting / overfitting. */
    private final boolean overfitWarning;

    /** True if the strategy is too simple / underfit to capture patterns. */
    private final boolean underfitWarning;

    /** True if the sample size is sufficient for reliable statistical inference. */
    private final boolean sampleSizeSufficient;

    /** True if the profit factor meets the minimum acceptable threshold. */
    private final boolean profitFactorAcceptable;

    /** True if the maximum drawdown is within the acceptable limit. */
    private final boolean drawdownAcceptable;

    /**
     * Regime compatibility score (0.0-1.0): how well the strategy fits the current
     * regime.
     */
    private final double regimeCompatibilityScore;

    /** True if the backtest results are statistically meaningful. */
    private final boolean statisticallyMeaningful;

    /** AI recommendation for the next step in the lifecycle. */
    private final String recommendedNextStep;

    /** Timestamp when this review was generated. */
    private final Instant reviewedAt;

    /** @return true if the strategy was approved to proceed. */
    public boolean isApproved() {
        return decision == AIReviewDecision.APPROVE;
    }

    /** Institutional lifecycle alias: AI score on a 0-100 scale. */
    public double getAiScore() {
        return aiConfidence * 100.0;
    }

    /** Institutional lifecycle alias for the approval decision. */
    public AIReviewDecision getApprovalStatus() {
        return decision;
    }

    /** Institutional lifecycle alias for overfit risk on a 0-1 scale. */
    public double getOverfitRisk() {
        return overfitWarning ? 1.0 : 0.0;
    }

    /** Institutional lifecycle alias for confidence. */
    public double getConfidence() {
        return aiConfidence;
    }

    /** Institutional lifecycle alias for the recommendation. */
    public String getRecommendation() {
        return recommendedNextStep;
    }

    /** Institutional lifecycle alias for the reasoning summary. */
    public String getReasoning() {
        return reasoningSummary;
    }

    /** @return true if more work/data is needed before a final determination. */
    public boolean requiresMoreWork() {
        return decision != null && decision.requiresMoreWork();
    }

    /**
     * @return true if the review found critical issues that would prevent live
     *         deployment.
     *         Critical issues: overfit, insufficient sample, unacceptable drawdown.
     */
    public boolean hasCriticalIssues() {
        return overfitWarning || !sampleSizeSufficient || !drawdownAcceptable;
    }

    /**
     * Backward-compatible alias for older UI panels.
     */
    public List<String> getReasoningPoints() {
        if (reasoningSummary == null || reasoningSummary.isBlank()) {
            return List.of();
        }
        return List.of(reasoningSummary);
    }

    /**
     * Backward-compatible alias for older UI panels.
     */
    public List<String> getWarnings() {
        List<String> warnings = new ArrayList<>();
        if (overfitWarning) {
            warnings.add("Potential overfitting detected");
        }
        if (underfitWarning) {
            warnings.add("Potential underfitting detected");
        }
        if (!sampleSizeSufficient) {
            warnings.add("Sample size may be insufficient");
        }
        if (!profitFactorAcceptable) {
            warnings.add("Profit factor below threshold");
        }
        if (!drawdownAcceptable) {
            warnings.add("Drawdown exceeds acceptable threshold");
        }
        return warnings;
    }
}
