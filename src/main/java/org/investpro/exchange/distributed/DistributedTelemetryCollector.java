package org.investpro.exchange.distributed;

import org.investpro.exchange.resilience.ExchangeTelemetryEngine;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for collecting and aggregating telemetry from distributed
 * exchange workers.
 *
 * <p><b>Design-only interface — no production implementation exists yet.</b>
 */
public interface DistributedTelemetryCollector {

    void collectTelemetry(
            String exchangeName,
            ExchangeTelemetryEngine.ExchangeTelemetrySnapshot snapshot
    );

    CompletableFuture<Map<String, Object>> getAggregatedMetrics();

    void flush();
}
