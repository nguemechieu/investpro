package org.investpro.exchange.runtime;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;

/**
 * Immutable snapshot of an exchange's runtime metadata at a point in time.
 *
 * <p>Captures connectivity, latency, and freshness information alongside
 * the current {@link ExchangeRuntimeState}. Produced by
 * {@link ExchangeRuntimeManager} and consumed by monitoring dashboards,
 * the health engine, and telemetry sinks.
 *
 * @param exchangeName      canonical exchange identifier
 * @param state             current runtime state
 * @param lastHeartbeat     timestamp of the last successful heartbeat (null if never received)
 * @param websocketConnected whether the WebSocket stream is currently established
 * @param staleDuration     time since the last fresh data point (null if data is current)
 * @param latencyMs         most recently measured round-trip latency in milliseconds
 * @param reconnectCount    total number of reconnect attempts since runtime start
 * @param lastStateChange   timestamp of the most recent state transition
 * @param lastAuthCheckTime timestamp of the most recent authentication check
 * @param authSuccessful    result of the most recent authentication check
 * @param capturedAt        timestamp when this snapshot was created
 */
public record ExchangeRuntimeMetadata(
        @NotNull String exchangeName,
        @NotNull ExchangeRuntimeState state,
        @Nullable Instant lastHeartbeat,
        boolean websocketConnected,
        @Nullable Duration staleDuration,
        long latencyMs,
        long reconnectCount,
        @NotNull Instant lastStateChange,
        @Nullable Instant lastAuthCheckTime,
        boolean authSuccessful,
        @NotNull Instant capturedAt
) {

    /** Convenience factory for a healthy runtime state. */
    public static @NotNull ExchangeRuntimeMetadata connected(
            @NotNull String exchangeName,
            long latencyMs,
            long reconnectCount
    ) {
        Instant now = Instant.now();
        return new ExchangeRuntimeMetadata(
                exchangeName, ExchangeRuntimeState.CONNECTED, now, true,
                null, latencyMs, reconnectCount, now, now, true, now);
    }

    /** Convenience factory for a disconnected state. */
    public static @NotNull ExchangeRuntimeMetadata disconnected(
            @NotNull String exchangeName,
            long reconnectCount
    ) {
        Instant now = Instant.now();
        return new ExchangeRuntimeMetadata(
                exchangeName, ExchangeRuntimeState.DISCONNECTED, null, false,
                null, -1L, reconnectCount, now, null, false, now);
    }

    /** Returns true if the heartbeat was received within the given duration. */
    public boolean isHeartbeatFresh(@NotNull Duration maxAge) {
        if (lastHeartbeat == null) return false;
        return Duration.between(lastHeartbeat, Instant.now()).compareTo(maxAge) <= 0;
    }

    /** Returns a brief diagnostic string. */
    public @NotNull String summary() {
        return "RuntimeMeta[%s] state=%s ws=%s latency=%dms reconnects=%d stale=%s"
                .formatted(exchangeName, state, websocketConnected, latencyMs,
                        reconnectCount, staleDuration != null ? staleDuration : "none");
    }
}
