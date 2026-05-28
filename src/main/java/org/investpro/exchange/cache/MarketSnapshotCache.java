package org.investpro.exchange.cache;

import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.investpro.exchange.resilience.StaleCacheManager;
import org.investpro.exchange.resilience.model.EndpointType;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Typed cache wrapper for normalised market snapshots.
 */
public class MarketSnapshotCache {

    private final ConcurrentHashMap<String, StaleCacheManager<NormalizedMarketSnapshot>> caches =
            new ConcurrentHashMap<>();

    @Nullable
    private final AgentEventBus eventBus;
    @Nullable
    private final ExchangeTelemetryEngine telemetry;
    private final Duration maxFreshAge;

    public MarketSnapshotCache(
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry,
            @NotNull Duration maxFreshAge
    ) {
        this.eventBus = eventBus;
        this.telemetry = telemetry;
        this.maxFreshAge = maxFreshAge;
    }

    public @NotNull CompletableFuture<NormalizedMarketSnapshot> getOrFetch(
            @NotNull String exchangeName,
            @NotNull TradePair pair,
            @NotNull Supplier<CompletableFuture<NormalizedMarketSnapshot>> fetcher
    ) {
        return cacheFor(exchangeName, pair).getOrServeStale(fetcher);
    }

    public void store(
            @NotNull String exchangeName,
            @NotNull TradePair pair,
            @NotNull NormalizedMarketSnapshot snapshot
    ) {
        cacheFor(exchangeName, pair).store(snapshot);
    }

    public void markStale(@NotNull String exchangeName, @NotNull TradePair pair) {
        cacheFor(exchangeName, pair).markStale();
    }

    public boolean isStale(@NotNull String exchangeName, @NotNull TradePair pair) {
        return cacheFor(exchangeName, pair).isStale();
    }

    public boolean hasCachedValue(@NotNull String exchangeName, @NotNull TradePair pair) {
        return cacheFor(exchangeName, pair).hasCachedValue();
    }

    public void shutdown() {
        caches.values().forEach(StaleCacheManager::shutdown);
    }

    private static @NotNull String getCacheKey(
            @NotNull String exchangeName,
            @NotNull TradePair pair
    ) {
        return exchangeName.toUpperCase() + ":" + pair.toString().toUpperCase();
    }

    private @NotNull StaleCacheManager<NormalizedMarketSnapshot> cacheFor(
            @NotNull String exchangeName,
            @NotNull TradePair pair
    ) {
        String key = getCacheKey(exchangeName, pair);
        return caches.computeIfAbsent(key, k ->
                new StaleCacheManager<>(
                        k,
                        EndpointType.PRICING,
                        maxFreshAge,
                        eventBus,
                        telemetry
                ));
    }

    /** Placeholder for {@code org.investpro.exchange.normalization.NormalizedMarketSnapshot}. */
    public interface NormalizedMarketSnapshot {
    }
}
