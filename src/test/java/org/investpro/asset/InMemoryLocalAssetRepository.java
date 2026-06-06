package org.investpro.asset;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class InMemoryLocalAssetRepository implements LocalAssetRepository {
    private final Map<String, AssetCatalogEntry> assets = new LinkedHashMap<>();
    private final Map<ExchangeId, Instant> refreshes = new LinkedHashMap<>();

    @Override
    public List<AssetCatalogEntry> findByExchange(ExchangeId exchangeId) {
        return assets.values().stream()
                .filter(asset -> asset.exchangeId() == exchangeId)
                .toList();
    }

    @Override
    public Optional<AssetCatalogEntry> findById(String id) {
        return Optional.ofNullable(assets.get(id));
    }

    @Override
    public void upsert(AssetCatalogEntry asset) {
        assets.put(asset.id(), asset);
    }

    @Override
    public void upsertAll(List<AssetCatalogEntry> assets) {
        for (AssetCatalogEntry asset : new ArrayList<>(assets)) {
            upsert(asset);
        }
    }

    @Override
    public Optional<Instant> lastRefreshAt(ExchangeId exchangeId) {
        return Optional.ofNullable(refreshes.get(exchangeId));
    }

    @Override
    public void recordRefresh(ExchangeId exchangeId, Instant refreshedAt, String status, String message) {
        if ("SUCCESS".equals(status)) {
            refreshes.put(exchangeId, refreshedAt);
        }
    }
}
