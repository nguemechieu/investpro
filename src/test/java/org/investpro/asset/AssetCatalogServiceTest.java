package org.investpro.asset;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class AssetCatalogServiceTest {
    @Test
    void loadsLocalAssetsImmediatelyAndAddsReversedStellarDisplayPair() {
        InMemoryLocalAssetRepository repository = new InMemoryLocalAssetRepository();
        repository.upsert(new AssetCatalogEntry(
                null, ExchangeId.STELLAR, "BTC/USDC", "BTC/USDC", "BTC", "USDC",
                AssetType.STELLAR_ASSET, AssetStatus.ACTIVE, TradabilityStatus.UNKNOWN,
                false, false, true, false, true, true, null, null, null, null,
                null, null, "GBTCISSUER", "anchor.example", true, true, true, true,
                true, false, Instant.now(), Instant.now(), "{}"));
        AssetRefreshScheduler scheduler = new AssetRefreshScheduler(
                repository,
                (exchange, exchangeId) -> java.util.concurrent.CompletableFuture.completedFuture(List.of()),
                new AssetCatalogMergeService(),
                Executors.newSingleThreadScheduledExecutor(),
                event -> {});

        AssetCatalogService service = new AssetCatalogService(repository, scheduler);

        List<String> symbols = service.loadMarketWatchPairs(ExchangeId.STELLAR).stream()
                .map(pair -> pair.toString('/'))
                .toList();

        assertTrue(symbols.contains("BTC/USDC"));
        assertTrue(symbols.contains("USDC/BTC"));
    }
}
