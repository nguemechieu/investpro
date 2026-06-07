package org.investpro.news;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsRepository {
    void saveAll(List<CryptoNewsItem> items);

    List<CryptoNewsItem> findLatest(int limit);

    List<CryptoNewsItem> findBySymbol(String symbol, int limit);

    List<CryptoNewsItem> findByCategory(NewsCategory category, int limit);

    List<CryptoNewsItem> findByUrgency(NewsUrgency urgency, int limit);

    List<CryptoNewsItem> findSince(LocalDateTime since);

    void markRead(String newsId);

    void markImportant(String newsId, boolean important);
}
