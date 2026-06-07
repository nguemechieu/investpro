package org.investpro.news;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryNewsRepository implements NewsRepository {

    private final Map<String, CryptoNewsItem> items = new LinkedHashMap<>();
    private final Set<String> readIds = ConcurrentHashMap.newKeySet();
    private final Set<String> importantIds = ConcurrentHashMap.newKeySet();

    @Override
    public synchronized void saveAll(List<CryptoNewsItem> newItems) {
        if (newItems == null) {
            return;
        }
        for (CryptoNewsItem item : newItems) {
            if (item != null && item.id() != null) {
                items.put(item.id(), item);
            }
        }
    }

    @Override
    public synchronized List<CryptoNewsItem> findLatest(int limit) {
        return sorted().stream().limit(Math.max(0, limit)).toList();
    }

    @Override
    public synchronized List<CryptoNewsItem> findBySymbol(String symbol, int limit) {
        String target = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        return sorted().stream()
                .filter(item -> item.mentionedSymbols().contains(target))
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public synchronized List<CryptoNewsItem> findByCategory(NewsCategory category, int limit) {
        return sorted().stream()
                .filter(item -> item.category() == category)
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public synchronized List<CryptoNewsItem> findByUrgency(NewsUrgency urgency, int limit) {
        return sorted().stream()
                .filter(item -> item.urgency() == urgency)
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public synchronized List<CryptoNewsItem> findSince(LocalDateTime since) {
        LocalDateTime cutoff = since == null ? LocalDateTime.MIN : since;
        return sorted().stream()
                .filter(item -> item.publishedAt() != null && !item.publishedAt().isBefore(cutoff))
                .toList();
    }

    @Override
    public void markRead(String newsId) {
        if (newsId != null) {
            readIds.add(newsId);
        }
    }

    @Override
    public void markImportant(String newsId, boolean important) {
        if (newsId == null) {
            return;
        }
        if (important) {
            importantIds.add(newsId);
        } else {
            importantIds.remove(newsId);
        }
    }

    public boolean isRead(String newsId) {
        return readIds.contains(newsId);
    }

    public boolean isImportant(String newsId) {
        return importantIds.contains(newsId);
    }

    private List<CryptoNewsItem> sorted() {
        return new ArrayList<>(items.values()).stream()
                .sorted(Comparator.comparing(CryptoNewsItem::publishedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .toList();
    }
}
