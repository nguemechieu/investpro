package org.investpro.exchange.execution;

/**
 * Categorizes the execution venue for a trade request.
 *
 * <p>The {@link org.investpro.exchange.routing.SmartExecutionRouter} selects the
 * appropriate venue based on the instrument, capability profile, and execution
 * constraints in the {@link ExecutionRequest}.
 */
public enum ExecutionVenue {

    /** Traditional centralized exchange (Coinbase, Binance, Bitfinex, etc.). */
    CENTRALIZED(true, false, "Centralized Exchange"),

    /** Regulated broker platform (OANDA, Interactive Brokers, Alpaca, etc.). */
    BROKER(true, false, "Regulated Broker"),

    /** Decentralized exchange (Uniswap, Jupiter, etc.). Requires on-chain signing. */
    DEX(true, true, "Decentralized Exchange"),

    /** Direct on-chain execution via smart contract or native transfer (Solona, Stellar). */
    BLOCKCHAIN(true, true, "Blockchain Direct"),

    /** Paper trading mode — no real funds, orders are simulated locally. */
    PAPER(false, false, "Paper Trading"),

    /** Backtesting or simulation mode — purely in-memory, no network calls. */
    SIMULATED(false, false, "Simulation");

    /** True if real funds are involved. */
    public final boolean realFunds;
    /** True if execution requires on-chain transaction signing. */
    public final boolean onChain;
    /** Human-readable display name. */
    public final String displayName;

    ExecutionVenue(boolean realFunds, boolean onChain, String displayName) {
        this.realFunds = realFunds;
        this.onChain = onChain;
        this.displayName = displayName;
    }

    /** Returns true if execution involves real financial risk. */
    public boolean isRealFunds() { return realFunds; }

    /** Returns true if on-chain signing is required. */
    public boolean isOnChain() { return onChain; }

    /** Returns the human-readable display name. */
    public String getDisplayName() { return displayName; }
}
