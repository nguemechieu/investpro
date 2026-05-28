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
 */
public class OrderHistorySnapshotCache {

    private final ConcurrentHashMap<String, StaleCacheManager<List<Order>>> caches =
            new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;
    @Nullable
    private final ExchangeTelemetryEngine telemetry;
    private final Duration maxFreshAge;

    public OrderHistorySnapshotCache(
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry,
            @NotNull Duration maxFreshAge
    ) {
        this.eventBus = eventBus;
        this.telemetry = telemetry;
        this.maxFreshAge = maxFreshAge;
    }

    public @NotNull CompletableFuture<List<Order>> getOrFetch(
            @NotNull String exchangeName,
            @NotNull Supplier<CompletableFuture<List<Order>>> fetcher
    ) {
        return cacheFor(exchangeName).getOrServeStale(fetcher);
    }

    public void store(
            @NotNull String exchangeName,
            @NotNull List<Order> orders
    ) {
        cacheFor(exchangeName).store(orders);
    }

    public void markStale(@NotNull String exchangeName) {
        cacheFor(exchangeName).markStale();
    }

    public boolean isStale(@NotNull String exchangeName) {
        return cacheFor(exchangeName).isStale();
    }

    public boolean hasCachedValue(@NotNull String exchangeName) {
        return cacheFor(exchangeName).hasCachedValue();
    }

    public void shutdown() {
        caches.values().forEach(StaleCacheManager::shutdown);
    }

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
