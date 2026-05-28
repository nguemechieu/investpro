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
 *
 * <p>Wraps {@link StaleCacheManager}{@code <Map<String, Double>>} instances, where
 * the map key is the currency symbol (e.g., {@code "USD"}, {@code "BTC"}) and the
 * value is the available balance.
 *
 * <p>Cache keys are {@code "EXCHANGE:ACCOUNT"} strings, using the exchange name
 * as the primary key.  Multiple accounts per exchange can be supported by
 * appending an account identifier.
 *
 * <p>When a balance fetch fails or the BALANCES circuit breaker is open,
 * the last known balance snapshot is served as stale data.
 */
public class BalanceSnapshotCache {

    private final ConcurrentHashMap<String, StaleCacheManager<Map<String, Double>>> caches =
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
    public BalanceSnapshotCache(
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
     * Returns cached balances if fresh, or calls {@code fetcher} to refresh.
     *
     * @param exchangeName the exchange identifier (e.g., "OANDA")
     * @param fetcher      supplier that performs a fresh balance fetch when needed
     * @return future resolving to the (possibly stale) balance map
     */
    public @NotNull CompletableFuture<Map<String, Double>> getOrFetch(
            @NotNull String exchangeName,
            @NotNull Supplier<CompletableFuture<Map<String, Double>>> fetcher
    ) {
        return cacheFor(exchangeName).getOrServeStale(fetcher);
    }

    /**
     * Stores a fresh balance snapshot in the cache.
     *
     * @param exchangeName the exchange identifier
     * @param balances     the fresh balance map (currency → available balance)
     */
    public void store(
            @NotNull String exchangeName,
            @NotNull Map<String, Double> balances
    ) {
        cacheFor(exchangeName).store(balances);
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
