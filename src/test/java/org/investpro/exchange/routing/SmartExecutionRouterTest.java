package org.investpro.exchange.routing;

import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionRoute;
import org.investpro.exchange.execution.ExecutionVenue;
import org.investpro.exchange.normalization.NormalizedMarketSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SmartExecutionRouter}.
 *
 * <p>All tests use in-memory snapshots — no live API calls.
 */
class SmartExecutionRouterTest {

    private SmartExecutionRouter router;

    @BeforeEach
    void setUp() {
        router = new SmartExecutionRouter(null); // no event bus needed for unit tests
    }

    @Test
    void routeReturnsEmptyWhenNoCandidates() {
        ExecutionRequest req = ExecutionRequest.builder("BTC-USD", ExecutionRequest.Side.BUY, BigDecimal.ONE).build();
        Optional<ExecutionRoute> result = router.route(req, List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void routeReturnsEmptyWhenNoFreshSnapshots() {
        ExecutionRequest req = ExecutionRequest.builder("BTC-USD", ExecutionRequest.Side.BUY, BigDecimal.ONE).build();
        Optional<ExecutionRoute> result = router.route(req, List.of("Coinbase", "Binance"));
        // No snapshots registered, so nothing scorable
        assertThat(result).isEmpty();
    }

    @Test
    void routeSelectsBestSpreadExchange() {
        // Coinbase has tighter spread; should be preferred
        router.updateSnapshot("Coinbase", tightSpreadSnapshot("Coinbase", "BTC-USD"));
        router.updateSnapshot("Binance", wideSpreadSnapshot("Binance", "BTC-USD"));

        ExecutionRequest req = ExecutionRequest.builder("BTC-USD", ExecutionRequest.Side.BUY, BigDecimal.ONE).build();
        Optional<ExecutionRoute> result = router.route(req, List.of("Coinbase", "Binance"));

        assertThat(result).isPresent();
        assertThat(result.get().exchangeName()).isEqualTo("Coinbase");
    }

    @Test
    void routeEnforcesPreferredExchangeWhenFallbackDisallowed() {
        router.updateSnapshot("Binance", tightSpreadSnapshot("Binance", "BTC-USD"));
        // Coinbase not registered — no snapshot
        ExecutionRequest req = ExecutionRequest.builder("BTC-USD", ExecutionRequest.Side.BUY, BigDecimal.ONE)
                .exchange("Coinbase")
                .allowFallback(false)
                .build();

        Optional<ExecutionRoute> result = router.route(req, List.of("Coinbase", "Binance"));
        // Preferred exchange has no snapshot and fallback is disallowed
        assertThat(result).isEmpty();
    }

    @Test
    void routeFallsBackWhenPreferredUnavailable() {
        router.updateSnapshot("Binance", tightSpreadSnapshot("Binance", "BTC-USD"));
        ExecutionRequest req = ExecutionRequest.builder("BTC-USD", ExecutionRequest.Side.BUY, BigDecimal.ONE)
                .exchange("Coinbase")
                .allowFallback(true)
                .build();

        Optional<ExecutionRoute> result = router.route(req, List.of("Coinbase", "Binance"));
        assertThat(result).isPresent();
        assertThat(result.get().exchangeName()).isEqualTo("Binance");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private NormalizedMarketSnapshot tightSpreadSnapshot(String exchange, String symbol) {
        return NormalizedMarketSnapshot.fromTicker(
                exchange, symbol,
                new BigDecimal("40000.00"),
                new BigDecimal("40001.00"),
                new BigDecimal("40000.50"),
                new BigDecimal("5000000"),
                Instant.now()
        );
    }

    private NormalizedMarketSnapshot wideSpreadSnapshot(String exchange, String symbol) {
        return NormalizedMarketSnapshot.fromTicker(
                exchange, symbol,
                new BigDecimal("39900.00"),
                new BigDecimal("40100.00"),
                new BigDecimal("40000.00"),
                new BigDecimal("1000000"),
                Instant.now()
        );
    }
}
