package org.investpro.telemetry;

import java.time.Instant;
import java.util.Map;

/**
 * Compact runtime telemetry snapshot for UI, logs, and future Prometheus export.
 */
public record RuntimeMetricSnapshot(
        double processCpuLoad,
        double systemCpuLoad,
        long heapUsedBytes,
        long heapMaxBytes,
        long nonHeapUsedBytes,
        long gcCollections,
        long gcCollectionMillis,
        Map<String, Number> counters,
        Instant capturedAt) {

    public RuntimeMetricSnapshot {
        counters = counters == null ? Map.of() : Map.copyOf(counters);
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }

    public double heapUtilization() {
        return heapMaxBytes <= 0 ? 0.0 : (double) heapUsedBytes / heapMaxBytes;
    }
}
