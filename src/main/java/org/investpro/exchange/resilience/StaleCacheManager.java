package org.investpro.exchange.resilience;

import lombok.extern.slf4j.Slf4j;
import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Generic stale-cache manager for exchange endpoints.
 *
 * <p>When a non-critical endpoint (order history, analytics) fails or its circuit
 * breaker opens, the manager returns a cached snapshot marked as stale rather than
 * propagating the failure or triggering retry storms.
 *
 * <p>Behavior:
 * <ol>
 *   <li>Fresh hit: returns cached value immediately if within max age.</li>
 *   <li>Stale hit: marks snapshot stale, notifies telemetry, schedules background refresh,
 *       returns stale snapshot to caller.</li>
 *   <li>Cache miss: triggers synchronous fetch; stores result; returns to caller.</li>
 * </ol>
 *
 * @param <T> the cached value type (e.g., {@code OrderHistorySnapshot})
 */
@Slf4j
public final class StaleCacheManager<T> {

    private String cacheName = "";
    private final EndpointType endpoint;
    private final Duration maxFreshAge;
    @Nullable
    private final AgentEventBus eventBus;
    @Nullable
    private final ExchangeTelemetryEngine telemetry;

    private final AtomicReference<CacheEntry<T>> cache = new AtomicReference<>(null);

    private final ScheduledExecutorService backgroundRefresher = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "stale-cache-refresh-" + cacheName);
        t.setDaemon(true);
        return t;
    });

    private volatile boolean refreshInFlight = false;

    public StaleCacheManager(
            @NotNull String cacheName,
            @NotNull EndpointType endpoint,
            @NotNull Duration maxFreshAge,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry
    ) {
        this.cacheName = cacheName;
        this.endpoint = endpoint;
        this.maxFreshAge = maxFreshAge;
        this.eventBus = eventBus;
        this.telemetry = telemetry;
    }

    /**
     * Returns the cached value if fresh, or serves stale and schedules a background refresh.
     *
     * <p>If no cache entry exists at all, calls {@code freshFetcher} synchronously and
     * stores the result.
     *
     * @param freshFetcher supplier to call when a fresh fetch is needed
     * @return a future resolving to the (possibly stale) cached value
     */
    public @NotNull CompletableFuture<T> getOrServeStale(@NotNull Supplier<CompletableFuture<T>> freshFetcher) {
        CacheEntry<T> entry = cache.get();

        if (entry == null) {
            // Cold start: fetch synchronously
            return freshFetcher.get().whenComplete((value, ex) -> {
                if (ex == null && value != null) {
                    cache.set(new CacheEntry<>(value, Instant.now(), false));
                }
            });
        }

        if (!entry.isStale(maxFreshAge)) {
            // Fresh cache hit
            return CompletableFuture.completedFuture(entry.value);
        }

        // Stale: serve cached value and schedule background refresh
        if (!refreshInFlight) {
            scheduleBackgroundRefresh(freshFetcher);
        }

        if (telemetry != null) telemetry.recordStaleCacheServed();
        publishStaleCacheEvent(entry);
        log.debug("Stale cache served for {} [{}] age={}", cacheName, endpoint, entry.age());
        return CompletableFuture.completedFuture(entry.value);
    }

    /**
     * Explicitly stores a fresh value in the cache (e.g., after a successful fetch).
     *
     * @param value the fresh value to cache
     */
    public void store(@NotNull T value) {
        cache.set(new CacheEntry<>(value, Instant.now(), false));
    }

    /** Marks the current cache entry as stale without clearing it. */
    public void markStale() {
        CacheEntry<T> entry = cache.get();
        if (entry != null && !entry.stale) {
            cache.set(entry.markStale());
        }
    }

    /** Returns true if the cache has any entry (fresh or stale). */
    public boolean hasCachedValue() {
        return cache.get() != null;
    }

    /** Returns the age of the current cache entry, or {@link Duration#ZERO} if empty. */
    public @NotNull Duration cacheAge() {
        CacheEntry<T> entry = cache.get();
        return entry == null ? Duration.ZERO : entry.age();
    }

    /** Returns whether the current cache is stale. */
    public boolean isStale() {
        CacheEntry<T> entry = cache.get();
        return entry != null && entry.isStale(maxFreshAge);
    }

    /** Shuts down background refresh executor. */
    public void shutdown() {
        backgroundRefresher.shutdownNow();
    }

    // ─────────────────────────────────────────────────────────────────────────────────

    private void scheduleBackgroundRefresh(@NotNull Supplier<CompletableFuture<T>> freshFetcher) {
        refreshInFlight = true;
        backgroundRefresher.schedule(() -> {
            try {
                freshFetcher.get().whenComplete((value, ex) -> {
                    refreshInFlight = false;
                    if (ex == null && value != null) {
                        cache.set(new CacheEntry<>(value, Instant.now(), false));
                        log.debug("Stale cache refreshed for {}", cacheName);
                    } else if (ex != null) {
                        log.debug("Background cache refresh failed for {}: {}", cacheName, ex.getMessage());
                    }
                });
            } catch (Exception e) {
                refreshInFlight = false;
                log.debug("Background cache refresh error for {}: {}", cacheName, e.getMessage());
            }
        }, 5, TimeUnit.SECONDS);
    }

    private void publishStaleCacheEvent(@NotNull CacheEntry<T> entry) {
        if (eventBus == null) return;
        try {
            Map<String, Object> meta = Map.of(
                    "cacheName", cacheName,
                    "endpoint", endpoint.name(),
                    "ageSeconds", entry.age().toSeconds()
            );
            eventBus.publishAsync(AgentEvent.of(AgentEvent.STALE_CACHE_SERVED, "StaleCacheManager", cacheName, meta));
        } catch (Exception ignored) {
        }
    }

    /**
     * Internal cache entry.
     *
     * @param <V> value type
     */
    private record CacheEntry<V>(
            @NotNull V value,
            @NotNull Instant storedAt,
            boolean stale
    ) {
        boolean isStale(@NotNull Duration maxAge) {
            return stale || age().compareTo(maxAge) > 0;
        }

        Duration age() {
            return Duration.between(storedAt, Instant.now());
        }

        CacheEntry<V> markStale() {
            return new CacheEntry<>(value, storedAt, true);
        }
    }
}
