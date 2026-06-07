package org.investpro.news;

import java.time.LocalDateTime;
import java.util.List;

public record NewsFetchResult(
        NewsSourceDefinition source,
        List<CryptoNewsItem> items,
        List<String> warnings,
        List<String> errors,
        LocalDateTime fetchedAt) {

    public NewsFetchResult {
        items = items == null ? List.of() : List.copyOf(items);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
        fetchedAt = fetchedAt == null ? LocalDateTime.now() : fetchedAt;
    }
}
