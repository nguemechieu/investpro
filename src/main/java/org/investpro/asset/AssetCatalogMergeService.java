package org.investpro.asset;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

public final class AssetCatalogMergeService {
    private static final Duration MISSING_CONFIRMATION_WINDOW = Duration.ofDays(7);

    public AssetCatalogMergeResult merge(
            LocalAssetRepository repository,
            ExchangeId exchangeId,
            List<AssetCatalogEntry> remoteAssets,
            Instant refreshedAt,
            Consumer<AssetCatalogEvent> eventConsumer) {
        Objects.requireNonNull(repository, "repository must not be null");
        Instant now = refreshedAt == null ? Instant.now() : refreshedAt;
        List<AssetCatalogEntry> existing = repository.findByExchange(exchangeId);
        Map<String, AssetCatalogEntry> existingById = new LinkedHashMap<>();
        for (AssetCatalogEntry asset : existing) {
            existingById.put(asset.id(), asset);
        }

        List<AssetCatalogEntry> added = new ArrayList<>();
        List<AssetCatalogEntry> updated = new ArrayList<>();
        List<AssetCatalogEntry> reactivated = new ArrayList<>();
        List<AssetCatalogEntry> toPersist = new ArrayList<>();

        for (AssetCatalogEntry remote : remoteAssets == null ? List.<AssetCatalogEntry>of() : remoteAssets) {
            AssetCatalogEntry normalized = remote.withStatus(AssetStatus.ACTIVE, now, now);
            AssetCatalogEntry previous = existingById.remove(normalized.id());
            if (previous == null) {
                added.add(normalized);
                toPersist.add(normalized);
                continue;
            }

            boolean wasInactive = previous.status() == AssetStatus.INACTIVE
                    || previous.status() == AssetStatus.DELISTED
                    || previous.status() == AssetStatus.SUSPENDED;
            if (wasInactive) {
                reactivated.add(normalized);
            } else if (materiallyChanged(previous, normalized)) {
                updated.add(normalized);
            }
            toPersist.add(normalized);
        }

        List<AssetCatalogEntry> inactivated = new ArrayList<>();
        for (AssetCatalogEntry missing : existingById.values()) {
            if (missing.manuallyAdded()) {
                continue;
            }
            if (missing.status() == AssetStatus.DELISTED) {
                continue;
            }
            Instant lastSeen = missing.lastSeenAt() == null ? Instant.EPOCH : missing.lastSeenAt();
            if (Duration.between(lastSeen, now).compareTo(MISSING_CONFIRMATION_WINDOW) >= 0) {
                AssetCatalogEntry inactive = missing.withStatus(AssetStatus.INACTIVE, lastSeen, now);
                inactivated.add(inactive);
                toPersist.add(inactive);
            }
        }

        repository.upsertAll(toPersist);
        AssetCatalogEvent event = new AssetCatalogEvent(exchangeId, added, updated, inactivated, reactivated, now);
        if (event.totalChanges() > 0 && eventConsumer != null) {
            eventConsumer.accept(event);
        }
        return new AssetCatalogMergeResult(added, updated, inactivated, reactivated);
    }

    private boolean materiallyChanged(AssetCatalogEntry left, AssetCatalogEntry right) {
        return !Objects.equals(left.rawExchangeSymbol(), right.rawExchangeSymbol())
                || !Objects.equals(left.baseAsset(), right.baseAsset())
                || !Objects.equals(left.quoteAsset(), right.quoteAsset())
                || left.assetType() != right.assetType()
                || left.requiresTrustline() != right.requiresTrustline()
                || left.verified() != right.verified()
                || left.supportsMarketOrders() != right.supportsMarketOrders()
                || left.supportsLimitOrders() != right.supportsLimitOrders()
                || left.supportsStopOrders() != right.supportsStopOrders()
                || left.supportsLiveTrading() != right.supportsLiveTrading()
                || left.supportsPaperTrading() != right.supportsPaperTrading();
    }
}
