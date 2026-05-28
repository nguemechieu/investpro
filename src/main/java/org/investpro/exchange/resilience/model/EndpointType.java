package org.investpro.exchange.resilience.model;

/**
 * Categorizes each exchange REST endpoint by function and criticality.
 *
 * <p>Critical endpoints directly affect live trading and must never be silenced
 * by non-critical endpoint failures. Non-critical endpoints serve analytics,
 * history, and informational purposes and are served from stale cache on failure.
 */
public enum EndpointType {

    // ── Critical endpoints (affect live trading) ──────────────────────────────

    /** Live price streaming or REST ticker. */
    PRICING(true, 1),

    /** Order submission and management. */
    EXECUTION(true, 1),

    /** Account balance queries. */
    BALANCES(true, 2),

    /** Open position queries. */
    POSITIONS(true, 2),

    /** Full account summary. */
    ACCOUNT(true, 3),

    // ── Non-critical endpoints (analytics / history) ───────────────────────────

    /** Historical order list. Stale cache used on failure. */
    ORDER_HISTORY(false, 4),

    /** Trade fill history. Stale cache used on failure. */
    TRADE_HISTORY(false, 5),

    /** Instrument analytics and statistics. */
    ANALYTICS(false, 6),

    /** Archived transaction history. */
    TRANSACTIONS(false, 6);

    /** Whether this endpoint directly impacts live trading. */
    public final boolean critical;

    /**
     * Request priority (lower = higher priority).
     * Used by {@code RequestPrioritizationQueue}.
     */
    public final int priority;

    EndpointType(boolean critical, int priority) {
        this.critical = critical;
        this.priority = priority;
    }
}
