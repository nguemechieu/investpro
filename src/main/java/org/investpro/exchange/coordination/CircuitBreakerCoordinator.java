package org.investpro.exchange.coordination;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.ExchangeCircuitBreaker;
import org.investpro.exchange.resilience.model.EndpointType;
import org.investpro.exchange.resilience.model.ExchangeConnectivityState;
import org.investpro.exchange.resilience.model.CircuitState;
import org.investpro.exchange.throttle.ExchangeThrottleProfile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages a fleet of {@link ExchangeCircuitBreaker} instances, one per
 * (exchange, endpoint) pair.
 *
 * <p>Provides aggregate connectivity state queries, delegated record/success
 * calls, and coordinated shutdown.  Each registered exchange receives circuit
 * breakers for the four primary endpoint categories:
 * {@link EndpointType#PRICING}, {@link EndpointType#EXECUTION},
 * {@link EndpointType#BALANCES}, and {@link EndpointType#ORDER_HISTORY}.
 *
 * <p>The aggregate state ({@link #getAggregateState}) maps to
 * {@link ExchangeConnectivityState} as follows:
 * <ul>
 *   <li>All breakers CLOSED → {@code CONNECTED}</li>
 *   <li>EXECUTION breaker OPEN → {@code CIRCUIT_OPEN}</li>
 *   <li>Any other breaker OPEN → {@code DEGRADED}</li>
 * </ul>
 */
@Slf4j
public class CircuitBreakerCoordinator {

    /** Critical endpoints for which circuit breakers are created per exchange. */
    private static final EndpointType[] MANAGED_ENDPOINTS = {
            EndpointType.PRICING,
            EndpointType.EXECUTION,
            EndpointType.BALANCES,
            EndpointType.ORDER_HISTORY
    };

    // per-exchange → per-endpoint circuit breaker map
    private final Map<String, Map<EndpointType, ExchangeCircuitBreaker>> circuits =
            new ConcurrentHashMap<>();

    private final Map<String, ExchangeThrottleProfile> throttleProfiles =
            new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;

    /**
     * Constructs the coordinator with an optional event bus.
     *
     * @param eventBus optional event bus; {@code null} disables circuit-breaker telemetry
     */
    public CircuitBreakerCoordinator(@Nullable AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

    // ── Registration ─────────────────────────────────────────────────────────

    /**
     * Registers an exchange and creates circuit breakers for its managed endpoints.
     *
     * <p>Calling this method multiple times for the same exchange name is a no-op
     * after the first registration.
     *
     * @param exchangeName the unique exchange identifier (e.g., "COINBASE")
     * @param profile      the throttle profile that governs retry behaviour for this exchange
     */
    public void registerExchange(
            @NotNull String exchangeName,
            @NotNull ExchangeThrottleProfile profile
    ) {
        circuits.computeIfAbsent(exchangeName, name -> {
            Map<EndpointType, ExchangeCircuitBreaker> map = new EnumMap<>(EndpointType.class);
            for (EndpointType endpoint : MANAGED_ENDPOINTS) {
                map.put(endpoint, new ExchangeCircuitBreaker(name, endpoint, eventBus));
            }
            log.info("CircuitBreakerCoordinator: registered {} with {} endpoint breakers",
                    name, MANAGED_ENDPOINTS.length);
            return map;
        });
        throttleProfiles.put(exchangeName, profile);
    }

    // ── Circuit breaker access ────────────────────────────────────────────────

    /**
     * Returns the circuit breaker for the given exchange and endpoint, if registered.
     *
     * @param exchangeName the exchange name
     * @param endpoint     the endpoint type
     * @return an {@link Optional} containing the breaker, or empty if not registered
     */
    public @NotNull Optional<ExchangeCircuitBreaker> getCircuitBreaker(
            @NotNull String exchangeName,
            @NotNull EndpointType endpoint
    ) {
        Map<EndpointType, ExchangeCircuitBreaker> map = circuits.get(exchangeName);
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get(endpoint));
    }

    // ── Execution / pricing guards ────────────────────────────────────────────

    /**
     * Returns {@code true} if the EXECUTION endpoint circuit is CLOSED or HALF_OPEN
     * (i.e., requests are permitted).
     *
     * @param exchangeName the exchange to check
     * @return {@code false} if the EXECUTION circuit is OPEN; {@code true} otherwise
     */
    public boolean isExecutionAllowed(@NotNull String exchangeName) {
        return getCircuitBreaker(exchangeName, EndpointType.EXECUTION)
                .map(ExchangeCircuitBreaker::isRequestAllowed)
                .orElse(true);
    }

    /**
     * Returns {@code true} if the PRICING endpoint circuit allows requests.
     *
     * @param exchangeName the exchange to check
     */
    public boolean isPricingAllowed(@NotNull String exchangeName) {
        return getCircuitBreaker(exchangeName, EndpointType.PRICING)
                .map(ExchangeCircuitBreaker::isRequestAllowed)
                .orElse(true);
    }

    // ── Aggregate state ───────────────────────────────────────────────────────

    /**
     * Returns the aggregate connectivity state for a single exchange.
     *
     * <ul>
     *   <li>CONNECTED — all managed breakers are CLOSED</li>
     *   <li>CIRCUIT_OPEN — the EXECUTION breaker is OPEN</li>
     *   <li>DEGRADED — at least one non-EXECUTION breaker is OPEN</li>
     * </ul>
     *
     * @param exchangeName the exchange to query
     * @return aggregate {@link ExchangeConnectivityState}
     */
    public @NotNull ExchangeConnectivityState getAggregateState(@NotNull String exchangeName) {
        Map<EndpointType, ExchangeCircuitBreaker> map = circuits.get(exchangeName);
        if (map == null) return ExchangeConnectivityState.DISCONNECTED;

        ExchangeCircuitBreaker executionBreaker = map.get(EndpointType.EXECUTION);
        if (executionBreaker != null && executionBreaker.getState() == CircuitState.OPEN) {
            return ExchangeConnectivityState.CIRCUIT_OPEN;
        }

        boolean anyOpen = map.values().stream()
                .anyMatch(cb -> cb.getState() == CircuitState.OPEN);
        return anyOpen ? ExchangeConnectivityState.DEGRADED : ExchangeConnectivityState.CONNECTED;
    }

    /**
     * Returns the aggregate connectivity state for every registered exchange.
     *
     * @return map of exchange name → {@link ExchangeConnectivityState}
     */
    public @NotNull Map<String, ExchangeConnectivityState> getAggregateStateAll() {
        Map<String, ExchangeConnectivityState> result = new HashMap<>();
        for (String exchange : circuits.keySet()) {
            result.put(exchange, getAggregateState(exchange));
        }
        return Map.copyOf(result);
    }

    // ── Record success / failure ──────────────────────────────────────────────

    /**
     * Records a successful call on the specified endpoint.
     *
     * @param exchangeName the exchange that succeeded
     * @param endpoint     the endpoint that succeeded
     */
    public void recordSuccess(@NotNull String exchangeName, @NotNull EndpointType endpoint) {
        getCircuitBreaker(exchangeName, endpoint).ifPresent(ExchangeCircuitBreaker::recordSuccess);
    }

    /**
     * Records a failed call on the specified endpoint.
     *
     * @param exchangeName the exchange that failed
     * @param endpoint     the endpoint that failed
     * @param cause        the exception that caused the failure
     */
    public void recordFailure(
            @NotNull String exchangeName,
            @NotNull EndpointType endpoint,
            @NotNull Throwable cause
    ) {
        getCircuitBreaker(exchangeName, endpoint)
                .ifPresent(cb -> cb.recordFailure(cause));
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Shuts down all managed circuit breakers, releasing background scheduler threads.
     * Should be called during application shutdown.
     */
    public void shutdown() {
        circuits.values().stream()
                .flatMap(map -> map.values().stream())
                .forEach(cb -> {
                    try {
                        cb.shutdown();
                    } catch (Exception e) {
                        log.warn("Error shutting down circuit breaker: {}", e.getMessage());
                    }
                });
        log.info("CircuitBreakerCoordinator: all circuit breakers shut down");
    }
}
