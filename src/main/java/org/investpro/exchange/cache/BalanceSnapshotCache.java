package org.investpro.exchange.cache;

import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.investpro.exchange.resilience.StaleCacheManager;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Typed cache wrapper for account balance snapshots.
 */
public class BalanceSnapshotCache {

    private final ConcurrentHashMap<String, StaleCacheManager<Map<String, Double>>> caches =
            new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;
    @Nullable
    private final ExchangeTelemetryEngine telemetry;
    private final Duration maxFreshAge;

    public BalanceSnapshotCache(
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry,
            @NotNull Duration maxFreshAge
    ) {
        this.eventBus = eventBus;
        this.telemetry = telemetry;
        this.maxFreshAge = maxFreshAge;
    }

    public @NotNull CompletableFuture<Map<String, Double>> getOrFetch(
            @NotNull String exchangeName,
            @NotNull Supplier<CompletableFuture<Map<String, Double>>> fetcher
    ) {
        return cacheFor(exchangeName).getOrServeStale(fetcher);
    }

    public void store(
            @NotNull String exchangeName,
            @NotNull Map<String, Double> balances
    ) {
        cacheFor(exchangeName).store(balances);
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

    private @NotNull StaleCacheManager<Map<String, Double>> cacheFor(
            @NotNull String exchangeName
    ) {
        String key = exchangeName.toUpperCase() + ":BALANCES";
        return caches.computeIfAbsent(key, k ->
                new StaleCacheManager<>(
                        k,
                        EndpointType.BALANCES,
                        maxFreshAge,
                        eventBus,
                        telemetry
                ));
    }
}
