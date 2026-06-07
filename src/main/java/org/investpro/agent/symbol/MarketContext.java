package org.investpro.agent.symbol;

import org.investpro.news.NewsContext;

import java.time.LocalDateTime;
import java.util.Map;

public record MarketContext(
        Object latestTicker,
        Object latestOrderBook,
        NewsContext newsContext,
        LocalDateTime createdAt,
        Map<String, String> metadata) {

    public MarketContext {
        createdAt = createdAt == null ? LocalDateTime.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
