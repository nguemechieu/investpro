package org.investpro.exchange.resilience;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.model.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Central coordinator for exchange connectivity, circuit breakers, and health state.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Owns one {@link ExchangeCircuitBreaker} per endpoint.</li>
 *   <li>Aggregates endpoint health into an exchange-level {@link ExchangeConnectivityState}.</li>
 *   <li>Emits {@link AgentEvent} connectivity events to the {@link AgentEventBus}.</li>
 *   <li>Provides callers with pre-checked route methods that enforce isolation between
 *       critical and non-critical endpoint failures.</li>
 * </ul>
 *
 * <p>Non-critical endpoint failures (order history, analytics) are contained
 * within their own circuit breakers and never propagate to pricing or execution.
 */
@Slf4j
public final class ExchangeConnectivityManager {

    private final String exchangeName;
    @Nullable
    private final AgentEventBus eventBus;
    @Getter
    private final EndpointHealthMonitor healthMonitor;
    @Getter
    private final ExchangeTelemetryEngine telemetry;

    private final Map<EndpointType, ExchangeCircuitBreaker> circuitBreakers = new ConcurrentHashMap<>();
    private final AtomicReference<ExchangeConnectivityState> connectivityState =
            new AtomicReference<>(ExchangeConnectivityState.DISCONNECTED);

    // Initialized in constructor after exchangeName is assigned
    private final ScheduledExecutorService healthEvaluator;

