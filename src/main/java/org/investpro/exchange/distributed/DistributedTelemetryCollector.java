package org.investpro.exchange.distributed;

import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for distributed telemetry collection.
 *
 * <p>In a multi-node deployment, each node publishes its telemetry snapshot
 * to a shared time-series store or aggregator. This interface abstracts the
 * publishing contract so local and remote nodes use the same API.
 *
 * <p><b>Implementation deferred</b>: time-series backend integration (Prometheus,
 * InfluxDB, Azure Monitor) is not yet implemented.
 */
public interface DistributedTelemetryCollector {

    /** Returns the collector endpoint identifier. */
    @NotNull String collectorId();

    /**
     * Publishes a telemetry snapshot to the distributed collector.
     *
     * @param exchangeName exchange that the snapshot belongs to
     * @param snapshot     the telemetry snapshot to publish
     * @return future that completes when the snapshot is acknowledged
     */
    CompletableFuture<Void> publish(
            @NotNull String exchangeName,
            @NotNull ExchangeTelemetryEngine.ExchangeTelemetrySnapshot snapshot
    );

    /**
     * Queries the last N snapshots for a given exchange from the distributed store.
     *
     * @param exchangeName  exchange to query
     * @param count         number of recent snapshots to retrieve
     * @return future resolving to a list of raw snapshot objects
     */
    CompletableFuture<java.util.List<Object>> queryRecent(
            @NotNull String exchangeName,
            int count
    );
}
