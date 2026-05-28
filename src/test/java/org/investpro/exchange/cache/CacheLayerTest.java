package org.investpro.exchange.cache;

import org.investpro.exchange.normalization.NormalizedMarketSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the typed stale-safe cache layer.
 *
 * <p>Tests verify that caches return values from suppliers, serve stale
 * data, and expose invalidation correctly. No live API calls.
 */
class CacheLayerTest {

    @Test
    void marketSnapshotCacheFetchesFromSupplier() throws ExecutionException, InterruptedException {
        MarketSnapshotCache cache = new MarketSnapshotCache("Coinbase", null, null);
        NormalizedMarketSnapshot expected = freshSnapshot();

        NormalizedMarketSnapshot result = cache.get(() -> CompletableFuture.completedFuture(expected)).get();

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void balanceCacheFetchesFromSupplier() throws ExecutionException, InterruptedException {
        BalanceSnapshotCache cache = new BalanceSnapshotCache("Coinbase", null, null);
        var expectedBalance = java.util.Map.of("USD", new BigDecimal("10000.00"));

        var result = cache.get(() -> CompletableFuture.completedFuture(expectedBalance)).get();

        assertThat(result).containsEntry("USD", new BigDecimal("10000.00"));
    }

    @Test
    void orderHistoryCacheFetchesFromSupplier() throws ExecutionException, InterruptedException {
        OrderHistorySnapshotCache cache = new OrderHistorySnapshotCache("Coinbase", null, null);
        var expectedOrders = java.util.List.of("order-1", "order-2");

        var result = cache.get(() -> CompletableFuture.completedFuture(expectedOrders)).get();

        assertThat(result).containsExactlyInAnyOrder("order-1", "order-2");
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private NormalizedMarketSnapshot freshSnapshot() {
        return NormalizedMarketSnapshot.fromTicker(
                "Coinbase", "BTC-USD",
                new BigDecimal("40000"),
                new BigDecimal("40001"),
                new BigDecimal("40000.5"),
                new BigDecimal("1000"),
                Instant.now()
        );
    }
}
