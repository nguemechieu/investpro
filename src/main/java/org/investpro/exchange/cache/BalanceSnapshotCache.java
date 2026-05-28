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
import java.util.function.Supplier;

/**
 * Typed stale-safe cache for account balance snapshots.
 *
 * <p>Balance data is less time-sensitive than pricing, so the default max-fresh
 * age is 30 seconds. Stale balances are served under the same contract as
 * {@link MarketSnapshotCache} to prevent cascade failures during account
 * endpoint outages.
 *
 * <p>The snapshot is typed as {@code Map<String, java.math.BigDecimal>}
 * (currency symbol → balance), matching the typical return type of exchange
 * balance APIs.
 */
public final class BalanceSnapshotCache {

    private static final Duration DEFAULT_MAX_FRESH_AGE = Duration.ofSeconds(30);

    private final StaleCacheManager<Map<String, java.math.BigDecimal>> delegate;

    public BalanceSnapshotCache(
            @NotNull String exchangeName,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry
    ) {
        this(exchangeName, DEFAULT_MAX_FRESH_AGE, eventBus, telemetry);
    }

    public BalanceSnapshotCache(
            @NotNull String exchangeName,
            @NotNull Duration maxFreshAge,
            @Nullable AgentEventBus eventBus,
            @Nullable ExchangeTelemetryEngine telemetry
    ) {
        this.delegate = new StaleCacheManager<>(
                exchangeName + "-balance-snapshot",
                EndpointType.ACCOUNT,
                maxFreshAge,
                eventBus,
                telemetry
        );
    }

    /**
     * Returns a fresh or stale balance map, scheduling a background refresh if stale.
     *
     * @param freshFetcher supplier that fetches a fresh balance map from the exchange
     * @return future resolving to the (possibly stale) balance map
     */
    public CompletableFuture<Map<String, java.math.BigDecimal>> get(
            @NotNull Supplier<CompletableFuture<Map<String, java.math.BigDecimal>>> freshFetcher
    ) {
        return delegate.getOrServeStale(freshFetcher);
    }

    /** Forces the cache to be invalidated on the next access. */
    public void invalidate() {
        delegate.invalidate();
    }
}
