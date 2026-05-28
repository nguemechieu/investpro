package org.investpro.exchange.cache;

import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.investpro.exchange.resilience.StaleCacheManager;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Typed stale-safe cache for order history snapshots.
 *
 * <p>Order history is the least time-sensitive endpoint; defaults to a
 * 5-minute max-fresh age. The cache prevents order-history endpoint failures
 * from impacting pricing or execution flows.
 *
 * <p>The snapshot is typed as {@code List<String>} (order IDs) for generic
 * compatibility. In practice, adapt to a concrete order record type in the
 * specific adapter.
 */
public final class OrderHistorySnapshotCache {

    private static final Duration DEFAULT_MAX_FRESH_AGE = Duration.ofMinutes(5);

    private final StaleCacheManager<List<String>> delegate;

    public OrderHistorySnapshotCache(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry
    ) {
        this(exchangeName, DEFAULT_MAX_FRESH_AGE, eventBus, telemetry);
    }

    public OrderHistorySnapshotCache(
            @NotNull String exchangeName,
            @NotNull Duration maxFreshAge,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry
    ) {
        this.delegate = new StaleCacheManager<>(
                exchangeName + "-order-history-snapshot",
                EndpointType.ORDER_HISTORY,
                maxFreshAge,
                eventBus,
                telemetry
        );
    }

    /**
     * Returns a fresh or stale order history list, scheduling a background refresh if stale.
     *
     * @param freshFetcher supplier that fetches order history from the exchange
     * @return future resolving to the (possibly stale) order list
     */
    public CompletableFuture<List<String>> get(
            @NotNull Supplier<CompletableFuture<List<String>>> freshFetcher
    ) {
        return delegate.getOrServeStale(freshFetcher);
    }

    /** Forces the cache to be invalidated on the next access. */
    public void invalidate() {
        delegate.invalidate();
    }
}
