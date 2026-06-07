package org.investpro.news;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class NewsDeduplicationService {

    public List<CryptoNewsItem> markDuplicates(List<CryptoNewsItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        List<CryptoNewsItem> result = new ArrayList<>();
        Map<String, String> seen = new HashMap<>();
        for (CryptoNewsItem item : items) {
            String key = key(item);
            String originalId = seen.putIfAbsent(key, item.id());
            result.add(originalId == null ? item : item.asDuplicateOf(originalId));
        }
        return result;
    }

    private String key(CryptoNewsItem item) {
        String normalizedTitle = item.title() == null ? "" : item.title()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", "")
                .replaceAll("\\s+", " ")
                .trim();
        String day = item.publishedAt() == null ? "" : item.publishedAt().format(DateTimeFormatter.ISO_LOCAL_DATE);
        return normalizedTitle + "|" + day;
    }
}
