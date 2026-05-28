package org.investpro.exchange.resilience;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.resilience.model.EndpointHealthSnapshot;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Dedicated telemetry engine for the exchange connectivity layer.
 *
 * <p>Separate from {@code EventBusMetricsEngine}, this engine focuses exclusively
 * on exchange-level infrastructure: HTTP request rates, latency histograms,
 * WebSocket reconnects, circuit breaker activity, and stale cache usage.
 *
 * <p>A snapshot may be read at any time and is safe to access from multiple threads.
 */
@Slf4j
public final class ExchangeTelemetryEngine {

    private final String exchangeName;
    private final Instant startedAt = Instant.now();

    // ─ WebSocket metrics ───────────────────────────────────────────────────
    private final AtomicLong wsReconnects = new AtomicLong(0);
    private final AtomicBoolean wsConnected = new AtomicBoolean(false);
    private final AtomicLong wsConnectedAt = new AtomicLong(0);
    private final AtomicLong wsTotalUptime = new AtomicLong(0);

    // ─ Request metrics ────────────────────────────────────────────────────
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalSuccesses = new AtomicLong(0);
    private final AtomicLong totalFailures = new AtomicLong(0);
    private final AtomicLong totalTimeouts = new AtomicLong(0);
    private final AtomicLong totalRetries = new AtomicLong(0);
    private final Map<EndpointType, AtomicLong> requestsByEndpoint = new ConcurrentHashMap<>();
    private final Map<EndpointType, AtomicLong> failuresByEndpoint = new ConcurrentHashMap<>();

    // ─ Circuit breaker metrics ─────────────────────────────────────────────
    private final AtomicLong circuitBreakerTrips = new AtomicLong(0);
    private final AtomicLong circuitBreakerRecoveries = new AtomicLong(0);

    // ─ Stale cache metrics ────────────────────────────────────────────────
    private final AtomicLong staleCacheServed = new AtomicLong(0);

    // ─ Latency tracking (EMA per endpoint) ─────────────────────────────────
    private final Map<EndpointType, Double> emaLatencyByEndpoint = new ConcurrentHashMap<>();
    private static final double ALPHA = 0.1;

    // ─ Throughput window ───────────────────────────────────────────────────
    private volatile long windowStartMs = System.currentTimeMillis();
    private final AtomicLong windowRequests = new AtomicLong(0);

    public ExchangeTelemetryEngine(@NotNull String exchangeName) {
        this.exchangeName = exchangeName;
        for (EndpointType type : EndpointType.values()) {
            requestsByEndpoint.put(type, new AtomicLong(0));
            failuresByEndpoint.put(type, new AtomicLong(0));
            emaLatencyByEndpoint.put(type, 0.0);
        }
    }

    /** Records a new outbound request for the given endpoint. */
    public void recordRequest(@NotNull EndpointType endpoint) {
        totalRequests.incrementAndGet();
        windowRequests.incrementAndGet();
        requestsByEndpoint.computeIfAbsent(endpoint, ignored -> new AtomicLong(0)).incrementAndGet();
    }

    /** Records a successful response for the given endpoint with latency. */
    public void recordSuccess(@NotNull EndpointType endpoint, long latencyMs) {
        totalSuccesses.incrementAndGet();
        updateEmaLatency(endpoint, latencyMs);
    }

    /** Records a failed response for the given endpoint with latency. */
    public void recordFailure(@NotNull EndpointType endpoint, long latencyMs) {
        totalFailures.incrementAndGet();
        failuresByEndpoint.computeIfAbsent(endpoint, ignored -> new AtomicLong(0)).incrementAndGet();
        if (latencyMs > 5_000) totalTimeouts.incrementAndGet();
    }

    /** Records a retry attempt. */
    public void recordRetry() {
        totalRetries.incrementAndGet();
    }

    /** Records a circuit breaker trip. */
    public void recordCircuitBreakerTrip() {
        circuitBreakerTrips.incrementAndGet();
    }

