package org.investpro.asset;

import java.util.List;

public record AssetCatalogMergeResult(
        List<AssetCatalogEntry> added,
        List<AssetCatalogEntry> updated,
        List<AssetCatalogEntry> inactivated,
        List<AssetCatalogEntry> reactivated
) {
    public AssetCatalogMergeResult {
        added = added == null ? List.of() : List.copyOf(added);
        updated = updated == null ? List.of() : List.copyOf(updated);
        inactivated = inactivated == null ? List.of() : List.copyOf(inactivated);
        reactivated = reactivated == null ? List.of() : List.copyOf(reactivated);
    }

    public int changedCount() {
        return added.size() + updated.size() + inactivated.size() + reactivated.size();
    }
}
