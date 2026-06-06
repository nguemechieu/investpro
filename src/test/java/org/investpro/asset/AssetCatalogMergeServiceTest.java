package org.investpro.asset;

import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AssetCatalogMergeServiceTest {
    private final AssetCatalogMergeService mergeService = new AssetCatalogMergeService();
    private final InMemoryLocalAssetRepository repository = new InMemoryLocalAssetRepository();

    @Test
    void upsertsNewRemoteAssets() throws Exception {
        AssetCatalogEntry btc = AssetCatalogEntry.fromTradePair(
                ExchangeId.COINBASE,
                TradePair.fromSymbol("BTC-USD"),
                Instant.parse("2026-06-05T00:00:00Z"));

        AssetCatalogMergeResult result = mergeService.merge(
                repository,
                ExchangeId.COINBASE,
                List.of(btc),
                Instant.parse("2026-06-05T00:00:00Z"),
                event -> {});

        assertEquals(1, result.added().size());
        assertEquals(1, repository.findByExchange(ExchangeId.COINBASE).size());
        assertEquals(AssetStatus.ACTIVE, repository.findByExchange(ExchangeId.COINBASE).getFirst().status());
    }

    @Test
    void marksMissingAssetsInactiveOnlyAfterConfirmationWindow() throws Exception {
        AssetCatalogEntry stale = AssetCatalogEntry.fromTradePair(
                ExchangeId.COINBASE,
                TradePair.fromSymbol("ETH-USD"),
                Instant.parse("2026-05-01T00:00:00Z"));
        repository.upsert(stale);

        AssetCatalogMergeResult result = mergeService.merge(
                repository,
                ExchangeId.COINBASE,
                List.of(),
                Instant.parse("2026-06-05T00:00:00Z"),
                event -> {});

        assertEquals(1, result.inactivated().size());
        assertEquals(AssetStatus.INACTIVE, repository.findByExchange(ExchangeId.COINBASE).getFirst().status());
    }

    @Test
    void preservesManuallyAddedStellarAssetsWhenMissingRemotely() throws Exception {
        AssetCatalogEntry manual = new AssetCatalogEntry(
                null, ExchangeId.STELLAR, "USDC/XLM", "USDC/XLM", "USDC", "XLM",
                AssetType.STELLAR_ASSET, AssetStatus.ACTIVE, TradabilityStatus.TRUSTLINE_REQUIRED,
                false, false, true, false, true, true, null, null, null, null,
                null, null, "GISSUER", "example.com", true, false, false, false,
                true, true, Instant.parse("2026-05-01T00:00:00Z"),
                Instant.parse("2026-05-01T00:00:00Z"), "{}");
        repository.upsert(manual);

        AssetCatalogMergeResult result = mergeService.merge(
                repository,
                ExchangeId.STELLAR,
                List.of(),
                Instant.parse("2026-06-05T00:00:00Z"),
                event -> {});

        assertEquals(0, result.inactivated().size());
        assertEquals(AssetStatus.ACTIVE, repository.findByExchange(ExchangeId.STELLAR).getFirst().status());
    }

    @Test
    void stellarCanonicalIdentityIncludesIssuer() {
        String first = AssetCatalogEntry.canonicalId(ExchangeId.STELLAR, "USDC/XLM", "USDC", "XLM", "GAAA");
        String second = AssetCatalogEntry.canonicalId(ExchangeId.STELLAR, "USDC/XLM", "USDC", "XLM", "GBBB");

        assertNotEquals(first, second);
    }
}
