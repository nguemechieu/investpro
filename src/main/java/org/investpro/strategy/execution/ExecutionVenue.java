package org.investpro.strategy.execution;

/**
 * Supported execution venues for order routing in InvestPro.
 * Used by {@link ExecutionRouter} to select the appropriate venue
 * for a given symbol and strategy.
 */
public enum ExecutionVenue {
    // Legacy names retained for compatibility with existing routing code.
    COINBASE_ADVANCED("Coinbase Advanced Trade", "crypto", true),
    BINANCE_SPOT("Binance", "crypto", true),
    OANDA_REST("OANDA FX", "forex", true),
    INTERACTIVE_BROKERS("Interactive Brokers", "equities", true),

    COINBASE("Coinbase Advanced Trade", "crypto", true),
    OANDA("OANDA FX", "forex", true),
    BINANCE("Binance", "crypto", true),
    SOLONA_DEX("Solona DEX", "defi", true),
    STELLAR("Stellar Network", "defi", true),
    UNKNOWN("No exchange venue selected", "unknown", false);

    /** Display name for UI and logging. */
    public final String displayName;

    /** Asset class supported by this venue (crypto, forex, defi, simulation). */
    public final String assetClass;

    /** Whether this venue routes to a real live exchange. */
    public final boolean isLive;

    ExecutionVenue(String displayName, String assetClass, boolean isLive) {
        this.displayName = displayName;
        this.assetClass = assetClass;
        this.isLive = isLive;
    }

    /** @deprecated Simulation is an ExecutionMode, never an exchange venue. */
    @Deprecated
    public boolean isSimulation() {
        return false;
    }

    public ExecutionVenue canonical() {
        return switch (this) {
            case COINBASE_ADVANCED -> COINBASE;
            case BINANCE_SPOT -> BINANCE;
            case OANDA_REST -> OANDA;
            default -> this;
        };
    }
}
