package org.investpro.strategy;

/**
 * Lifecycle validation status for a strategy as it progresses through the
 * InvestPro safety-gate pipeline.
 *
 * <p>No strategy may be promoted to {@code LIVE_APPROVED} without first
 * completing all preceding stages. Neither strategy type (plugin or no-code)
 * may bypass these gates.</p>
 */
public enum StrategyValidationStatus {

    /** Strategy has just been registered; no validation has occurred yet. */
    UNVALIDATED("Unvalidated", false),

    /** Structural / configuration validation is running. */
    VALIDATING("Validating", false),

    /** Structural validation passed; strategy is ready for backtesting. */
    VALIDATED("Validated", false),

    /** Backtesting completed successfully. */
    BACKTESTED("Backtested", false),

    /** AI backtest review completed. */
    AI_REVIEWED("AI Reviewed", false),

    /** Currently in paper-trading phase. */
    PAPER_TRADING("Paper Trading", false),

    /** Paper-trading validation passed; awaiting live approval decision. */
    PAPER_APPROVED("Paper Approved", false),

    /** Approved for live trading by both AI and risk governance. */
    LIVE_APPROVED("Live Approved", true),

    /** Validation or review has failed at some stage. */
    FAILED("Failed", false),

    /** Archived — will no longer be evaluated or traded. */
    ARCHIVED("Archived", false);

    /** Short label used in UI columns. */
    public final String displayName;

    /** Whether this status allows live assignment. */
    public final boolean allowsLiveTrading;

    StrategyValidationStatus(String displayName, boolean allowsLiveTrading) {
        this.displayName = displayName;
        this.allowsLiveTrading = allowsLiveTrading;
    }

    /** @return true if the strategy may proceed to the next pipeline stage. */
    public boolean isProgressing() {
        return this != FAILED && this != ARCHIVED;
    }
}
