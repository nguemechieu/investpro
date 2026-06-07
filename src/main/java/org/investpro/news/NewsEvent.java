package org.investpro.news;

import java.time.LocalDateTime;

public record NewsEvent(
        NewsEventType type,
        CryptoNewsItem item,
        NewsSourceDefinition source,
        String message,
        LocalDateTime createdAt) {

    public static NewsEvent of(NewsEventType type, CryptoNewsItem item, NewsSourceDefinition source, String message) {
        return new NewsEvent(type, item, source, message, LocalDateTime.now());
    }
}
