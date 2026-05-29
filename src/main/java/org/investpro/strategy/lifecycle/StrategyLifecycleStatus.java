package org.investpro.strategy.lifecycle;

/**
 * Represents the lifecycle stage of a strategy assignment within the InvestPro
 * Institutional AI-Driven Strategy Lifecycle Management System.
 */
public enum StrategyLifecycleStatus {
    DISCOVERED("Strategy has been discovered and is pending backtesting"),
    BACKTESTING("Strategy is currently being backtested"),
    AI_REVIEW("Strategy backtest is under AI review"),
    RANKED("Strategy has been ranked following AI review"),
    ASSIGNED("Strategy has been assigned to a symbol/timeframe"),
    PAPER_TRADING("Strategy is in paper trading validation phase"),
    VALIDATING("Strategy paper trade results are being validated by AI"),
    LIVE_APPROVED("Strategy has been approved for live trading"),
    LIVE_ACTIVE("Strategy is actively trading live"),
    WATCH("Strategy is live but under close monitoring"),
    DEGRADED("Strategy performance has degraded below acceptable thresholds"),
    PAUSED("Strategy has been paused by AI or user"),
    DEMOTED("Strategy has been demoted from live to paper trading"),
    REPLACED("Strategy has been replaced by a better candidate"),
    FAILED("Strategy has failed validation or performance requirements"),
    ARCHIVED("Strategy has been archived and is no longer active");

    /** Human-readable description of this lifecycle stage. */
    public final String description;

    StrategyLifecycleStatus(String description) {
        this.description = description;
    }

    /** @return true if the strategy is currently actively running (live or paper). */
    public boolean isActive() {
        return this == LIVE_ACTIVE || this == WATCH || this == PAPER_TRADING;
    }

    /** @return true if the strategy is deployed in a live trading environment. */
    public boolean isLive() {
        return this == LIVE_ACTIVE || this == WATCH || this == DEGRADED;
    }

    /** @return true if the strategy is in pre-deployment evaluation. */
    public boolean isPending() {
        return this == DISCOVERED || this == BACKTESTING || this == AI_REVIEW || this == RANKED;
    }

    /** @return true if the strategy has reached a terminal state and will no longer trade. */
    public boolean isTerminal() {
        return this == FAILED || this == ARCHIVED || this == REPLACED;
    }
}
