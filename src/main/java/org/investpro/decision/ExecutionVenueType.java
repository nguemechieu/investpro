package org.investpro.decision;

/**
 * Execution venue type for routing trade decisions.
 *
 * <p>Prepares the system for multi-venue execution including centralized exchanges,
 * brokers, DEX protocols, Solana/Stellar blockchain, and paper/simulation modes.</p>
 */
public enum ExecutionVenueType {

    /**
     * Centralized exchange (Coinbase, Binance, Kraken, etc.).
     * Order book based; API-driven; standard clearing.
     */
    CENTRALIZED_EXCHANGE("Centralized exchange", true, false, false),

    /**
     * Regulated broker (OANDA, Interactive Brokers, etc.).
     * FX/CFD/equity access; margin accounts; regulatory oversight.
     */
    BROKER("Regulated broker", true, false, false),

    /**
     * Decentralized exchange (Uniswap, Raydium, Jupiter, etc.).
     * AMM-based; on-chain settlement; slippage from liquidity pool.
     */
    DEX("Decentralized exchange", false, true, true),

    /**
     * Direct blockchain execution (Solana, Stellar, Ethereum).
     * Smart contract interaction; gas/fee based; self-custodied.
     */
    BLOCKCHAIN("Blockchain smart contract execution", false, true, true),

    /**
     * Simulated venue for backtesting. No real orders; ideal fill assumed
     * or configurable slippage model applied.
     */
    SIMULATED("Backtesting simulation", false, false, false),

    /**
     * Paper trading venue. Real market data; no real capital.
     * Orders tracked in paper ledger only.
     */
    PAPER("Paper trading", false, false, false);

    /** Whether this venue supports traditional order types (limit, market, stop). */
    public final boolean supportsTraditionalOrders;

    /** Whether this venue uses on-chain settlement. */
    public final boolean isOnChain;

    /** Whether this venue uses decentralized liquidity (AMM, pool-based). */
    public final boolean isDecentralized;

    ExecutionVenueType(
            String description,
            boolean supportsTraditionalOrders,
            boolean isOnChain,
            boolean isDecentralized) {
        this.supportsTraditionalOrders = supportsTraditionalOrders;
        this.isOnChain = isOnChain;
        this.isDecentralized = isDecentralized;
    }

    public boolean isSimulated() {
        return this == SIMULATED || this == PAPER;
    }

    /** Returns {@code true} if this venue uses on-chain settlement (same as {@link #isOnChain} field). */
    public boolean isOnChain() {
        return isOnChain;
    }

    public boolean requiresBlockchainWallet() {
        return isOnChain;
    }
}
