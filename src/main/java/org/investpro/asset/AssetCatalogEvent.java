package org.investpro.asset;

import java.time.Instant;
import java.util.List;

public record AssetCatalogEvent(
        ExchangeId exchangeId,
        List<AssetCatalogEntry> added,
        List<AssetCatalogEntry> changed,
        List<AssetCatalogEntry> inactive,
        List<AssetCatalogEntry> reactivated,
        Instant occurredAt
) {
    public AssetCatalogEvent {
        added = added == null ? List.of() : List.copyOf(added);
        changed = changed == null ? List.of() : List.copyOf(changed);
        inactive = inactive == null ? List.of() : List.copyOf(inactive);
        reactivated = reactivated == null ? List.of() : List.copyOf(reactivated);
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public int totalChanges() {
        return added.size() + changed.size() + inactive.size() + reactivated.size();
    }
}
