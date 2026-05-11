package org.investpro.operations;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents a system activity event for operations monitoring.
 * Tracks important lifecycle events across the application.
 */
@Data
@Builder
@AllArgsConstructor
public class SystemActivityEvent {

    public enum Component {
        SYSTEM, EXCHANGE, TRADING_ENGINE, RISK_MANAGER, SIGNAL_PROCESSOR,
        STRATEGY_ENGINE, EXECUTION_ENGINE, WEBSOCKET, REST_API, DATABASE,
        AUTHENTICATION, BOT, SCHEDULER, UNKNOWN
    }

    public enum Severity {
        INFO, WARN, ERROR, CRITICAL
    }

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("event_id")
    private final String eventId;

    @JsonProperty("component")
    private final Component component;

    @JsonProperty("severity")
    private final Severity severity;

    @JsonProperty("event_type")
    private final String eventType;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("correlation_id")
    @Nullable
    private final String correlationId;

    @JsonProperty("metadata")
    private final Map<String, String> metadata;

    public SystemActivityEvent(Component component, Severity severity, String eventType, String message) {
        this.timestamp = Instant.now();
        this.eventId = UUID.randomUUID().toString();
        this.component = component;
        this.severity = severity;
        this.eventType = eventType;
        this.message = message;
        this.correlationId = null;
        this.metadata = new HashMap<>();
    }

    public SystemActivityEvent(Component component, Severity severity, String eventType, String message,
            @Nullable String correlationId) {
        this.timestamp = Instant.now();
        this.eventId = UUID.randomUUID().toString();
        this.component = component;
        this.severity = severity;
        this.eventType = eventType;
        this.message = message;
        this.correlationId = correlationId;
        this.metadata = new HashMap<>();
    }

    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    public void addMetadata(String key, String value) {
        metadata.put(key, value);
    }

    public String getMetadata(String key) {
        return metadata.getOrDefault(key, "");
    }

    @Override
    public String toString() {
        return "[%s] %s | %s | %s: %s".formatted(
                timestamp,
                severity,
                component,
                eventType,
                message);
    }
}
