package org.investpro.asset;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.Exchange;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
public final class AssetRefreshScheduler {
    private final LocalAssetRepository repository;
    private final ExchangeAssetDiscoveryService discoveryService;
    private final AssetCatalogMergeService mergeService;
    private final ScheduledExecutorService executor;
    private final Consumer<AssetCatalogEvent> eventConsumer;
    private final Set<ExchangeId> running = ConcurrentHashMap.newKeySet();
    private final Map<ExchangeId, Duration> ttlByExchange = Map.of(
            ExchangeId.COINBASE, Duration.ofHours(6),
            ExchangeId.BINANCE_US, Duration.ofHours(6),
            ExchangeId.OANDA, Duration.ofHours(24),
            ExchangeId.ALPACA, Duration.ofHours(24),
            ExchangeId.STELLAR, Duration.ofHours(12),
            ExchangeId.IBKR, Duration.ofDays(30),
            ExchangeId.UNKNOWN, Duration.ofHours(24));

    public AssetRefreshScheduler(
            LocalAssetRepository repository,
            ExchangeAssetDiscoveryService discoveryService,
            AssetCatalogMergeService mergeService,
            ScheduledExecutorService executor,
            Consumer<AssetCatalogEvent> eventConsumer) {
        this.repository = repository;
        this.discoveryService = discoveryService;
        this.mergeService = mergeService;
        this.executor = executor;
        this.eventConsumer = eventConsumer;
    }

    public boolean isStaleOrMissing(ExchangeId exchangeId) {
        if (repository.findByExchange(exchangeId).isEmpty()) {
            return true;
        }
        Optional<Instant> lastRefresh = repository.lastRefreshAt(exchangeId);
        if (lastRefresh.isEmpty()) {
            return true;
        }
        Duration ttl = ttlByExchange.getOrDefault(exchangeId, Duration.ofHours(24));
        return lastRefresh.get().plus(ttl).isBefore(Instant.now());
    }

    public CompletableFuture<AssetCatalogMergeResult> refreshIfStale(Exchange exchange, ExchangeId exchangeId) {
        return isStaleOrMissing(exchangeId)
                ? refresh(exchange, exchangeId, false)
                : CompletableFuture.completedFuture(new AssetCatalogMergeResult(List.of(), List.of(), List.of(), List.of()));
    }

    public CompletableFuture<AssetCatalogMergeResult> refresh(Exchange exchange, ExchangeId exchangeId, boolean manual) {
        if (!running.add(exchangeId)) {
            log.info("asset.catalog.refresh.skipped exchange={} reason=already-running", exchangeId.id());
            return CompletableFuture.completedFuture(new AssetCatalogMergeResult(List.of(), List.of(), List.of(), List.of()));
        }
        return attempt(exchange, exchangeId, manual, 1)
                .whenComplete((result, error) -> running.remove(exchangeId));
    }

    private CompletableFuture<AssetCatalogMergeResult> attempt(
            Exchange exchange,
            ExchangeId exchangeId,
            boolean manual,
            int attempt) {
        Instant startedAt = Instant.now();
        log.info("asset.catalog.refresh.start exchange={} manual={} attempt={}", exchangeId.id(), manual, attempt);
        return discoveryService.discover(exchange, exchangeId)
                .thenApply(remoteAssets -> {
                    AssetCatalogMergeResult result = mergeService.merge(
                            repository,
                            exchangeId,
                            remoteAssets,
                            Instant.now(),
                            eventConsumer);
                    repository.recordRefresh(exchangeId, Instant.now(), "SUCCESS",
                            "assets=%d added=%d updated=%d inactive=%d reactivated=%d durationMs=%d".formatted(
                                    remoteAssets.size(),
                                    result.added().size(),
                                    result.updated().size(),
                                    result.inactivated().size(),
                                    result.reactivated().size(),
                                    Duration.between(startedAt, Instant.now()).toMillis()));
                    log.info("asset.catalog.refresh.success exchange={} assets={} added={} updated={} inactive={} reactivated={} durationMs={}",
                            exchangeId.id(), remoteAssets.size(), result.added().size(), result.updated().size(),
                            result.inactivated().size(), result.reactivated().size(),
                            Duration.between(startedAt, Instant.now()).toMillis());
                    return result;
                })
                .exceptionallyCompose(error -> {
                    log.warn("asset.catalog.refresh.failure exchange={} attempt={} reason={}",
                            exchangeId.id(), attempt, rootMessage(error));
                    if (attempt >= 3) {
                        repository.recordRefresh(exchangeId, Instant.now(), "FAILURE", rootMessage(error));
                        return CompletableFuture.failedFuture(error);
                    }
                    CompletableFuture<AssetCatalogMergeResult> retry = new CompletableFuture<>();
                    long delaySeconds = (long) Math.pow(2, attempt);
                    executor.schedule(() -> attempt(exchange, exchangeId, manual, attempt + 1)
                            .whenComplete((result, retryError) -> {
                                if (retryError != null) {
                                    retry.completeExceptionally(retryError);
                                } else {
                                    retry.complete(result);
                                }
                            }), delaySeconds, TimeUnit.SECONDS);
                    return retry;
                });
    }

    private static String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null && cursor.getCause() != null) {
            cursor = cursor.getCause();
        }
        return cursor == null || cursor.getMessage() == null ? "unknown" : cursor.getMessage();
    }
}
