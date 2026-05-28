package org.investpro.decision;

/**
 * Execution mode for trade decisions.
 *
 * <p>Controls memory allocation strategy and lifecycle behavior. LIGHTWEIGHT and SIMULATION
 * modes avoid UUID allocation, large metadata maps, and heavy reasoning storage to minimize
 * GC pressure during massive backtesting in Strategy Lab.</p>
 */
public enum DecisionMode {

    /**
     * Live production trading. Full lifecycle tracking, UUID IDs, persistence,
     * audit trails, and complete reasoning storage.
     */
    LIVE("Live production trading", true, true, true),

    /**
     * Paper trading with real market data. Full lifecycle but no real capital at risk.
     * UUID IDs and reasoning are stored.
     */
    PAPER("Paper trading with real market data", true, true, false),

    /**
     * Historical backtest simulation. Moderate allocation: avoids heavy reasoning
     * but tracks lifecycle. Uses sequential IDs for performance.
     */
    SIMULATION("Historical backtest simulation", false, false, false),

    /**
     * Lightweight mass simulation for Strategy Lab screening. Minimal allocations:
     * sequential IDs, no reasoning storage, no metadata maps.
     * Optimized for millions of decisions per second.
     */
    LIGHTWEIGHT("Lightweight mass simulation mode", false, false, false);

    /** Whether to use UUID-based decision IDs (vs sequential longs). */
    public final boolean useUuidIds;

    /** Whether to store full AI reasoning chain. */
    public final boolean storeReasoning;

    /** Whether this mode involves real capital at risk. */
    public final boolean isLive;

    DecisionMode(String description, boolean useUuidIds, boolean storeReasoning, boolean isLive) {
        this.useUuidIds = useUuidIds;
        this.storeReasoning = storeReasoning;
        this.isLive = isLive;
    }

    public boolean isSimulated() {
        return this == SIMULATION || this == LIGHTWEIGHT;
    }
}
