package org.investpro.news;

import java.util.List;

public record NewsContext(
        String symbol,
        List<CryptoNewsItem> latestNews,
        double averageSentiment,
        NewsUrgency highestUrgency,
        boolean criticalNegativeNews,
        boolean positiveCatalyst,
        List<String> warnings) {

    public NewsContext {
        latestNews = latestNews == null ? List.of() : List.copyOf(latestNews);
        highestUrgency = highestUrgency == null ? NewsUrgency.LOW : highestUrgency;
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static NewsContext empty(String symbol) {
        return new NewsContext(symbol, List.of(), 0.0, NewsUrgency.LOW, false, false, List.of());
    }
}
