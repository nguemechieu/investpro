package org.investpro.telemetry;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics sidecar for event-driven components. It can be called by any bus or
 * adapter without introducing a hard dependency on a specific event bus.
 */
public final class EventBusMetricsEngine {
    private static final EventBusMetricsEngine INSTANCE = new EventBusMetricsEngine();

    private final LongAdder total = new LongAdder();
    private final LongAdder slow = new LongAdder();
    private final LongAdder dropped = new LongAdder();
    private final LongAdder retry = new LongAdder();
    private final LongAdder deadLetter = new LongAdder();
    private final Map<String, LongAdder> byType = new ConcurrentHashMap<>();
    private final long startedNanos = System.nanoTime();

    private EventBusMetricsEngine() {
    }

    public static EventBusMetricsEngine getInstance() {
        return INSTANCE;
    }

    public void recordPublished(String eventType) {
        total.increment();
        byType.computeIfAbsent(eventType == null ? "UNKNOWN" : eventType, ignored -> new LongAdder()).increment();
    }

    public void recordConsumerLatency(String eventType, long latencyNanos) {
        if (latencyNanos > 50_000_000L) {
            slow.increment();
        }
    }

    public void recordDropped() {
        dropped.increment();
    }

    public void recordRetry() {
        retry.increment();
    }

    public void recordDeadLetter() {
        deadLetter.increment();
    }

    public EventBusMetricsSnapshot snapshot() {
        Map<String, Long> copy = new LinkedHashMap<>();
        byType.forEach((key, value) -> copy.put(key, value.sum()));
        double seconds = Math.max(0.001, (System.nanoTime() - startedNanos) / 1_000_000_000.0);
        return new EventBusMetricsSnapshot(total.sum(), total.sum() / seconds, slow.sum(), dropped.sum(),
                retry.sum(), deadLetter.sum(), copy, Instant.now());
    }
}
