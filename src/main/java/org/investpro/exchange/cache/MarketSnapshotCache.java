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
 *
 * <p>Wraps {@link StaleCacheManager}{@code <NormalizedMarketSnapshot>} instances,
 * keyed by a composite {@code "EXCHANGE:BASE/QUOTE"} string.  When a fresh fetch
 * fails or the circuit breaker is open, stale data is served transparently.
 *
 * <p>Each (exchange, pair) combination maintains an independent cache entry with
 * its own staleness timer.
 *
 * <p>Note: {@code NormalizedMarketSnapshot} is expected in package
 * {@code org.investpro.exchange.normalization} and will be linked once that
 * package is implemented.
 */
public class MarketSnapshotCache {

    private final ConcurrentHashMap<String, StaleCacheManager<NormalizedMarketSnapshot>> caches =
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
    public MarketSnapshotCache(
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
     * Returns a cached snapshot if fresh, or calls {@code fetcher} to refresh.
     *
     * @param exchangeName the exchange identifier (e.g., "COINBASE")
     * @param pair         the trade pair
     * @param fetcher      supplier that performs a fresh fetch when needed
     * @return future resolving to the (possibly stale) snapshot
     */
    public @NotNull CompletableFuture<NormalizedMarketSnapshot> getOrFetch(
            @NotNull String exchangeName,
            @NotNull TradePair pair,
            @NotNull Supplier<CompletableFuture<NormalizedMarketSnapshot>> fetcher
    ) {
        return cacheFor(exchangeName, pair).getOrServeStale(fetcher);
    }

    /**
     * Stores a fresh snapshot in the cache.
     *
     * @param exchangeName the exchange identifier
     * @param pair         the trade pair
     * @param snapshot     the fresh snapshot to store
     */
    public void store(
            @NotNull String exchangeName,
            @NotNull TradePair pair,
            @NotNull NormalizedMarketSnapshot snapshot
    ) {
        cacheFor(exchangeName, pair).store(snapshot);
    }

    /**
     * Marks the cache entry for the given (exchange, pair) as stale without evicting it.
     *
     * @param exchangeName the exchange identifier
     * @param pair         the trade pair
     */
    public void markStale(@NotNull String exchangeName, @NotNull TradePair pair) {
        cacheFor(exchangeName, pair).markStale();
    }

    /**
     * Returns {@code true} if the cache entry is stale (exists but beyond max age).
     *
     * @param exchangeName the exchange identifier
     * @param pair         the trade pair
     */
    public boolean isStale(@NotNull String exchangeName, @NotNull TradePair pair) {
        return cacheFor(exchangeName, pair).isStale();
    }

    /**
     * Returns {@code true} if any cached value (fresh or stale) exists for the given key.
     *
     * @param exchangeName the exchange identifier
     * @param pair         the trade pair
     */
    public boolean hasCachedValue(@NotNull String exchangeName, @NotNull TradePair pair) {
        return cacheFor(exchangeName, pair).hasCachedValue();
    }

    /**
     * Shuts down all underlying stale-cache managers, releasing background threads.
     */
    public void shutdown() {
        caches.values().forEach(StaleCacheManager::shutdown);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Computes the canonical cache key for the given (exchange, pair) combination. */
    private static @NotNull String getCacheKey(
            @NotNull String exchangeName,
            @NotNull TradePair pair
    ) {
        return exchangeName.toUpperCase() + ":" + pair.toString().toUpperCase();
    }

    /** Returns (creating if absent) the StaleCacheManager for the given key. */
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

    // ── Placeholder type ──────────────────────────────────────────────────────

    /**
     * Placeholder for {@code org.investpro.exchange.normalization.NormalizedMarketSnapshot}.
     * Replace this inner interface with the real import once that class is implemented.
     */
    public interface NormalizedMarketSnapshot {
    }
}
