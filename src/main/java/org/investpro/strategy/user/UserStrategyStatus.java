package org.investpro.strategy.user;

/**
 * Lifecycle status for user-developed strategies.
 *
 * Tracks progress from discovery through live trading assignment.
 */
public enum UserStrategyStatus {
    /**
     * JAR file found but not yet loaded.
     */
    DISCOVERED,

    /**
     * Successfully loaded from JAR file.
     */
    LOADED,

    /**
     * Validation checks passed (structure, metadata, basic execution).
     */
    VALIDATED,

    /**
     * Validation failed or strategy is broken.
     */
    INVALID,

    /**
     * Currently running backtest.
     */
    BACKTESTING,

    /**
     * Backtest completed successfully with acceptable performance.
     */
    BACKTEST_PASSED,

    /**
     * Backtest failed or did not meet performance criteria.
     */
    BACKTEST_FAILED,

    /**
     * Currently paper trading (simulated live trading).
     */
    PAPER_TRADING,

    /**
     * Paper trading completed successfully with acceptable performance.
     */
    PAPER_PASSED,

    /**
     * Paper trading failed or did not meet performance criteria.
     */
    PAPER_FAILED,

    /**
     * All requirements met, eligible for live trading.
     */
    LIVE_READY,

    /**
     * Currently assigned to live trading on one or more symbols.
     */
    LIVE_ASSIGNED,

    /**
     * Disabled - will not be loaded or assigned.
     */
    DISABLED;

    public boolean isReady() {
        return this == LIVE_READY || this == LIVE_ASSIGNED;
    }

    public boolean isFailure() {
        return this == INVALID || this == BACKTEST_FAILED || this == PAPER_FAILED;
    }

    public boolean isInProgress() {
        return this == BACKTESTING || this == PAPER_TRADING;
    }
}
