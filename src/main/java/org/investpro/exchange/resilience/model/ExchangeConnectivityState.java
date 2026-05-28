package org.investpro.exchange.resilience.model;

/**
 * Runtime connectivity state of an exchange connection.
 *
 * <p>Transitions:
 * <pre>
 *   DISCONNECTED ──► RECOVERING ──► CONNECTED
 *        ▲                               │
 *        │                               ▼
 *   CIRCUIT_OPEN ◄── DEGRADED ◄──────────┘
 * </pre>
 */
public enum ExchangeConnectivityState {

    /** All endpoints healthy, WebSocket active. */
    CONNECTED,

    /** At least one non-critical endpoint failing; execution still operational. */
    DEGRADED,

    /** All endpoints unreachable; no live data. */
    DISCONNECTED,

    /** Connection lost; automatic reconnect in progress. */
    RECOVERING,

    /**
     * Circuit breaker is open for one or more critical endpoints.
     * Requests are blocked until the cooldown expires and a probe succeeds.
     */
    CIRCUIT_OPEN;

    /** Returns true if the exchange is considered operational for live trading. */
    public boolean isOperational() {
        return this == CONNECTED || this == DEGRADED;
    }

    /** Returns true if execution should be suspended. */
    public boolean isExecutionBlocked() {
        return this == DISCONNECTED || this == CIRCUIT_OPEN;
    }
}