    public ExchangeConnectivityManager(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus
    ) {
        this.exchangeName = exchangeName;
        this.eventBus = eventBus;
        this.healthMonitor = new EndpointHealthMonitor(exchangeName);
        this.telemetry = new ExchangeTelemetryEngine(exchangeName);

        // Initialize after exchangeName is set so the thread name can reference it
        this.healthEvaluator = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "exchange-health-evaluator-" + exchangeName);
            t.setDaemon(true);
            return t;
        });

        // Create one circuit breaker per endpoint
        for (EndpointType type : EndpointType.values()) {
            circuitBreakers.put(type, new ExchangeCircuitBreaker(exchangeName, type, eventBus));
        }

        // Periodically re-evaluate connectivity state
        healthEvaluator.scheduleAtFixedRate(this::evaluateConnectivityState, 5, 5, TimeUnit.SECONDS);
    }

    /**
     * Executes a request through the circuit breaker for the given endpoint.
     *
     * <p>Non-critical endpoint failures never block critical endpoints.
     * Callers should handle {@link ExchangeCircuitBreaker.CircuitOpenException}
     * by serving stale cache for non-critical endpoints.
     *
     * @param endpoint the target endpoint
     * @param supplier the HTTP request supplier
     * @param <T>      response type
     * @return a CompletableFuture with the response
     */
    public <T> CompletableFuture<T> route(
            @NotNull EndpointType endpoint,
            @NotNull java.util.function.Supplier<CompletableFuture<T>> supplier
    ) {
        ExchangeCircuitBreaker breaker = circuitBreakers.get(endpoint);
        telemetry.recordRequest(endpoint);

        long startNs = System.nanoTime();
        return breaker.call(supplier)
                .whenComplete((result, ex) -> {
                    long latencyMs = (System.nanoTime() - startNs) / 1_000_000;
                    if (ex != null) {
                        Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
                        healthMonitor.recordFailure(endpoint, cause);
                        healthMonitor.updateCircuitState(endpoint, breaker.getState());
                        telemetry.recordFailure(endpoint, latencyMs);
                    } else {
                        healthMonitor.recordSuccess(endpoint, latencyMs);
                        healthMonitor.updateCircuitState(endpoint, breaker.getState());
                        telemetry.recordSuccess(endpoint, latencyMs);
                    }
                });
    }

    /**
     * Records a failure directly against an endpoint's circuit breaker and health monitor.
     *
     * <p>Use this when status-code checks inside a supplier detect errors that should
     * be counted (e.g. HTTP 504 inside {@code OandaTransactionClient.sendJson}).
     * The circuit breaker accumulates the failure count and may open the circuit.
     *
     * @param endpoint the endpoint that failed
     * @param cause    the exception representing the failure
     */
    public void recordFailure(@NotNull EndpointType endpoint, @NotNull Throwable cause) {
        ExchangeCircuitBreaker breaker = circuitBreakers.get(endpoint);
        if (breaker != null) {
            breaker.recordFailure(cause);
            healthMonitor.updateCircuitState(endpoint, breaker.getState());
        }
        healthMonitor.recordFailure(endpoint, cause);
        telemetry.recordFailure(endpoint, 0L);
    }

    /**
     * Records a successful response directly against an endpoint's circuit breaker
     * and health monitor.
     *
     * <p>Resets the consecutive-failure counter and may transition the circuit from
     * HALF_OPEN back to CLOSED.
     *
     * @param endpoint the endpoint that succeeded
     */
    public void recordSuccess(@NotNull EndpointType endpoint) {
        ExchangeCircuitBreaker breaker = circuitBreakers.get(endpoint);
        if (breaker != null) {
            breaker.recordSuccess();
            healthMonitor.updateCircuitState(endpoint, breaker.getState());
        }
        healthMonitor.recordSuccess(endpoint, 0L);
        telemetry.recordSuccess(endpoint, 0L);
    }

    /**
     * Returns the current connectivity state of the exchange.
     *
     * @return current state
     */
    public @NotNull ExchangeConnectivityState getState() {
        return connectivityState.get();
    }

    /**
     * Returns true if the given endpoint's circuit breaker allows requests.
     *
     * @param endpoint the endpoint to check
     * @return true if requests are permitted
     */
    public boolean canRoute(@NotNull EndpointType endpoint) {
        return circuitBreakers.get(endpoint).isRequestAllowed();
    }

    /**
     * Returns the circuit state for the given endpoint.
     *
     * @param endpoint the endpoint to check
     * @return current circuit state
     */
    public @NotNull CircuitState getCircuitState(@NotNull EndpointType endpoint) {
        return circuitBreakers.get(endpoint).getState();
    }

    /** Signals that the WebSocket connection was established. */
    public void onWebSocketConnected() {
        telemetry.recordWebSocketConnected();
        transitionState(ExchangeConnectivityState.CONNECTED);
    }

    /** Signals that the WebSocket connection was lost. */
    public void onWebSocketDisconnected() {
        telemetry.recordWebSocketDisconnected();
        transitionState(ExchangeConnectivityState.RECOVERING);
    }

    /**
     * Builds a full {@link ExchangeHealthReport} from current state.
     *
     * @return current health report
     */
    public @NotNull ExchangeHealthReport buildHealthReport() {
        Map<EndpointType, EndpointHealthSnapshot> snapshots = healthMonitor.snapshotAll();
        ExchangeHealthScore score = ExchangeHealthScore.compute(snapshots, telemetry.snapshot());
        return new ExchangeHealthReport(
                exchangeName,
                connectivityState.get(),
                score.grade(),
                score.composite(),
                score.websocketHealth(),
                score.endpointHealth(),
                score.executionHealth(),
                score.latencyScore(),
                score.reliabilityScore(),
                telemetry.isWebSocketConnected(),
                countOpenCircuits(snapshots),
                countDegradedEndpoints(snapshots),
                snapshots,
                Instant.now()
        );
    }

    /** Shuts down all circuit breakers and background tasks. */
    public void shutdown() {
        circuitBreakers.values().forEach(ExchangeCircuitBreaker::shutdown);
        healthEvaluator.shutdownNow();
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private void evaluateConnectivityState() {
        try {
            Map<EndpointType, EndpointHealthSnapshot> snapshots = healthMonitor.snapshotAll();
            ExchangeConnectivityState newState = deriveState(snapshots);
            ExchangeConnectivityState previous = connectivityState.getAndSet(newState);

            if (previous != newState) {
                log.info("Exchange connectivity state changed: {} → {} for {}", previous, newState, exchangeName);
                emitStateChangeEvent(previous, newState);
            }
        } catch (Exception e) {
            log.debug("Health evaluation error for {}: {}", exchangeName, e.getMessage());
        }
    }

    private @NotNull ExchangeConnectivityState deriveState(
            @NotNull Map<EndpointType, EndpointHealthSnapshot> snapshots
    ) {
        boolean criticalCircuitOpen = snapshots.entrySet().stream()
                .filter(e -> e.getKey().critical)
                .anyMatch(e -> e.getValue().circuitState() == CircuitState.OPEN);
        boolean anyCircuitOpen = snapshots.values().stream()
                .anyMatch(s -> s.circuitState() == CircuitState.OPEN);
        boolean anyFailing = snapshots.values().stream()
                .anyMatch(s -> s.consecutiveFailures() > 0);

        if (criticalCircuitOpen) return ExchangeConnectivityState.CIRCUIT_OPEN;
        if (anyCircuitOpen || anyFailing) return ExchangeConnectivityState.DEGRADED;
        return ExchangeConnectivityState.CONNECTED;
    }

    private void transitionState(@NotNull ExchangeConnectivityState newState) {
        ExchangeConnectivityState previous = connectivityState.getAndSet(newState);
        if (previous != newState) {
            emitStateChangeEvent(previous, newState);
        }
    }

    private void emitStateChangeEvent(
            @NotNull ExchangeConnectivityState from,
            @NotNull ExchangeConnectivityState to
    ) {
        if (eventBus == null) return;
        String eventType = switch (to) {
            case CONNECTED -> AgentEvent.EXCHANGE_CONNECTED;
            case DISCONNECTED, RECOVERING -> AgentEvent.EXCHANGE_DISCONNECTED;
            default -> AgentEvent.EXCHANGE_HEALTH_CHANGED;
        };
        Map<String, Object> meta = Map.of(
                "exchangeName", exchangeName,
                "from", from.name(),
                "to", to.name()
        );
        eventBus.publishAsync(AgentEvent.of(eventType, "ExchangeConnectivityManager", to, meta));
    }

    private int countOpenCircuits(@NotNull Map<EndpointType, EndpointHealthSnapshot> snapshots) {
        return (int) snapshots.values().stream()
                .filter(s -> s.circuitState() == CircuitState.OPEN)
                .count();
    }

    private int countDegradedEndpoints(@NotNull Map<EndpointType, EndpointHealthSnapshot> snapshots) {
        return (int) snapshots.values().stream()
                .filter(s -> s.consecutiveFailures() > 0)
                .count();
    }
}
