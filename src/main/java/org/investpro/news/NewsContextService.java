package org.investpro.news;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class NewsContextService {

    private final NewsRepository repository;

    public NewsContextService(NewsRepository repository) {
        this.repository = repository;
    }

    public NewsContext getContextForSymbol(String symbol, Duration lookback) {
        if (repository == null || symbol == null || symbol.isBlank()) {
            return NewsContext.empty(symbol);
        }
        Duration safeLookback = lookback == null ? Duration.ofHours(24) : lookback;
        LocalDateTime since = LocalDateTime.now().minus(safeLookback);
        String normalized = symbol.toUpperCase(Locale.ROOT);
        List<CryptoNewsItem> latest = repository.findSince(since).stream()
                .filter(item -> item.mentionedSymbols().contains(normalized))
                .sorted(Comparator.comparing(CryptoNewsItem::publishedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(20)
                .toList();
        if (latest.isEmpty()) {
            return NewsContext.empty(normalized);
        }
        double averageSentiment = latest.stream().mapToDouble(CryptoNewsItem::sentimentScore).average().orElse(0.0);
        NewsUrgency highestUrgency = latest.stream()
                .map(CryptoNewsItem::urgency)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(NewsUrgency.LOW);
        boolean criticalNegative = latest.stream().anyMatch(item ->
                item.urgency() == NewsUrgency.CRITICAL
                        && (item.impact() == NewsImpact.NEGATIVE || item.impact() == NewsImpact.VERY_NEGATIVE));
        boolean positiveCatalyst = latest.stream().anyMatch(item ->
                item.impact() == NewsImpact.POSITIVE || item.impact() == NewsImpact.VERY_POSITIVE);
        List<String> warnings = criticalNegative
                ? List.of("Recent negative news detected for " + normalized + ". Entry requires confirmation.")
                : List.of();
        return new NewsContext(normalized, latest, averageSentiment, highestUrgency, criticalNegative, positiveCatalyst, warnings);
    }

    public boolean hasCriticalNegativeNews(String symbol, Duration lookback) {
        return getContextForSymbol(symbol, lookback).criticalNegativeNews();
    }

    public boolean hasPositiveCatalyst(String symbol, Duration lookback) {
        return getContextForSymbol(symbol, lookback).positiveCatalyst();
    }
}
