package org.investpro.decision;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;

/**
 * Final go/no-go decision for a trade, with complete lifecycle tracking.
 *
 * <p>ExecutionDecision is the output of the full pipeline: signal → intent → risk →
 * AI reasoning → execution planning → this decision. It aggregates all upstream results
 * and provides the definitive answer: should the trade be submitted?</p>
 *
 * <p>Downstream consumers (execution coordinators, portfolio managers, audit systems)
 * interact only with this object — not with the raw pipeline components.</p>
 */
public record ExecutionDecision(

        /** Unique decision identifier (UUID for LIVE, sequential for SIMULATION). */
        @NotNull String decisionId,

        /** Overall lifecycle status of this decision. */
        @NotNull DecisionStatus status,

        /** Execution mode (LIVE, PAPER, SIMULATION, LIGHTWEIGHT). */
        @NotNull DecisionMode mode,

        /** The trade intent that initiated this pipeline run. */
        @NotNull TradeIntent intent,

        /** Risk evaluation result. May be null in LIGHTWEIGHT mode. */
        @Nullable RiskEvaluation riskEvaluation,

        /** Execution plan. Null if decision is not EXECUTION_PENDING or EXECUTED. */
        @Nullable ExecutionPlan executionPlan,

        /** AI reasoning. Null if reasoning was skipped or mode is LIGHTWEIGHT. */
        @Nullable DecisionReasoning reasoning,

        /** Portfolio impact analysis. Null if portfolio analyzer is not configured. */
        @Nullable PortfolioImpact portfolioImpact,

        /** Score breakdown across all pipeline dimensions. */
        @Nullable DecisionScoreBreakdown scoreBreakdown,

        /** Typed execution context. Null in LIGHTWEIGHT mode. */
        @Nullable DecisionContext context,

        /** Human-readable explanation of the decision outcome. */
        @NotNull String explanation,

        /** Hard blockers that prevented execution. */
        @NotNull List<String> blockers,

        /** Non-fatal warnings accompanying the decision. */
        @NotNull List<String> warnings,

        /** When this decision was finalized. */
        @NotNull Instant decidedAt

) {

    /** Returns true if this decision permits trade execution. */
    public boolean isApproved() {
        return status == DecisionStatus.EXECUTION_PENDING || status == DecisionStatus.EXECUTED;
    }

    /** Returns true if any hard blockers are present. */
    public boolean hasBlockers() {
        return !blockers.isEmpty();
    }

    /** Returns the composite score if a breakdown is available, otherwise -1.0. */
    public double compositeScore() {
        return scoreBreakdown != null ? scoreBreakdown.compositeScore() : -1.0;
    }

    /**
     * Quick factory for a rejected decision (risk or AI veto).
     */
    public static ExecutionDecision rejected(
            @NotNull String decisionId,
            @NotNull DecisionMode mode,
            @NotNull TradeIntent intent,
            @NotNull DecisionStatus rejectionStatus,
            @NotNull List<String> blockers,
            @NotNull String explanation) {

        return new ExecutionDecision(
                decisionId, rejectionStatus, mode, intent,
                null, null, null, null, null, null,
                explanation, List.copyOf(blockers), List.of(),
                Instant.now());
    }

    /**
     * Quick factory for an approved, execution-ready decision.
     *
     * <p>{@code plan} may be {@code null} in SIMULATION and LIGHTWEIGHT modes where
     * tick-level pricing is not available during mass backtesting. In LIVE and PAPER
     * modes a non-null, valid {@link ExecutionPlan} is expected.</p>
     */
    public static ExecutionDecision approved(
            @NotNull String decisionId,
            @NotNull DecisionMode mode,
            @NotNull TradeIntent intent,
            @NotNull RiskEvaluation risk,
            @Nullable ExecutionPlan plan,
            @Nullable DecisionReasoning reasoning,
            @Nullable PortfolioImpact portfolio,
            @Nullable DecisionScoreBreakdown scores,
            @Nullable DecisionContext ctx,
            @NotNull List<String> warnings,
            @NotNull String explanation) {

        return new ExecutionDecision(
                decisionId, DecisionStatus.EXECUTION_PENDING, mode, intent,
                risk, plan, reasoning, portfolio, scores, ctx,
                explanation, List.of(), List.copyOf(warnings),
                Instant.now());
    }
}
