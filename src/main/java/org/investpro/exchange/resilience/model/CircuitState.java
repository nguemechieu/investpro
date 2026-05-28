package org.investpro.exchange.resilience.model;

/**
 * State of a per-endpoint circuit breaker.
 *
 * <p>State machine:
 * <pre>
 *   CLOSED ──(failures exceed threshold)──► OPEN
 *     ▲                                       │
 *     │                              (cooldown expires)
 *     │                                       ▼
 *     └──────(probe succeeds)────── HALF_OPEN
 * </pre>
 */
public enum CircuitState {

    /**
     * Circuit is closed (normal operation).
     * All requests flow through; failures are counted.
     */
    CLOSED,

    /**
     * Circuit is open (tripped).
     * All requests are blocked immediately; stale cache is served.
     * A single probe attempt is allowed after the cooldown window expires.
     */
    OPEN,

    /**
     * Circuit is half-open (recovery probe in flight).
     * One trial request is allowed. Success transitions to CLOSED;
     * failure resets the backoff and transitions back to OPEN.
     */
    HALF_OPEN;

    public boolean isRequestAllowed() {
        return this == CLOSED || this == HALF_OPEN;
    }

    public boolean isTripped() {
        return this == OPEN || this == HALF_OPEN;
    }
}
