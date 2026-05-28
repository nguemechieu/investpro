package org.investpro.exchange.resilience;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.resilience.model.CircuitState;
import org.investpro.exchange.resilience.model.EndpointHealthSnapshot;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tracks per-endpoint health metrics for a single exchange.
 *
 * <p>Call {@link #recordSuccess(EndpointType, long)} and
 * {@link #recordFailure(EndpointType, Throwable)} from any HTTP client layer.
 * Snapshots are consumed by {@link ExchangeConnectivityManager} and
 * {@link ExchangeTelemetryEngine}.
 */
@Slf4j
public final class EndpointHealthMonitor {

    private final String exchangeName;

    /** Per-endpoint mutable health state. */
    private final Map<EndpointType, EndpointStats> stats = new ConcurrentHashMap<>();

    public EndpointHealthMonitor(@NotNull String exchangeName) {
        this.exchangeName = exchangeName;
        for (EndpointType type : EndpointType.values()) {
            stats.put(type, new EndpointStats(type));
        }
    }

    /**
     * Records a successful response for the given endpoint.
     *
     * @param endpoint    the endpoint that succeeded
     * @param latencyMs   observed latency in milliseconds
     */
    public void recordSuccess(@NotNull EndpointType endpoint, long latencyMs) {
        EndpointStats s = getStats(endpoint);
        s.totalRequests.incrementAndGet();
        s.successfulRequests.incrementAndGet();
        s.consecutiveFailures.set(0);
        s.lastSuccessAt.set(Instant.now());
        s.updateLatency(latencyMs);
        updateSuccessRatio(s);
    }

    /**
     * Records a failure for the given endpoint.
     *
     * @param endpoint the endpoint that failed
     * @param cause    the exception (used only for logging, may be {@code null})
     */
    public void recordFailure(@NotNull EndpointType endpoint, @Nullable Throwable cause) {
        EndpointStats s = getStats(endpoint);
        s.totalRequests.incrementAndGet();
        s.failedRequests.incrementAndGet();
        s.consecutiveFailures.incrementAndGet();
        s.lastFailureAt.set(Instant.now());
        updateSuccessRatio(s);

        if (cause != null) {
            boolean isTimeout = cause.getMessage() != null
                    && (cause.getMessage().contains("504")
                    || cause.getMessage().contains("timeout")
                    || cause.getMessage().contains("timed out"));
            if (isTimeout) {
                s.timeouts.incrementAndGet();
            }
        }
    }

    /** Records a retry attempt for the given endpoint. */
    public void recordRetry(@NotNull EndpointType endpoint) {
        getStats(endpoint).retries.incrementAndGet();
    }

    /** Updates the circuit state for the given endpoint. */
    public void updateCircuitState(@NotNull EndpointType endpoint, @NotNull CircuitState circuitState) {
        getStats(endpoint).circuitState.set(circuitState);
    }

    /**
     * Returns an immutable snapshot of the health for a single endpoint.
     *
     * @param endpoint the endpoint to snapshot
     * @return current health snapshot
     */
    public @NotNull EndpointHealthSnapshot snapshot(@NotNull EndpointType endpoint) {
        EndpointStats s = getStats(endpoint);
        return new EndpointHealthSnapshot(
                endpoint,
                s.circuitState.get(),
                s.totalRequests.get(),
                s.successfulRequests.get(),
                s.failedRequests.get(),
                s.timeouts.get(),
                s.retries.get(),
                s.consecutiveFailures.get(),
                s.averageLatencyMs(),
                s.successRatio.get(),
                s.lastSuccessAt.get(),
                s.lastFailureAt.get(),
                Instant.now()
        );
    }

    /**
     * Returns snapshots for all tracked endpoints.
     *
     * @return immutable map of endpoint → snapshot
     */
    public @NotNull Map<EndpointType, EndpointHealthSnapshot> snapshotAll() {
        Map<EndpointType, EndpointHealthSnapshot> result = new ConcurrentHashMap<>();
        for (EndpointType type : EndpointType.values()) {
            result.put(type, snapshot(type));
        }
        return Map.copyOf(result);
    }

    /** Returns the number of consecutive failures for the given endpoint. */
    public int getConsecutiveFailures(@NotNull EndpointType endpoint) {
        return getStats(endpoint).consecutiveFailures.get();
    }

    /** Returns the current success ratio (0.0–1.0) for the given endpoint. */
    public double getSuccessRatio(@NotNull EndpointType endpoint) {
        return getStats(endpoint).successRatio.get();
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private @NotNull EndpointStats getStats(@NotNull EndpointType endpoint) {
        return stats.computeIfAbsent(endpoint, EndpointStats::new);
    }

    private void updateSuccessRatio(@NotNull EndpointStats s) {
        long total = s.totalRequests.get();
        if (total == 0) return;
        double ratio = (double) s.successfulRequests.get() / total;
        s.successRatio.set(ratio);
    }

    /**
     * Mutable per-endpoint health statistics.
     * Internal to {@code EndpointHealthMonitor}; never exposed directly.
     */
    private static final class EndpointStats {

        final EndpointType type;
        final AtomicLong totalRequests = new AtomicLong(0);
        final AtomicLong successfulRequests = new AtomicLong(0);
        final AtomicLong failedRequests = new AtomicLong(0);
        final AtomicLong timeouts = new AtomicLong(0);
        final AtomicLong retries = new AtomicLong(0);
        final AtomicInteger consecutiveFailures = new AtomicInteger(0);
        final AtomicReference<CircuitState> circuitState = new AtomicReference<>(CircuitState.CLOSED);
        final AtomicReference<Instant> lastSuccessAt = new AtomicReference<>(null);
        final AtomicReference<Instant> lastFailureAt = new AtomicReference<>(null);
        final AtomicReference<Double> successRatio = new AtomicReference<>(1.0);

        // Exponential moving average for latency
        private volatile double emaLatencyMs = 0.0;
        private static final double ALPHA = 0.1;

        EndpointStats(@NotNull EndpointType type) {
            this.type = type;
        }

        synchronized void updateLatency(long latencyMs) {
            if (emaLatencyMs == 0.0) {
                emaLatencyMs = latencyMs;
            } else {
                emaLatencyMs = ALPHA * latencyMs + (1 - ALPHA) * emaLatencyMs;
            }
        }

        synchronized double averageLatencyMs() {
            return emaLatencyMs;
        }
    }
}
