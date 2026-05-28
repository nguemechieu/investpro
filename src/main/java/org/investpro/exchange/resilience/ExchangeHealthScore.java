package org.investpro.exchange.resilience;

import org.investpro.exchange.resilience.model.*;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Computes a composite health score (0.0–1.0) for the exchange connectivity layer.
 *
 * <p>Scores are derived from {@link EndpointHealthSnapshot} data and
 * {@link ExchangeTelemetryEngine.ExchangeTelemetrySnapshot} metrics.
 * The result is classified into a {@link ExchangeHealthGrade}:
 *
 * <pre>
 *   GREEN  ≥ 0.85 — fully operational
 *   YELLOW ≥ 0.65 — non-critical degradation
 *   ORANGE ≥ 0.40 — significant degradation
 *   RED    &lt; 0.40 — critical failure
 * </pre>
 *
 * <p>Scoring model:
 * <ul>
 *   <li>Endpoint health (40%): mean success ratio of all critical endpoints.</li>
 *   <li>Execution health (25%): success ratio of EXECUTION endpoint specifically.</li>
 *   <li>Latency score (15%): penalty for high average latency (&gt;500ms = 0).</li>
 *   <li>Reliability score (15%): penalty for circuit breaker trips and retries.</li>
 *   <li>WebSocket health (5%): bonus if WebSocket is live.</li>
 * </ul>
 */
public record ExchangeHealthScore(
        double websocketHealth,
        double endpointHealth,
        double executionHealth,
        double latencyScore,
        double reliabilityScore,
        double composite,
        @NotNull ExchangeHealthGrade grade
) {

    /**
     * Computes a health score from live snapshots and telemetry.
     *
     * @param snapshots per-endpoint health snapshots
     * @param telemetry current telemetry snapshot
     * @return computed health score
     */
    public static @NotNull ExchangeHealthScore compute(
            @NotNull Map<EndpointType, EndpointHealthSnapshot> snapshots,
            @NotNull ExchangeTelemetryEngine.ExchangeTelemetrySnapshot telemetry
    ) {
        double websocketHealth = telemetry.wsConnected() ? 1.0 : 0.0;

        // Endpoint health: mean success ratio of critical endpoints
        double endpointHealthSum = 0.0;
        int criticalCount = 0;
        for (EndpointType type : EndpointType.values()) {
            if (!type.critical) continue;
            EndpointHealthSnapshot snap = snapshots.get(type);
            if (snap != null) {
                // Circuit open = 0; otherwise use success ratio
                endpointHealthSum += (snap.circuitState() == CircuitState.OPEN) ? 0.0 : snap.successRatio();
                criticalCount++;
            }
        }
        double endpointHealth = criticalCount == 0 ? 1.0 : endpointHealthSum / criticalCount;

        // Execution health: success ratio of the EXECUTION endpoint specifically
        EndpointHealthSnapshot execSnap = snapshots.get(EndpointType.EXECUTION);
        double executionHealth = execSnap == null ? 1.0 :
                (execSnap.circuitState() == CircuitState.OPEN ? 0.0 : execSnap.successRatio());

        // Latency score: penalty above 500ms, capped at 2000ms
        double maxLatencyMs = 2000.0;
        double targetLatencyMs = 500.0;
        double avgLatency = snapshots.values().stream()
                .mapToDouble(EndpointHealthSnapshot::averageLatencyMs)
                .average()
                .orElse(0.0);
        double latencyScore = avgLatency <= targetLatencyMs ? 1.0
                : Math.max(0.0, 1.0 - (avgLatency - targetLatencyMs) / (maxLatencyMs - targetLatencyMs));

        // Reliability score: penalty for circuit breaker trips and timeouts
        double failureRate = telemetry.failureRate();
        double timeoutRate = telemetry.timeoutRate();
        double reliabilityScore = Math.max(0.0, 1.0 - (failureRate * 0.6) - (timeoutRate * 0.4));

        // Composite score (weighted sum)
        double composite = 0.40 * endpointHealth
                + 0.25 * executionHealth
                + 0.15 * latencyScore
                + 0.15 * reliabilityScore
                + 0.05 * websocketHealth;

        composite = Math.max(0.0, Math.min(1.0, composite));

        return new ExchangeHealthScore(
                websocketHealth,
                endpointHealth,
                executionHealth,
                latencyScore,
                reliabilityScore,
                composite,
                ExchangeHealthGrade.fromScore(composite)
        );
    }

    /** Returns a brief string summary of the health score. */
    public String summary() {
        return "HealthScore[%s] composite=%.2f ws=%.2f endpoint=%.2f exec=%.2f latency=%.2f reliability=%.2f"
                .formatted(
                        grade.name(),
                        composite,
                        websocketHealth,
                        endpointHealth,
                        executionHealth,
                        latencyScore,
                        reliabilityScore
                );
    }
}
