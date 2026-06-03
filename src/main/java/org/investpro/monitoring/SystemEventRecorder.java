package org.investpro.monitoring;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Lightweight singleton for recording important system events with timestamps.
 * Used to track last occurrences of key events without mutating trading state.
 */
@Slf4j
@Getter
@Setter
public class SystemEventRecorder {
    private volatile Instant lastHeartbeatAt;
    private volatile String lastHeartbeatSource;

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
    private final AtomicLong heartbeatCount = new AtomicLong();
    private final AtomicLong marketTickCount = new AtomicLong();
    private final AtomicLong orderAcceptedCount = new AtomicLong();
    private final AtomicLong orderRejectedCount = new AtomicLong();
    private final AtomicLong executionErrorCount = new AtomicLong();
    private final AtomicLong webSocketDisconnectCount = new AtomicLong();
    private final AtomicLong accountUpdateCount = new AtomicLong();
    private final AtomicLong accountErrorCount = new AtomicLong();

    private static final SystemEventRecorder INSTANCE = new SystemEventRecorder();

    /**
     * Get singleton instance for global access.
     */
    public static SystemEventRecorder getInstance() {
        return INSTANCE;
    }

    public void recordHeartbeat(String source) {
        this.lastHeartbeatAt = Instant.now();
        this.lastHeartbeatSource = source == null ? "unknown" : source;
        heartbeatCount.incrementAndGet();
    }

    /**
     * Record a market tick event.
     */
    public void recordMarketTick() {
        this.lastMarketTickAt = Instant.now();
        marketTickCount.incrementAndGet();
    }

    /**
     * Record an accepted order event.
     */
    public void recordOrderAccepted() {
        this.lastOrderAcceptedAt = Instant.now();
        this.lastOrderRejectionReason = null;
        orderAcceptedCount.incrementAndGet();
    }

    /**
     * Record a rejected order event.
     */
    public void recordOrderRejected(String reason) {
        this.lastOrderRejectedAt = Instant.now();
        this.lastOrderRejectionReason = reason;
        orderRejectedCount.incrementAndGet();
    }

    /**
     * Record an execution error.
     */
    public void recordExecutionError(String error) {
        this.lastExecutionErrorAt = Instant.now();
        this.lastExecutionError = error;
        executionErrorCount.incrementAndGet();
    }

    /**
     * Record a rate limit event.
     */
    @SuppressWarnings("unused")
    public void recordRateLimit(String exchange) {
        this.lastRateLimitAt = Instant.now();
        this.lastRateLimitExchange = exchange;
        this.lastHttpStatusByExchange.put(exchange, 429);
    }

    /**
     * Record HTTP status code from an exchange.
     */
    @SuppressWarnings("unused")
    public void recordHttpStatus(String exchange, int statusCode) {
        this.lastHttpStatusByExchange.put(exchange, statusCode);
    }

    /**
     * Record a WebSocket disconnection.
     */
    @SuppressWarnings("unused")
    public void recordWebSocketDisconnect() {
        this.lastWebSocketDisconnectAt = Instant.now();
        webSocketDisconnectCount.incrementAndGet();
    }

    /**
     * Record account update with balance.
     */
    public void recordAccountUpdate(double balance) {
        this.lastAccountUpdateAt = Instant.now();
        this.lastAccountBalance = balance;
        accountUpdateCount.incrementAndGet();
    }

    /**
     * Record an account error.
     */
    @SuppressWarnings("unused")
    public void recordAccountError(String error) {
        this.lastAccountErrorAt = Instant.now();
        this.lastAccountError = error;
        accountErrorCount.incrementAndGet();
    }

    /**
     * Record trading session status change.
     */
    @SuppressWarnings("unused")
    public void recordTradingSessionStatus(String status) {
        this.lastTradingSessionStatus = status;
    }

    /**
     * Get seconds since last market tick.
     */
    @SuppressWarnings("unused")
    public long getSecondsSinceLastTick() {
        if (lastMarketTickAt == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.SECONDS.between(lastMarketTickAt, Instant.now());
    }

    /**
     * Get seconds since last rate limit.
     */
    @SuppressWarnings("unused")
    public long getSecondsSinceLastRateLimit() {
        if (lastRateLimitAt == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.SECONDS.between(lastRateLimitAt, Instant.now());
    }

    public long getSecondsSinceLastHeartbeat() {
        if (lastHeartbeatAt == null) {
            return Long.MAX_VALUE;
        }
        return java.time.temporal.ChronoUnit.SECONDS.between(lastHeartbeatAt, Instant.now());
    }

    public Map<String, Long> snapshotCounters() {
        Map<String, Long> counters = new ConcurrentHashMap<>();
        counters.put("heartbeatCount", heartbeatCount.get());
        counters.put("marketTickCount", marketTickCount.get());
        counters.put("orderAcceptedCount", orderAcceptedCount.get());
        counters.put("orderRejectedCount", orderRejectedCount.get());
        counters.put("executionErrorCount", executionErrorCount.get());
        counters.put("webSocketDisconnectCount", webSocketDisconnectCount.get());
        counters.put("accountUpdateCount", accountUpdateCount.get());
        counters.put("accountErrorCount", accountErrorCount.get());
        return counters;
    }
}
