package org.investpro.exchange.runtime;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.ExchangeHealthScore;
import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.investpro.exchange.resilience.model.EndpointHealthSnapshot;
import org.investpro.exchange.resilience.model.EndpointType;
import org.investpro.exchange.resilience.model.ExchangeHealthGrade;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Central lifecycle coordinator for all registered exchange runtime contexts.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Maintain per-exchange {@link ExchangeRuntimeState} transitions</li>
 *   <li>Track runtime metadata (heartbeats, latency, reconnect counts)</li>
 *   <li>Drive periodic health evaluation via {@link ExchangeHealthScore}</li>
 *   <li>Emit lifecycle events to the {@link AgentEventBus}</li>
 *   <li>Provide snapshot access for monitoring dashboards</li>
 * </ul>
 *
 * <p>Thread safety: all state is guarded by {@link ConcurrentHashMap} and
 * {@link AtomicReference}. Methods are safe to call from any thread.
 */
@Slf4j
public final class ExchangeRuntimeManager {

    /** Default health evaluation interval. */
    private static final Duration HEALTH_CHECK_INTERVAL = Duration.ofSeconds(30);
    /** Heartbeat age beyond which state transitions to STALE. */
    private static final Duration STALE_THRESHOLD = Duration.ofSeconds(90);

    private final Map<String, AtomicReference<ExchangeRuntimeState>> states = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<ExchangeRuntimeMetadata>> metadata = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> reconnectCounts = new ConcurrentHashMap<>();
    private final Map<String, ExchangeTelemetryEngine> telemetryEngines = new ConcurrentHashMap<>();
    private final Map<String, Map<EndpointType, EndpointHealthSnapshot>> endpointSnapshots = new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;
    private final ScheduledExecutorService scheduler;
    private ScheduledFuture<?> healthCheckTask;
    private volatile boolean running = false;

