package org.investpro.telemetry;

import java.time.Instant;
import java.util.Map;

public record EventBusMetricsSnapshot(
        long totalEvents,
        double eventsPerSecond,
        long slowConsumerEvents,
        long droppedEvents,
        long retryEvents,
        long deadLetterEvents,
        Map<String, Long> eventsByType,
        Instant capturedAt) {

    public EventBusMetricsSnapshot {
        eventsByType = eventsByType == null ? Map.of() : Map.copyOf(eventsByType);
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }
}
