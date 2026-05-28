package org.investpro.decision;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Fully immutable institutional-grade execution decision composing all pipeline outputs.
 *
 * <p>{@code InstitutionalExecutionDecision} is the top-level decision object produced at the
 * end of the institutional pipeline. It composes:</p>
 * <ul>
 *   <li>The core {@link ExecutionDecision} from phase evaluations</li>
 *   <li>{@link ExecutionRoute} — where and how to execute</li>
 *   <li>{@link PositionSizingDecision} — computed position size</li>
 *   <li>{@link ExecutionLifecycle} — phase timestamps for audit</li>
 *   <li>{@link DecisionPerformanceMetrics} — timing for optimization</li>
 *   <li>Optional {@link BlockchainExecutionContext} — for on-chain execution</li>
 * </ul>
 *
 * <h3>Usage:</h3>
 * <p>New institutional code uses {@code InstitutionalExecutionDecision} directly.
 * For backward compatibility, use {@link BotTradeDecisionAssembler} to bridge
 * this into a {@link BotTradeDecision} for legacy consumers.</p>
 *
 * @param coreDecision    the evaluated execution decision (intent, risk, plan, reasoning)
 * @param route           selected execution route
 * @param sizing          recommended position sizing
 * @param lifecycle       lifecycle timestamps for audit and replay
 * @param metrics         pipeline performance timing metrics
 * @param blockchainCtx   optional blockchain context (non-null only for on-chain venues)
 * @param assembledAt     timestamp when this object was assembled
 */
public record InstitutionalExecutionDecision(
        ExecutionDecision coreDecision,
        ExecutionRoute route,
        PositionSizingDecision sizing,
        ExecutionLifecycle lifecycle,
        DecisionPerformanceMetrics metrics,
        @Nullable BlockchainExecutionContext blockchainCtx,
        Instant assembledAt
) {

    // ─── Compact constructor (validation) ─────────────────────────────────────

    public InstitutionalExecutionDecision {
        if (coreDecision == null) throw new IllegalArgumentException("coreDecision must not be null");
        if (route == null)        route     = ExecutionRoute.simulated();
        if (sizing == null)       sizing    = PositionSizingDecision.simulation();
        if (lifecycle == null)    lifecycle = ExecutionLifecycle.created();
        if (metrics == null)      metrics   = DecisionPerformanceMetrics.zero();
        if (assembledAt == null)  assembledAt = Instant.now();
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Assembles a full institutional decision from a core {@link ExecutionDecision}
     * plus all pipeline component outputs.
     */
    public static InstitutionalExecutionDecision from(
            ExecutionDecision coreDecision,
            ExecutionRoute route,
            PositionSizingDecision sizing,
            ExecutionLifecycle lifecycle,
            DecisionPerformanceMetrics metrics,
            @Nullable BlockchainExecutionContext blockchainCtx) {
        return new InstitutionalExecutionDecision(
                coreDecision, route, sizing, lifecycle, metrics, blockchainCtx, Instant.now());
    }

    /**
     * Creates a lightweight simulation decision wrapping a core {@link ExecutionDecision}.
     * Uses simulated defaults for all components. Avoids heavy allocations.
     */
    public static InstitutionalExecutionDecision simulation(ExecutionDecision coreDecision) {
        return new InstitutionalExecutionDecision(
                coreDecision,
                ExecutionRoute.simulated(),
                PositionSizingDecision.simulation(),
                ExecutionLifecycle.created(),
                DecisionPerformanceMetrics.zero(),
                null,
                Instant.now());
    }

    // ─── Delegation accessors (from coreDecision) ─────────────────────────────

    /** Returns the unique decision identifier. */
    public String decisionId() { return coreDecision.decisionId(); }

    /** Returns the lifecycle status of this decision. */
    public DecisionStatus status() { return coreDecision.status(); }

    /** Returns the execution mode (LIVE, PAPER, SIMULATION, LIGHTWEIGHT). */
    public DecisionMode mode() { return coreDecision.mode(); }

    /** Returns the trade intent that initiated this decision. */
    public TradeIntent intent() { return coreDecision.intent(); }

    /** Returns the risk evaluation outcome. */
    public RiskEvaluation riskEvaluation() { return coreDecision.riskEvaluation(); }

    /** Returns the execution plan (may be {@link ExecutionPlan#EMPTY} in simulation). */
    public ExecutionPlan executionPlan() { return coreDecision.executionPlan(); }

    /** Returns the AI/rule reasoning for this decision. */
    public DecisionReasoning reasoning() { return coreDecision.reasoning(); }

    /** Returns the portfolio impact analysis. */
    public PortfolioImpact portfolioImpact() { return coreDecision.portfolioImpact(); }

    /** Returns the multi-factor score breakdown. */
    public DecisionScoreBreakdown scoreBreakdown() { return coreDecision.scoreBreakdown(); }

    // ─── Derived properties ───────────────────────────────────────────────────

    /** Returns {@code true} if the decision is approved and ready for execution. */
    public boolean isApproved() {
        return coreDecision.status() == DecisionStatus.EXECUTION_PENDING
                || coreDecision.status() == DecisionStatus.EXECUTED;
    }

    /** Returns {@code true} if this is an on-chain (blockchain) execution. */
    public boolean isOnChain() {
        return route.isOnChain() || blockchainCtx != null;
    }

    /** Returns {@code true} if this is a simulated / backtesting decision. */
    public boolean isSimulated() {
        return mode() == DecisionMode.SIMULATION || mode() == DecisionMode.LIGHTWEIGHT
                || route.isSimulated();
    }

    /**
     * Creates a compact {@link DecisionSnapshot} for distributed workers.
     *
     * @param snapshotMode the desired level of detail
     */
    public DecisionSnapshot snapshot(DecisionSnapshot.SnapshotMode snapshotMode) {
        return switch (snapshotMode) {
            case FULL        -> DecisionSnapshot.full(coreDecision);
            case REPLAY      -> DecisionSnapshot.full(coreDecision);
            case ARCHIVE     -> DecisionSnapshot.archive(coreDecision);
            case LIGHTWEIGHT -> DecisionSnapshot.lightweight(coreDecision);
        };
    }

    @Override
    public String toString() {
        return "InstitutionalExecutionDecision{id=" + decisionId()
                + ", status=" + status()
                + ", mode=" + mode()
                + ", route=" + route.executionVenue()
                + ", sizing=" + sizing.effectiveSize()
                + ", onChain=" + isOnChain()
                + ", metrics=" + metrics + "}";
    }
}
