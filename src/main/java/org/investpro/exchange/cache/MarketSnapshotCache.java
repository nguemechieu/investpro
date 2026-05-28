package org.investpro.exchange.cache;

import org.investpro.core.agents.AgentEventBus;
import org.investpro.exchange.normalization.NormalizedMarketSnapshot;
import org.investpro.exchange.resilience.ExchangeTelemetryEngine;
import org.investpro.exchange.resilience.StaleCacheManager;
import org.investpro.exchange.resilience.model.EndpointType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

/**
 * Typed stale-safe cache for {@link NormalizedMarketSnapshot}.
 *
 * <p>Wraps {@link StaleCacheManager} with the PRICING endpoint type and a
 * configurable max-fresh age. Returns stale snapshots rather than failing
 * when the exchange is temporarily unavailable.
 */
public final class MarketSnapshotCache {

    private static final Duration DEFAULT_MAX_FRESH_AGE = Duration.ofSeconds(5);

    private final StaleCacheManager<NormalizedMarketSnapshot> delegate;

    public MarketSnapshotCache(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry
    ) {
        this(exchangeName, DEFAULT_MAX_FRESH_AGE, eventBus, telemetry);
    }

    public MarketSnapshotCache(
            @NotNull String exchangeName,
            @NotNull Duration maxFreshAge,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry
    ) {
        this.delegate = new StaleCacheManager<>(
                exchangeName + "-market-snapshot",
                EndpointType.PRICING,
                maxFreshAge,
                eventBus,
                telemetry
        );
    }

    /**
     * Returns a fresh or stale snapshot, scheduling a background refresh if stale.
     *
     * @param freshFetcher supplier that fetches a fresh snapshot from the exchange
     * @return future resolving to the (possibly stale) snapshot
     */
    public CompletableFuture<NormalizedMarketSnapshot> get(
            @NotNull Supplier<CompletableFuture<NormalizedMarketSnapshot>> freshFetcher
    ) {
        return delegate.getOrServeStale(freshFetcher);
    }

    /** Forces the cache to be invalidated on the next access. */
    public void invalidate() {
        delegate.invalidate();
    }
}
