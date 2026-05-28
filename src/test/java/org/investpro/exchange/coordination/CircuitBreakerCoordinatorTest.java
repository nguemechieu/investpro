package org.investpro.exchange.coordination;

import org.investpro.exchange.resilience.model.CircuitState;
import org.investpro.exchange.resilience.model.EndpointType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link CircuitBreakerCoordinator}.
 *
 * <p>No live API calls. Tests circuit health queries and aggregate ratios.
 */
class CircuitBreakerCoordinatorTest {

    private CircuitBreakerCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = CircuitBreakerCoordinator.forExchange("TestExchange", null);
    }

    @Test
    void allCircuitsStartClosed() {
        assertThat(coordinator.isFullyHealthy()).isTrue();
        assertThat(coordinator.hasOpenCircuit()).isFalse();
    }

    @Test
    void aggregateHealthRatioIsOneWhenAllClosed() {
        assertThat(coordinator.aggregateHealthRatio()).isEqualTo(1.0);
    }

    @Test
    void circuitStateForPricingIsClosed() {
        Optional<CircuitState> state = coordinator.circuitState(EndpointType.PRICING);
        assertThat(state).isPresent();
        assertThat(state.get()).isEqualTo(CircuitState.CLOSED);
    }

    @Test
    void allStatesMapContainsAllEndpoints() {
        Map<EndpointType, CircuitState> states = coordinator.allStates();
        for (EndpointType et : EndpointType.values()) {
            assertThat(states).containsKey(et);
        }
    }

    @Test
    void specificEndpointIsHealthy() {
        assertThat(coordinator.isHealthy(EndpointType.PRICING)).isTrue();
        assertThat(coordinator.isHealthy(EndpointType.ORDER_HISTORY)).isTrue();
    }

    @Test
    void exchangeNameIsPreserved() {
        assertThat(coordinator.getExchangeName()).isEqualTo("TestExchange");
    }

    @Test
    void lazyBreakerCreationForNewEndpoint() {
        assertThat(coordinator.breaker(EndpointType.PRICING)).isNotNull();
    }
}
