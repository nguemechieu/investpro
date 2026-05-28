package org.investpro.exchange.resilience.model;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Full composite health report for the exchange connectivity layer.
 *
 * <p>Aggregates per-endpoint snapshots into a single actionable summary
 * including a numeric health score and a {@link ExchangeHealthGrade}.
 */
public record ExchangeHealthReport(
        @NotNull String exchangeName,
        @NotNull ExchangeConnectivityState connectivityState,
        @NotNull ExchangeHealthGrade grade,
        double compositeScore,
        double websocketHealth,
        double endpointHealth,
        double executionHealth,
        double latencyScore,
        double reliabilityScore,
        boolean websocketConnected,
        int openCircuits,
        int degradedEndpoints,
        @NotNull Map<EndpointType, EndpointHealthSnapshot> endpointSnapshots,
        @NotNull Instant generatedAt
) {

    public ExchangeHealthReport {
        Objects.requireNonNull(exchangeName, "exchangeName must not be null");
        Objects.requireNonNull(connectivityState, "connectivityState must not be null");
        Objects.requireNonNull(grade, "grade must not be null");
        Objects.requireNonNull(endpointSnapshots, "endpointSnapshots must not be null");
        Objects.requireNonNull(generatedAt, "generatedAt must not be null");
        endpointSnapshots = Map.copyOf(endpointSnapshots);
    }

    /** Returns {@code true} if the exchange is safe for live execution. */
    public boolean isExecutionSafe() {
        return connectivityState.isOperational()
                && grade.isOperational()
                && executionHealth >= 0.80;
    }

    /** Returns a compact summary string for logging and dashboards. */
    public String summary() {
        return "ExchangeHealth[%s] grade=%s score=%.2f state=%s ws=%s circuits-open=%d degraded=%d"
                .formatted(
                        exchangeName,
                        grade.name(),
                        compositeScore,
                        connectivityState.name(),
                        websocketConnected ? "UP" : "DOWN",
                        openCircuits,
                        degradedEndpoints
                );
    }
}
