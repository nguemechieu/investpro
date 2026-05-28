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
 */
@Slf4j
public class CircuitBreakerCoordinator {

    private static final EndpointType[] MANAGED_ENDPOINTS = {
            EndpointType.PRICING,
            EndpointType.EXECUTION,
            EndpointType.BALANCES,
            EndpointType.ORDER_HISTORY
    };

    private final Map<String, Map<EndpointType, ExchangeCircuitBreaker>> circuits =
            new ConcurrentHashMap<>();

    private final Map<String, ExchangeThrottleProfile> throttleProfiles =
            new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;

    public CircuitBreakerCoordinator(@Nullable AgentEventBus eventBus) {
        this.eventBus = eventBus;
    }

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

    public @NotNull Optional<ExchangeCircuitBreaker> getCircuitBreaker(
            @NotNull String exchangeName,
            @NotNull EndpointType endpoint
    ) {
        Map<EndpointType, ExchangeCircuitBreaker> map = circuits.get(exchangeName);
        if (map == null) return Optional.empty();
        return Optional.ofNullable(map.get(endpoint));
    }

    public boolean isExecutionAllowed(@NotNull String exchangeName) {
        return getCircuitBreaker(exchangeName, EndpointType.EXECUTION)
                .map(ExchangeCircuitBreaker::isRequestAllowed)
                .orElse(true);
    }

    public boolean isPricingAllowed(@NotNull String exchangeName) {
        return getCircuitBreaker(exchangeName, EndpointType.PRICING)
                .map(ExchangeCircuitBreaker::isRequestAllowed)
                .orElse(true);
    }

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

    public @NotNull Map<String, ExchangeConnectivityState> getAggregateStateAll() {
        Map<String, ExchangeConnectivityState> result = new HashMap<>();
        for (String exchange : circuits.keySet()) {
            result.put(exchange, getAggregateState(exchange));
        }
        return Map.copyOf(result);
    }

    public void recordSuccess(@NotNull String exchangeName, @NotNull EndpointType endpoint) {
        getCircuitBreaker(exchangeName, endpoint).ifPresent(ExchangeCircuitBreaker::recordSuccess);
    }

    public void recordFailure(
            @NotNull String exchangeName,
            @NotNull EndpointType endpoint,
            @NotNull Throwable cause
    ) {
        getCircuitBreaker(exchangeName, endpoint)
                .ifPresent(cb -> cb.recordFailure(cause));
    }

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
