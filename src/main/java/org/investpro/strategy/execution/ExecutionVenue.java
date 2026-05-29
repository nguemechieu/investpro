package org.investpro.strategy.execution;

/**
 * Supported execution venues for order routing in InvestPro.
 * Used by {@link ExecutionRouter} to select the appropriate venue
 * for a given symbol and strategy.
 */
public enum ExecutionVenue {
    COINBASE("Coinbase Advanced Trade", "crypto", true),
    OANDA("OANDA FX", "forex", true),
    BINANCE("Binance", "crypto", true),
    SOLANA_DEX("Solana DEX", "defi", true),
    STELLAR("Stellar Network", "defi", true),
    PAPER_TRADING("Paper Trading Simulator", "simulation", false),
    SIMULATION("Backtest Simulation Engine", "simulation", false);

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

    /** @return true if this venue is a simulation and does not send real orders. */
    public boolean isSimulation() {
        return !isLive;
    }
}
