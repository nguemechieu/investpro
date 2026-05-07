package org.investpro.core.agents.symbol;

/**
 * Represents the trading mode/readiness state of a symbol.
 * 
 * Used by MarketWatch and System Monitor to display symbol status at a glance.
 */
public enum SymbolTradingMode {
    TRAINING("Training / Evaluating", false),
    PAPER_TRADING("Paper Trading", false),
    LIVE_READY("Live Ready", true),
    LIVE_TRADING("Live Trading", true),
    NO_ASSIGNMENT("No Assignment", false),
    BLOCKED("Blocked", false),
    PAUSED("Paused", false),
    FAILED("Failed", false),
    UNKNOWN("Unknown", false);

    private final String displayName;
    private final boolean liveAllowed;

    SymbolTradingMode(String displayName, boolean liveAllowed) {
        this.displayName = displayName;
        this.liveAllowed = liveAllowed;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isLiveAllowed() {
        return liveAllowed;
    }

    /**
     * Returns true if this mode represents any form of live trading.
     */
    public boolean isLiveMode() {
        return this == LIVE_READY || this == LIVE_TRADING;
    }

    /**
     * Returns true if this mode represents any form of trading (paper or live).
     */
    public boolean isTradingMode() {
        return this != NO_ASSIGNMENT && this != BLOCKED && this != PAUSED && this != FAILED && this != UNKNOWN;
    }
}