    /** Records a circuit breaker recovery. */
    public void recordCircuitBreakerRecovery() {
        circuitBreakerRecoveries.incrementAndGet();
    }

    /** Records a stale cache response served to a caller. */
    public void recordStaleCacheServed() {
        staleCacheServed.incrementAndGet();
    }

    /** Records a WebSocket connected event. */
    public void recordWebSocketConnected() {
        wsConnected.set(true);
        wsConnectedAt.set(System.currentTimeMillis());
    }

    /** Records a WebSocket disconnected event. */
    public void recordWebSocketDisconnected() {
        long connectedAt = wsConnectedAt.get();
        if (connectedAt > 0) {
            wsTotalUptime.addAndGet(System.currentTimeMillis() - connectedAt);
        }
        wsConnected.set(false);
        wsReconnects.incrementAndGet();
    }

    /** Returns true if WebSocket is currently connected. */
    public boolean isWebSocketConnected() {
        return wsConnected.get();
    }

    /**
     * Returns a current telemetry snapshot.
     *
     * @return immutable telemetry snapshot
     */
    public @NotNull ExchangeTelemetrySnapshot snapshot() {
        double requestsPerSec = computeRequestsPerSec();
        long totalReq = totalRequests.get();
        double failureRate = totalReq == 0 ? 0.0 : (double) totalFailures.get() / totalReq;
        double timeoutRate = totalReq == 0 ? 0.0 : (double) totalTimeouts.get() / totalReq;

        return new ExchangeTelemetrySnapshot(
                exchangeName,
                totalReq,
                totalSuccesses.get(),
                totalFailures.get(),
                totalTimeouts.get(),
                totalRetries.get(),
                requestsPerSec,
                failureRate,
                timeoutRate,
                wsReconnects.get(),
                wsConnected.get(),
                wsTotalUptime.get(),
                circuitBreakerTrips.get(),
                circuitBreakerRecoveries.get(),
                staleCacheServed.get(),
                Map.copyOf(emaLatencyByEndpoint),
                startedAt,
                Instant.now()
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private void updateEmaLatency(@NotNull EndpointType endpoint, long latencyMs) {
        emaLatencyByEndpoint.compute(endpoint, (k, prev) ->
                prev == null || prev == 0.0 ? latencyMs : ALPHA * latencyMs + (1 - ALPHA) * prev);
    }

    private double computeRequestsPerSec() {
        long now = System.currentTimeMillis();
        long elapsed = now - windowStartMs;
        if (elapsed < 1000) return windowRequests.get(); // less than 1 second
        double rps = (double) windowRequests.get() / (elapsed / 1000.0);
        // Reset window every 60 seconds
        if (elapsed > 60_000) {
            windowStartMs = now;
            windowRequests.set(0);
        }
        return rps;
    }

    /**
     * Immutable snapshot of exchange telemetry at a point in time.
     */
    public record ExchangeTelemetrySnapshot(
            String exchangeName,
            long totalRequests,
            long totalSuccesses,
            long totalFailures,
            long totalTimeouts,
            long totalRetries,
            double requestsPerSecond,
            double failureRate,
            double timeoutRate,
            long wsReconnects,
            boolean wsConnected,
            long wsTotalUptimeMs,
            long circuitBreakerTrips,
            long circuitBreakerRecoveries,
            long staleCacheServed,
            @NotNull Map<EndpointType, Double> endpointLatencyMs,
            @NotNull Instant startedAt,
            @NotNull Instant capturedAt
    ) {
        /** Returns a brief summary for logging and dashboards. */
        public String summary() {
            return "Telemetry[%s] req/s=%.1f failures=%.1f%% timeouts=%.1f%% ws-reconnects=%d stale-cache=%d circuit-trips=%d"
                    .formatted(
                            exchangeName,
                            requestsPerSecond,
                            failureRate * 100,
                            timeoutRate * 100,
                            wsReconnects,
                            staleCacheServed,
                            circuitBreakerTrips
                    );
        }
    }
}
