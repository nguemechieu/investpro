package org.investpro.exchange.execution;

/**
 * Classifies the type of venue through which an order is executed.
 *
 * <p>Each venue value carries metadata about whether real funds are at risk
 * ({@link #isRealFunds()}) and whether execution occurs on-chain
 * ({@link #isOnChain()}). The {@link #getDisplayName()} provides a
 * human-readable label suitable for dashboards.
 *
 * <p>Usage example:
 * <pre>{@code
 * ExecutionVenue venue = ExecutionVenue.CENTRALIZED;
 * if (venue.isRealFunds()) {
 *     log.warn("Real funds in play — confirm risk limits.");
 * }
 * }</pre>
 */
public enum ExecutionVenue {

    /**
     * Traditional centralised exchange (e.g., Coinbase, Binance, Bitfinex).
     * Orders are submitted via REST/WebSocket and matched by the exchange's
     * central order book.  Real funds are at risk; execution is off-chain.
     */
    CENTRALIZED(true, false, "Centralised Exchange"),

    /**
     * Regulated broker (e.g., OANDA, Interactive Brokers, Alpaca).
     * Orders are routed through a licensed intermediary; real funds are at risk.
     */
    BROKER(true, false, "Regulated Broker"),

    /**
     * Decentralised exchange (e.g., Uniswap, dYdX).
     * Trades are settled via on-chain smart contracts; real funds are at risk.
     */
    DEX(true, true, "Decentralised Exchange"),

    /**
     * Direct on-chain execution (e.g., Solana DEX program, Stellar DEX).
     * Orders are submitted as blockchain transactions; real funds are at risk.
     */
    BLOCKCHAIN(true, true, "Blockchain Direct"),

    /**
     * Paper trading mode.  Orders are simulated without any real fund movement.
     * Useful for strategy validation in a live-data environment.
     */
    PAPER(false, false, "Paper Trading"),

    /**
     * Backtesting / simulation mode.
     * Orders are evaluated against historical data; no real funds involved.
     */
    SIMULATED(false, false, "Simulation / Backtest");

    // ─────────────────────────────────────────────────────────────────────────

    private final boolean realFunds;
    private final boolean onChain;
    private final String displayName;

    ExecutionVenue(boolean realFunds, boolean onChain, String displayName) {
        this.realFunds = realFunds;
        this.onChain = onChain;
        this.displayName = displayName;
    }

    /**
     * Returns {@code true} if this venue involves real monetary exposure.
     * Any venue where {@code realFunds == true} must pass risk-management
     * checks before order submission.
     */
    public boolean isRealFunds() {
        return realFunds;
    }

    /**
     * Returns {@code true} if execution is settled directly on a blockchain
     * (as opposed to an off-chain central order book).
     */
    public boolean isOnChain() {
        return onChain;
    }

    /**
     * Returns a human-readable display name for this venue (e.g., for UI labels
     * and log messages).
     */
    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
