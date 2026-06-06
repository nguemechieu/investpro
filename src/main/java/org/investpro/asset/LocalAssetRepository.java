package org.investpro.asset;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface LocalAssetRepository {
    List<AssetCatalogEntry> findByExchange(ExchangeId exchangeId);

    Optional<AssetCatalogEntry> findById(String id);

    void upsert(AssetCatalogEntry asset);

    void upsertAll(List<AssetCatalogEntry> assets);

    Optional<Instant> lastRefreshAt(ExchangeId exchangeId);

    void recordRefresh(ExchangeId exchangeId, Instant refreshedAt, String status, String message);
}
