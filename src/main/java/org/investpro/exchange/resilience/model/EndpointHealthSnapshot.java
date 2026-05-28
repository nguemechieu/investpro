package org.investpro.exchange.resilience.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable point-in-time health snapshot for a single exchange endpoint.
 *
 * <p>Captured by {@code EndpointHealthMonitor} and surfaced via
 * {@code ExchangeConnectivityManager} and {@code ExchangeOperationsBoard}.
 */
public record EndpointHealthSnapshot(
        @NotNull EndpointType endpoint,
        @NotNull CircuitState circuitState,
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        long timeouts,
        long retries,
        int consecutiveFailures,
        double averageLatencyMs,
        double successRatio,
        @Nullable Instant lastSuccessAt,
        @Nullable Instant lastFailureAt,
        @NotNull Instant capturedAt
) {

    public EndpointHealthSnapshot {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        Objects.requireNonNull(circuitState, "circuitState must not be null");
        Objects.requireNonNull(capturedAt, "capturedAt must not be null");
    }

    /** Returns {@code true} if this endpoint is considered healthy. */
    public boolean isHealthy() {
        return circuitState == CircuitState.CLOSED
                && consecutiveFailures == 0
                && successRatio >= 0.95;
    }

    /** Returns {@code true} if this endpoint is degraded but usable. */
    public boolean isDegraded() {
        return !isHealthy() && circuitState != CircuitState.OPEN;
    }

    /** Returns a brief human-readable status string. */
    public String statusSummary() {
        return "%s | circuit=%s | successRatio=%.1f%% | avgLatency=%.0fms | consec-failures=%d"
                .formatted(
                        endpoint.name(),
                        circuitState.name(),
                        successRatio * 100,
                        averageLatencyMs,
                        consecutiveFailures
                );
    }
}
