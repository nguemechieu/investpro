package org.investpro.exchange.coordination;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.ExchangeCircuitBreaker;
import org.investpro.exchange.resilience.model.CircuitState;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Coordinates multiple {@link ExchangeCircuitBreaker} instances for a single exchange.
 *
 * <p>One coordinator is created per exchange. It holds one circuit breaker
 * per {@link EndpointType} and provides aggregate health queries across all
 * circuits.
 *
 * <p>Typical usage:
 * <pre>{@code
 *   var coord = CircuitBreakerCoordinator.forExchange("Coinbase", eventBus);
 *   var result = coord.execute(EndpointType.PRICING, () -> client.getPrice("BTC-USD"));
 * }</pre>
 */
@Slf4j
public final class CircuitBreakerCoordinator {

    private final String exchangeName;
    private final Map<EndpointType, ExchangeCircuitBreaker> breakers;

    private CircuitBreakerCoordinator(
            String exchangeName,
            Map<EndpointType, ExchangeCircuitBreaker> breakers
    ) {
        this.exchangeName = exchangeName;
        this.breakers = breakers;
    }

    /**
     * Creates a coordinator covering all {@link EndpointType} values for an exchange.
     *
     * @param exchangeName exchange identifier
     * @param eventBus     optional event bus for circuit state events
     * @return new coordinator
     */
    public static CircuitBreakerCoordinator forExchange(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus
    ) {
        Map<EndpointType, ExchangeCircuitBreaker> map = new ConcurrentHashMap<>();
        for (EndpointType endpoint : EndpointType.values()) {
            map.put(endpoint, new ExchangeCircuitBreaker(exchangeName, endpoint, eventBus));
        }
        return new CircuitBreakerCoordinator(exchangeName, map);
    }

    /**
     * Returns the circuit breaker for a specific endpoint, creating one lazily if absent.
     *
     * @param endpoint the endpoint type
     * @return the circuit breaker for that endpoint
     */
    public @NotNull ExchangeCircuitBreaker breaker(@NotNull EndpointType endpoint) {
        return breakers.computeIfAbsent(
                endpoint,
                ep -> new ExchangeCircuitBreaker(exchangeName, ep, null)
        );
    }

    /**
     * Returns true if ALL circuits are in {@link CircuitState#CLOSED} (healthy).
     */
    public boolean isFullyHealthy() {
        return breakers.values().stream().allMatch(b -> b.getState() == CircuitState.CLOSED);
    }

    /**
     * Returns true if ANY circuit is in {@link CircuitState#OPEN} (degraded/unavailable).
     */
    public boolean hasOpenCircuit() {
        return breakers.values().stream().anyMatch(b -> b.getState() == CircuitState.OPEN);
    }

    /**
     * Returns true if the specific endpoint circuit is healthy (CLOSED).
     *
     * @param endpoint the endpoint to check
     */
    public boolean isHealthy(@NotNull EndpointType endpoint) {
        ExchangeCircuitBreaker cb = breakers.get(endpoint);
        return cb != null && cb.getState() == CircuitState.CLOSED;
    }

    /**
     * Returns the aggregate health ratio: proportion of CLOSED circuits in [0.0, 1.0].
     */
    public double aggregateHealthRatio() {
        if (breakers.isEmpty()) return 1.0;
        long closed = breakers.values().stream()
                .filter(b -> b.getState() == CircuitState.CLOSED)
                .count();
        return (double) closed / breakers.size();
    }

    /**
     * Returns the circuit state for a specific endpoint.
     *
     * @param endpoint the endpoint to query
     * @return optional circuit state (empty if no breaker registered)
     */
    public Optional<CircuitState> circuitState(@NotNull EndpointType endpoint) {
        ExchangeCircuitBreaker cb = breakers.get(endpoint);
        return cb == null ? Optional.empty() : Optional.of(cb.getState());
    }

    /** Returns the exchange name this coordinator manages. */
    public String getExchangeName() { return exchangeName; }

    /** Returns a snapshot map of all endpoint → circuit states. */
    public Map<EndpointType, CircuitState> allStates() {
        Map<EndpointType, CircuitState> result = new EnumMap<>(EndpointType.class);
        breakers.forEach((ep, cb) -> result.put(ep, cb.getState()));
        return result;
    }

    @Override
    public String toString() {
        return "CircuitBreakerCoordinator{exchange='" + exchangeName
                + "', health=" + String.format("%.0f%%", aggregateHealthRatio() * 100) + "}";
    }
}
