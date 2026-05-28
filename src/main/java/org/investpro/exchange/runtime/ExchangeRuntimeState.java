package org.investpro.exchange.runtime;

/**
 * Full runtime lifecycle state of an exchange connection, extending the basic
 * connectivity states with authentication, staleness, and synchronization states.
 *
 * <p>State transition diagram:
 * <pre>
 *   DISCONNECTED ──► RECOVERING ──► SYNCHRONIZING ──► CONNECTED
 *        ▲                                                  │
 *        │                                                  ▼
 *   CIRCUIT_OPEN ◄── DEGRADED ◄─────────────────────────────┘
 *        │
 *   AUTH_FAILED ← (credential check fails at any state)
 *   STALE       ← (WebSocket data age exceeds threshold)
 * </pre>
 */
public enum ExchangeRuntimeState {

    /** All endpoints healthy, WebSocket active, data fresh. */
    CONNECTED,

    /** At least one non-critical endpoint failing; execution still operational. */
    DEGRADED,

    /** All endpoints unreachable; no live data available. */
    DISCONNECTED,

    /** Connection lost; automatic reconnect in progress. */
    RECOVERING,

    /**
     * Circuit breaker open for one or more critical endpoints.
     * Requests are blocked until the cooldown expires and a probe succeeds.
     */
    CIRCUIT_OPEN,

    /**
     * Credentials are invalid or expired. Manual intervention required.
     * Execution is blocked until re-authentication succeeds.
     */
    AUTH_FAILED,

    /**
     * WebSocket or REST data is beyond the acceptable freshness threshold.
     * The runtime is still connected but serving potentially stale quotes.
     */
    STALE,

    /**
     * Initial state after (re)connection: loading balances, positions, open orders.
     * Transitions to CONNECTED once the initial state reconciliation completes.
     */
    SYNCHRONIZING;

    /** Returns true if the exchange can serve live market data. */
    public boolean isLiveDataAvailable() {
        return this == CONNECTED || this == DEGRADED;
    }

    /** Returns true if order execution should be blocked. */
    public boolean isExecutionBlocked() {
        return this == DISCONNECTED || this == CIRCUIT_OPEN || this == AUTH_FAILED;
    }

    /** Returns true if the state indicates an error condition requiring attention. */
    public boolean isErrorState() {
        return this == CIRCUIT_OPEN || this == AUTH_FAILED || this == DISCONNECTED;
    }

    /** Returns true if a reconnect/recovery attempt is in progress. */
    public boolean isRecovering() {
        return this == RECOVERING || this == SYNCHRONIZING;
    }

    /** Returns true if data freshness is questionable. */
    public boolean isDataStale() {
        return this == STALE;
    }
}
