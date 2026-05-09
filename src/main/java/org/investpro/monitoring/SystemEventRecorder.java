package org.investpro.monitoring;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight singleton for recording important system events with timestamps.
 * Used to track last occurrences of key events without mutating trading state.
 */
@Slf4j
@Getter
@Setter
public class SystemEventRecorder {
    // Market data events
    private volatile Instant lastMarketTickAt;

    // Order events
    private volatile Instant lastOrderAcceptedAt;
    private volatile Instant lastOrderRejectedAt;
    private volatile String lastOrderRejectionReason;

    // Execution errors
    private volatile Instant lastExecutionErrorAt;
    private volatile String lastExecutionError;

    // Exchange-level events
    private volatile Instant lastRateLimitAt;
    private volatile String lastRateLimitExchange;
    private volatile Instant lastWebSocketDisconnectAt;

    // Account events
    private volatile Instant lastAccountUpdateAt;
    private volatile Double lastAccountBalance;
    private volatile Instant lastAccountErrorAt;
    private volatile String lastAccountError;

    // Status tracking
    private volatile String lastTradingSessionStatus;

    // Rate limit tracking by exchange
    private final Map<String, Integer> lastHttpStatusByExchange = new ConcurrentHashMap<>();

    private static final SystemEventRecorder INSTANCE = new SystemEventRecorder();

    /**
     * Get singleton instance for global access.
     */
    public static SystemEventRecorder getInstance() {
        return INSTANCE;
    }

    /**
     * Record a market tick event.
     */
    public void recordMarketTick() {
        this.lastMarketTickAt = Instant.now();
    }

    /**
     * Record an accepted order event.
     */
    public void recordOrderAccepted(String orderId) {
        this.lastOrderAcceptedAt = Instant.now();
        this.lastOrderRejectionReason = null;
    }

    /**
     * Record a rejected order event.
     */
    public void recordOrderRejected(String orderId, String reason) {
        this.lastOrderRejectedAt = Instant.now();
        this.lastOrderRejectionReason = reason;
    }

    /**
     * Record an execution error.
     */
    public void recordExecutionError(String error) {
        this.lastExecutionErrorAt = Instant.now();
        this.lastExecutionError = error;
    }

    /**
     * Record a rate limit event.
     */
    public void recordRateLimit(String exchange) {
        this.lastRateLimitAt = Instant.now();
        this.lastRateLimitExchange = exchange;
        this.lastHttpStatusByExchange.put(exchange, 429);
    }

    /**
     * Record HTTP status code from an exchange.
     */
    public void recordHttpStatus(String exchange, int statusCode) {
        this.lastHttpStatusByExchange.put(exchange, statusCode);
    }

    /**
     * Record a WebSocket disconnection.
     */
    public void recordWebSocketDisconnect(String reason) {
        this.lastWebSocketDisconnectAt = Instant.now();
    }

    /**
     * Record account update with balance.
     */
    public void recordAccountUpdate(double balance) {
        this.lastAccountUpdateAt = Instant.now();
        this.lastAccountBalance = balance;
    }

    /**
     * Record an account error.
     */
    public void recordAccountError(String error) {
        this.lastAccountErrorAt = Instant.now();
        this.lastAccountError = error;
    }

    /**
     * Record trading session status change.
     */
    public void recordTradingSessionStatus(String status) {
        this.lastTradingSessionStatus = status;
    }

    /**
     * Get seconds since last market tick.
     */
    public long getSecondsSinceLastTick() {
        if (lastMarketTickAt == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.SECONDS.between(lastMarketTickAt, Instant.now());
    }

    /**
     * Get seconds since last rate limit.
     */
    public long getSecondsSinceLastRateLimit() {
        if (lastRateLimitAt == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.SECONDS.between(lastRateLimitAt, Instant.now());
    }
}