    public ExchangeRuntimeManager(@Nullable AgentEventBus eventBus) {
        this.eventBus = eventBus;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "exchange-runtime-manager");
            t.setDaemon(true);
            return t;
        });
    }

    /** Registers an exchange with the runtime manager, starting in DISCONNECTED state. */
    public synchronized void register(@NotNull String exchangeName) {
        if (states.containsKey(exchangeName)) {
            log.debug("Exchange '{}' already registered with runtime manager", exchangeName);
            return;
        }
        states.put(exchangeName, new AtomicReference<>(ExchangeRuntimeState.DISCONNECTED));
        reconnectCounts.put(exchangeName, new AtomicLong(0));
        ExchangeRuntimeMetadata initialMeta = ExchangeRuntimeMetadata.disconnected(exchangeName, 0);
        metadata.put(exchangeName, new AtomicReference<>(initialMeta));
        log.info("Registered exchange '{}' with runtime manager", exchangeName);
    }

    public synchronized void register(
            @NotNull String exchangeName,
            @NotNull org.investpro.exchange.models.ExchangeCapability capability
    ) {
        register(exchangeName);
    }

    /** Unregisters an exchange from the runtime manager. */
    public synchronized void unregister(@NotNull String exchangeName) {
        states.remove(exchangeName);
        metadata.remove(exchangeName);
        reconnectCounts.remove(exchangeName);
        telemetryEngines.remove(exchangeName);
        endpointSnapshots.remove(exchangeName);
        log.info("Unregistered exchange '{}' from runtime manager", exchangeName);
    }

    /** Associates a telemetry engine for health score computation. */
    public void registerTelemetry(
            @NotNull String exchangeName,
            @NotNull ExchangeTelemetryEngine telemetry
    ) {
        telemetryEngines.put(exchangeName, telemetry);
    }

    /** Updates endpoint health snapshots for health score computation. */
    public void updateEndpointSnapshots(
            @NotNull String exchangeName,
            @NotNull Map<EndpointType, EndpointHealthSnapshot> snapshots
    ) {
        endpointSnapshots.put(exchangeName, new ConcurrentHashMap<>(snapshots));
    }

    /** Transitions the exchange to a new runtime state, emitting an event if the state changed. */
    public void transitionState(
            @NotNull String exchangeName,
            @NotNull ExchangeRuntimeState newState
    ) {
        AtomicReference<ExchangeRuntimeState> stateRef = states.get(exchangeName);
        if (stateRef == null) {
            log.warn("Attempted state transition for unregistered exchange '{}'", exchangeName);
            return;
        }
        ExchangeRuntimeState previousState = stateRef.getAndSet(newState);
        if (previousState == newState) return;

        log.info("Exchange '{}' state: {} \u2192 {}", exchangeName, previousState, newState);
        refreshMetadata(exchangeName, newState);
        publishStateEvent(exchangeName, previousState, newState);
    }

    /** Records a successful heartbeat for the given exchange. */
    public void recordHeartbeat(@NotNull String exchangeName) {
        recordHeartbeat(exchangeName, 0L);
    }

    /** Records a successful heartbeat for the given exchange. */
    public void recordHeartbeat(@NotNull String exchangeName, long latencyMs) {
        AtomicReference<ExchangeRuntimeMetadata> metaRef = metadata.get(exchangeName);
        if (metaRef == null) return;
        ExchangeRuntimeMetadata current = metaRef.get();
        ExchangeRuntimeState currentState = getState(exchangeName);
        Instant now = Instant.now();
        metaRef.set(new ExchangeRuntimeMetadata(
                exchangeName, currentState, now, current.websocketConnected(),
                null, latencyMs, current.reconnectCount(), current.lastStateChange(),
                current.lastAuthCheckTime(), current.authSuccessful(), now));
    }

    /** Records a WebSocket connection or disconnection event. */
    public void recordWebSocketState(@NotNull String exchangeName, boolean connected) {
        AtomicReference<ExchangeRuntimeMetadata> metaRef = metadata.get(exchangeName);
        if (metaRef == null) return;
        ExchangeRuntimeMetadata current = metaRef.get();
        metaRef.set(new ExchangeRuntimeMetadata(
                exchangeName, current.state(), current.lastHeartbeat(), connected,
                current.staleDuration(), current.latencyMs(), current.reconnectCount(),
                current.lastStateChange(), current.lastAuthCheckTime(), current.authSuccessful(),
                Instant.now()));
        if (!connected) {
            long count = reconnectCounts.getOrDefault(exchangeName, new AtomicLong(0)).incrementAndGet();
            log.debug("Exchange '{}' WebSocket disconnected (reconnect count: {})", exchangeName, count);
            if (current.state() == ExchangeRuntimeState.CONNECTED) {
                transitionState(exchangeName, ExchangeRuntimeState.DEGRADED);
            }
        }
    }

    /** Records an authentication result. */
    public void recordAuthResult(@NotNull String exchangeName, boolean successful) {
        AtomicReference<ExchangeRuntimeMetadata> metaRef = metadata.get(exchangeName);
        if (metaRef == null) return;
        ExchangeRuntimeMetadata current = metaRef.get();
        Instant now = Instant.now();
        metaRef.set(new ExchangeRuntimeMetadata(
                exchangeName, current.state(), current.lastHeartbeat(), current.websocketConnected(),
                current.staleDuration(), current.latencyMs(), current.reconnectCount(),
                current.lastStateChange(), now, successful, now));
        if (!successful) {
            transitionState(exchangeName, ExchangeRuntimeState.AUTH_FAILED);
        } else if (getState(exchangeName) == ExchangeRuntimeState.AUTH_FAILED) {
            transitionState(exchangeName, ExchangeRuntimeState.DISCONNECTED);
        }
    }

    /** Returns the current runtime state for the given exchange. */
    public @NotNull ExchangeRuntimeState getState(@NotNull String exchangeName) {
        AtomicReference<ExchangeRuntimeState> ref = states.get(exchangeName);
        return ref == null ? ExchangeRuntimeState.DISCONNECTED : ref.get();
    }

    public @NotNull Optional<ExchangeRuntimeState> getStateOptional(@NotNull String exchangeName) {
        AtomicReference<ExchangeRuntimeState> ref = states.get(exchangeName);
        return ref == null ? Optional.empty() : Optional.of(ref.get());
    }

    /** Returns the latest runtime metadata snapshot for the given exchange. */
    public @Nullable ExchangeRuntimeMetadata getMetadata(@NotNull String exchangeName) {
        AtomicReference<ExchangeRuntimeMetadata> ref = metadata.get(exchangeName);
        return ref == null ? null : ref.get();
    }

    public @NotNull Optional<ExchangeRuntimeMetadata> getMetadataOptional(@NotNull String exchangeName) {
        AtomicReference<ExchangeRuntimeMetadata> ref = metadata.get(exchangeName);
        return ref == null ? Optional.empty() : Optional.of(ref.get());
    }

    /** Returns all registered exchange names. */
    public @NotNull List<String> getRegisteredExchanges() {
        return List.copyOf(states.keySet());
    }

    /** Returns a snapshot of all runtime metadata, keyed by exchange name. */
    public @NotNull Map<String, ExchangeRuntimeMetadata> getAllMetadata() {
        return metadata.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get()));
    }

    /** Computes and returns the health score for the given exchange. */
    public @NotNull Optional<ExchangeHealthScore> computeHealthScore(@NotNull String exchangeName) {
        ExchangeTelemetryEngine telemetry = telemetryEngines.get(exchangeName);
        Map<EndpointType, EndpointHealthSnapshot> snapshots = endpointSnapshots.get(exchangeName);
        if (telemetry == null || snapshots == null || snapshots.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(ExchangeHealthScore.compute(snapshots, telemetry.snapshot()));
    }

    /** Starts the periodic health evaluation loop. */
    public synchronized void start() {
        if (running) return;
        running = true;
        healthCheckTask = scheduler.scheduleAtFixedRate(
                this::runHealthCycle,
                HEALTH_CHECK_INTERVAL.toSeconds(),
                HEALTH_CHECK_INTERVAL.toSeconds(),
                TimeUnit.SECONDS
        );
        log.info("ExchangeRuntimeManager started (health check interval: {})", HEALTH_CHECK_INTERVAL);
    }

    /** Stops the runtime manager and shuts down the background scheduler. */
    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (healthCheckTask != null) healthCheckTask.cancel(false);
        scheduler.shutdown();
        log.info("ExchangeRuntimeManager stopped");
    }

    // ─ Private helpers ──────────────────────────────────────────────

    private void runHealthCycle() {
        for (String exchangeName : states.keySet()) {
            try {
                evaluateStaleState(exchangeName);
                computeAndLogHealthScore(exchangeName);
            } catch (Exception e) {
                log.warn("Health cycle error for exchange '{}': {}", exchangeName, e.getMessage());
            }
        }
    }

    private void evaluateStaleState(@NotNull String exchangeName) {
        AtomicReference<ExchangeRuntimeMetadata> metaRef = metadata.get(exchangeName);
        if (metaRef == null) return;
        ExchangeRuntimeMetadata meta = metaRef.get();
        if (meta.state() != ExchangeRuntimeState.CONNECTED &&
                meta.state() != ExchangeRuntimeState.DEGRADED) return;
        if (!meta.isHeartbeatFresh(STALE_THRESHOLD)) {
            transitionState(exchangeName, ExchangeRuntimeState.STALE);
        }
    }

    private void computeAndLogHealthScore(@NotNull String exchangeName) {
        computeHealthScore(exchangeName).ifPresent(score -> {
            if (score.grade() == ExchangeHealthGrade.RED) {
                log.warn("Exchange '{}' health: {}", exchangeName, score.summary());
            } else {
                log.debug("Exchange '{}' health: {}", exchangeName, score.summary());
            }
        });
    }

    private void refreshMetadata(@NotNull String exchangeName, @NotNull ExchangeRuntimeState newState) {
        AtomicReference<ExchangeRuntimeMetadata> metaRef = metadata.get(exchangeName);
        if (metaRef == null) return;
        ExchangeRuntimeMetadata current = metaRef.get();
        Instant now = Instant.now();
        metaRef.set(new ExchangeRuntimeMetadata(
                exchangeName, newState, current.lastHeartbeat(), current.websocketConnected(),
                current.staleDuration(), current.latencyMs(), current.reconnectCount(),
                now, current.lastAuthCheckTime(), current.authSuccessful(), now));
    }

    private void publishStateEvent(
            @NotNull String exchangeName,
            @NotNull ExchangeRuntimeState previous,
            @NotNull ExchangeRuntimeState next
    ) {
        if (eventBus == null) return;
        String eventType = switch (next) {
            case CONNECTED     -> AgentEvent.EXCHANGE_CONNECTED;
            case DISCONNECTED  -> AgentEvent.EXCHANGE_DISCONNECTED;
            case DEGRADED      -> AgentEvent.EXCHANGE_DEGRADED;
            case AUTH_FAILED   -> AgentEvent.EXCHANGE_AUTH_FAILED;
            case STALE         -> AgentEvent.WEBSOCKET_STALE;
            case CIRCUIT_OPEN  -> AgentEvent.CIRCUIT_OPENED;
            default            -> AgentEvent.EXCHANGE_HEALTH_CHANGED;
        };
        Map<String, Object> eventMeta = Map.of(
                "exchange", exchangeName,
                "previousState", previous.name(),
                "newState", next.name()
        );
        eventBus.publishAsync(new AgentEvent(
                eventType, "ExchangeRuntimeManager", exchangeName,
                Instant.now(), eventMeta));
    }
}
