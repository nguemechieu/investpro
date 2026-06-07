package org.investpro.news;

import java.util.Map;
import java.util.Set;

public record NewsSourceDefinition(
        String id,
        String name,
        String url,
        NewsSourceType type,
        boolean enabled,
        int refreshIntervalMinutes,
        Set<NewsCategory> categories,
        Map<String, String> metadata) {

    public NewsSourceDefinition {
        categories = categories == null ? Set.of() : Set.copyOf(categories);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
