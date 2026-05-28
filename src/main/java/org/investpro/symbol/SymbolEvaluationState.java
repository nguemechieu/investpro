package org.investpro.symbol;

/**
 * Represents the evaluation state of a symbol's strategy.
 * 
 * Lifecycle: NOT_STARTED -> COLLECTING_DATA -> BACKTESTING -> RANKING -> PAPER_TRADING -> ASSIGNED -> LIVE_READY -> LIVE_TRADING
 */
public enum SymbolEvaluationState {
    NOT_STARTED("Not Started", "Strategy evaluation has not started", false),
    COLLECTING_DATA("Collecting Data", "Gathering historical data for evaluation", false),
    BACKTESTING("Backtesting", "Testing strategy on historical data", false),
    RANKING("Ranking", "Ranking strategy candidates", false),
    PAPER_TRADING("Paper Trading", "Testing selected strategy in paper trading mode", false),
    ASSIGNED("Assigned", "Strategy assigned and ready for live or additional evaluation", false),
    LIVE_READY("Live Ready", "Strategy ready for live trading (pending live mode selection)", true),
    LIVE_TRADING("Live Trading", "Strategy actively trading in live mode", true),
    PAUSED("Paused", "Strategy execution paused", false),
    FAILED("Failed", "Strategy evaluation or execution failed", false);

    private final String displayName;
    private final String description;
    private final boolean ready;

    SymbolEvaluationState(String displayName, String description, boolean ready) {
        this.displayName = displayName;
        this.description = description;
        this.ready = ready;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Returns true if this state represents a ready-for-live state.
     */
    public boolean isReadyForLive() {
        return ready;
    }

    /**
     * Returns true if evaluation is still in progress.
     */
    public boolean isEvaluating() {
        return this == NOT_STARTED || this == COLLECTING_DATA || this == BACKTESTING || this == RANKING || this == PAPER_TRADING;
    }

    /**
     * Returns true if strategy is actively assigned and ready.
     */
    public boolean isAssignedOrReady() {
        return this == ASSIGNED || this == LIVE_READY || this == LIVE_TRADING;
    }
}
