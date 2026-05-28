package org.investpro.exchange.routing;

import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionRoute;
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
        router = new SmartExecutionRouter(null);
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
        assertThat(result).isEmpty();
    }

    @Test
    void routeSelectsBestSpreadExchange() {
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
        ExecutionRequest req = ExecutionRequest.builder("BTC-USD", ExecutionRequest.Side.BUY, BigDecimal.ONE)
                .exchange("Coinbase")
                .allowFallback(false)
                .build();

        Optional<ExecutionRoute> result = router.route(req, List.of("Coinbase", "Binance"));
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
        return NormalizedMarketSnapshot.fromRawPrices(
                exchange, symbol,
                40000.00, 40001.00, 40000.50, 5_000_000.0,
                Instant.now()
        );
    }

    private NormalizedMarketSnapshot wideSpreadSnapshot(String exchange, String symbol) {
        return NormalizedMarketSnapshot.fromRawPrices(
                exchange, symbol,
                39900.00, 40100.00, 40000.00, 1_000_000.0,
                Instant.now()
        );
    }
}
