package org.investpro.exchange.cache;

import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.investpro.exchange.resilience.StaleCacheManager;
import org.investpro.exchange.resilience.model.EndpointType;
import org.investpro.models.trading.Order;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Typed cache wrapper for order history snapshots.
 *
 * <p>Wraps {@link StaleCacheManager}{@code <List<Order>>} instances, keyed by
 * exchange name.  This is a non-critical cache — when order history cannot be
 * refreshed (e.g., circuit breaker open, network timeout), the last known list
 * of orders is served as stale data rather than propagating the failure.
 *
 * <p>The staleness TTL should be set generously (e.g., 5–30 minutes) for order
 * history, since this data changes infrequently compared to pricing.
 */
public class OrderHistorySnapshotCache {

    private final ConcurrentHashMap<String, StaleCacheManager<List<Order>>> caches =
            new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;
    @Nullable
    private final ExchangeTelemetryEngine telemetry;
    private final Duration maxFreshAge;

    /**
     * Constructs the cache.
     *
     * @param eventBus    optional event bus for stale-cache telemetry events
     * @param telemetry   optional telemetry engine for stale-served counters
     * @param maxFreshAge maximum age of a cache entry before it is considered stale
     */
    public OrderHistorySnapshotCache(
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry,
            @NotNull Duration maxFreshAge
    ) {
        this.eventBus = eventBus;
        this.telemetry = telemetry;
        this.maxFreshAge = maxFreshAge;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Returns cached order history if fresh, or calls {@code fetcher} to refresh.
     *
     * @param exchangeName the exchange identifier (e.g., "BINANCE")
     * @param fetcher      supplier that performs a fresh order-history fetch when needed
     * @return future resolving to the (possibly stale) list of orders
     */
    public @NotNull CompletableFuture<List<Order>> getOrFetch(
            @NotNull String exchangeName,
            @NotNull Supplier<CompletableFuture<List<Order>>> fetcher
    ) {
        return cacheFor(exchangeName).getOrServeStale(fetcher);
    }

    /**
     * Stores a fresh order history list in the cache.
     *
     * @param exchangeName the exchange identifier
     * @param orders       the fresh list of orders to cache
     */
    public void store(
            @NotNull String exchangeName,
            @NotNull List<Order> orders
    ) {
        cacheFor(exchangeName).store(orders);
    }

    /**
     * Marks the cache entry for the given exchange as stale without evicting it.
     *
     * @param exchangeName the exchange identifier
     */
    public void markStale(@NotNull String exchangeName) {
        cacheFor(exchangeName).markStale();
    }

    /**
     * Returns {@code true} if the cache entry is stale (exists but beyond max age).
     *
     * @param exchangeName the exchange identifier
     */
    public boolean isStale(@NotNull String exchangeName) {
        return cacheFor(exchangeName).isStale();
    }

    /**
     * Returns {@code true} if any cached value (fresh or stale) exists for the given key.
     *
     * @param exchangeName the exchange identifier
     */
    public boolean hasCachedValue(@NotNull String exchangeName) {
        return cacheFor(exchangeName).hasCachedValue();
    }

    /**
     * Shuts down all underlying stale-cache managers, releasing background threads.
     */
    public void shutdown() {
        caches.values().forEach(StaleCacheManager::shutdown);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Returns (creating if absent) the StaleCacheManager for the given exchange. */
    private @NotNull StaleCacheManager<List<Order>> cacheFor(
            @NotNull String exchangeName
    ) {
        String key = exchangeName.toUpperCase() + ":ORDER_HISTORY";
        return caches.computeIfAbsent(key, k ->
                new StaleCacheManager<>(
                        k,
                        EndpointType.ORDER_HISTORY,
                        maxFreshAge,
                        eventBus,
                        telemetry
                ));
    }
}
