package org.investpro.asset;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AssetRefreshSchedulerTest {
    @Test
    void detectsMissingAndStaleCatalogs() {
        InMemoryLocalAssetRepository repository = new InMemoryLocalAssetRepository();
        AssetRefreshScheduler scheduler = new AssetRefreshScheduler(
                repository,
                (exchange, exchangeId) -> java.util.concurrent.CompletableFuture.completedFuture(List.of()),
                new AssetCatalogMergeService(),
                Executors.newSingleThreadScheduledExecutor(),
                event -> {});

        assertTrue(scheduler.isStaleOrMissing(ExchangeId.COINBASE));
        repository.recordRefresh(ExchangeId.COINBASE, Instant.now(), "SUCCESS", "");
        assertTrue(scheduler.isStaleOrMissing(ExchangeId.COINBASE));
    }

    @Test
    void preventsDuplicateRefreshJobsForSameExchange() throws Exception {
        InMemoryLocalAssetRepository repository = new InMemoryLocalAssetRepository();
        AtomicInteger discoveries = new AtomicInteger();
        var executor = Executors.newSingleThreadScheduledExecutor();
        AssetRefreshScheduler scheduler = new AssetRefreshScheduler(
                repository,
                (exchange, exchangeId) -> {
                    discoveries.incrementAndGet();
                    return new java.util.concurrent.CompletableFuture<>();
                },
                new AssetCatalogMergeService(),
                executor,
                event -> {});

        scheduler.refresh(null, ExchangeId.COINBASE, true);
        scheduler.refresh(null, ExchangeId.COINBASE, true).get(1, TimeUnit.SECONDS);

        assertEquals(1, discoveries.get());
        executor.shutdownNow();
    }
}
