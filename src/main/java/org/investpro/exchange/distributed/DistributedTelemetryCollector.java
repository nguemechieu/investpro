package org.investpro.exchange.distributed;

import org.investpro.exchange.resilience.ExchangeTelemetryEngine;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for collecting and aggregating telemetry from distributed
 * exchange workers.
 *
 * <p>In a single-node deployment, telemetry is handled directly by
 * {@link ExchangeTelemetryEngine}.  In a distributed deployment, each worker
 * node produces its own telemetry stream; this collector aggregates those streams
 * into a unified view for dashboards, alerting, and capacity planning.
 *
 * <p>The collector is designed to be non-blocking: {@link #collectTelemetry} is
 * fire-and-forget, while {@link #getAggregatedMetrics} returns a future to avoid
 * blocking the calling thread on network I/O.
 *
 * <p><b>Design-only interface — no production implementation exists yet.</b>
 *
 * @see ExchangeTelemetryEngine
 * @see RemoteExchangeWorker
 */
public interface DistributedTelemetryCollector {

    /**
     * Accepts a telemetry snapshot from a single exchange worker and stores it
     * for aggregation.  This method is fire-and-forget; callers should not block
     * on its completion.
     *
     * @param exchangeName the name of the exchange that produced the snapshot
     * @param snapshot     the telemetry snapshot to collect
     */
    void collectTelemetry(
            String exchangeName,
            ExchangeTelemetryEngine.ExchangeTelemetrySnapshot snapshot
    );

    /**
     * Returns aggregated metrics across all collected telemetry snapshots.
     *
     * <p>The returned map is a generic {@code Map<String, Object>} to allow
     * flexibility.  Expected keys include:
     * <ul>
     *   <li>{@code "totalRequests"} — sum of all requests across workers</li>
     *   <li>{@code "totalFailures"} — sum of failures across workers</li>
     *   <li>{@code "circuitBreakerTrips"} — total circuit breaker trips</li>
     *   <li>{@code "staleCacheServed"} — total stale cache responses served</li>
     *   <li>{@code "avgLatencyMs"} — weighted average latency</li>
     *   <li>{@code "activeWorkers"} — number of workers that have reported recently</li>
     * </ul>
     *
     * @return a future resolving to the aggregated metrics map
     */
    CompletableFuture<Map<String, Object>> getAggregatedMetrics();

    /**
     * Flushes any buffered telemetry to the backing store (e.g., time-series DB,
     * message bus).  Should be called periodically or on shutdown.
     */
    void flush();
}
