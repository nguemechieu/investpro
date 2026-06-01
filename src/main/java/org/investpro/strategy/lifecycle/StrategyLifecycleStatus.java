package org.investpro.strategy.lifecycle;

/**
 * Represents the lifecycle stage of a strategy assignment within the InvestPro
 * Institutional AI-Driven Strategy Lifecycle Management System.
 */
public enum StrategyLifecycleStatus {
    UNASSIGNED("No strategy assignment is currently active for symbol/timeframe"),
    DISCOVERED("Strategy has been discovered and is pending validation"),
    VALIDATED("Strategy API, configuration, and platform-safety checks passed"),
    BACKTESTED("Strategy has completed backtesting with enough data"),
    AI_REVIEWED("Strategy has been reviewed by the AI supervision layer"),
    PAPER_TRADING("Strategy is in paper trading validation phase"),
    PAPER_APPROVED("Strategy paper trading period has been reviewed and approved"),
    LIVE_APPROVED("Strategy has been approved for live trading"),
    LIVE_ACTIVE("Strategy is actively trading live"),
    RECOVERED("Assignment was loaded from persistence during startup recovery"),
    RESUMED("Assignment monitoring was safely resumed after recovery"),
    WATCH("Strategy is live but under close monitoring"),
    DEGRADED("Strategy performance has degraded below acceptable thresholds"),
    NEEDS_REVIEW("Assignment requires manual review before live actions"),
    RECONCILIATION_FAILED("Broker reconciliation failed for this assignment"),
    ORPHANED_POSITION("Broker position exists without matching assignment ownership"),
    POSITION_CLOSED_EXTERNALLY("Broker position closed outside InvestPro control"),
    REPLACEMENT_PENDING("Replacement requested but blocked by safety gates"),
    PAUSED("Strategy has been paused by AI or user"),
    DEMOTED("Strategy has been demoted from live to paper trading"),
    REPLACED("Strategy has been replaced by a better candidate"),
    ARCHIVED("Strategy has been archived and is no longer active"),

    /** @deprecated Use {@link #DISCOVERED}. */
    @Deprecated
    ASSIGNED("Legacy alias for discovered strategy assignments"),
    /** @deprecated Use {@link #BACKTESTED}. */
    @Deprecated
    BACKTESTING("Legacy alias for in-progress backtesting"),
    /** @deprecated Use {@link #AI_REVIEWED}. */
    @Deprecated
    AI_REVIEW("Legacy alias for AI-reviewed strategies"),
    /** @deprecated Use {@link #PAPER_APPROVED}. */
    @Deprecated
    VALIDATING("Legacy alias for paper approval validation"),
    /** @deprecated Use {@link #ARCHIVED}. */
    @Deprecated
    FAILED("Legacy alias for archived failed strategies");

    /** Human-readable description of this lifecycle stage. */
    public final String description;

    StrategyLifecycleStatus(String description) {
        this.description = description;
    }

    /**
     * @return true if the strategy is currently actively running (live or paper).
     */
    public boolean isActive() {
        return this == LIVE_ACTIVE || this == WATCH || this == PAPER_TRADING || this == RESUMED;
    }

    /** @return true if the strategy is deployed in a live trading environment. */
    public boolean isLive() {
        return this == LIVE_ACTIVE || this == WATCH || this == DEGRADED || this == RESUMED;
    }

    /** @return true if the strategy is in pre-deployment evaluation. */
    public boolean isPending() {
        return this == DISCOVERED || this == VALIDATED || this == BACKTESTED || this == AI_REVIEWED
                || this == BACKTESTING || this == AI_REVIEW || this == ASSIGNED || this == VALIDATING
                || this == UNASSIGNED || this == RECOVERED || this == REPLACEMENT_PENDING || this == NEEDS_REVIEW;
    }

    /**
     * @return true if the strategy has reached a terminal state and will no longer
     *         trade.
     */
    public boolean isTerminal() {
        return this == FAILED || this == ARCHIVED || this == REPLACED;
    }

    /**
     * @return true if this status can be treated as a validation-complete state.
     */
    public boolean hasValidationEvidence() {
        return this == VALIDATED || this == BACKTESTED || this == AI_REVIEWED || this == PAPER_TRADING
                || this == PAPER_APPROVED || this == LIVE_APPROVED || this == LIVE_ACTIVE
                || this == VALIDATING || this == RECOVERED || this == RESUMED;
    }

    /** @return true if this status has completed paper trading review. */
    public boolean hasPaperApproval() {
        return this == PAPER_APPROVED || this == LIVE_APPROVED || this == LIVE_ACTIVE || this == VALIDATING;
    }
}
