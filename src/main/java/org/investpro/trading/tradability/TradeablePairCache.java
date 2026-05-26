package org.investpro.trading.tradability;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class TradeablePairCache {

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(15);
    private static final TradeablePairCache INSTANCE = new TradeablePairCache();

    private final ConcurrentHashMap<String, CacheEntry> store = new ConcurrentHashMap<>();

    private TradeablePairCache() {}

    public static TradeablePairCache getInstance() {
        return INSTANCE;
    }

    public record CacheEntry(List<TradePair> pairs, Instant refreshedAt, boolean stale) {}

    private static String key(String exchangeId, String accountId) {
        return exchangeId + ":" + (accountId == null ? "" : accountId);
    }

    public Optional<List<TradePair>> get(String exchangeId, String accountId) {
        CacheEntry entry = store.get(key(exchangeId, accountId));
        if (entry == null || isExpired(entry)) return Optional.empty();
        return Optional.of(entry.pairs());
    }

    public void put(String exchangeId, String accountId, List<TradePair> pairs) {
        store.put(key(exchangeId, accountId), new CacheEntry(List.copyOf(pairs), Instant.now(), false));
    }

    public void forceInvalidate(String exchangeId, String accountId) {
        store.remove(key(exchangeId, accountId));
    }

    public boolean isStale(String exchangeId, String accountId) {
        CacheEntry entry = store.get(key(exchangeId, accountId));
        return entry == null || isExpired(entry);
    }

    /**
     * Returns the cached pairs even if they are stale, but logs a warning in that case.
     */
    public Optional<List<TradePair>> getWithStaleWarning(String exchangeId, String accountId) {
        CacheEntry entry = store.get(key(exchangeId, accountId));
        if (entry == null) return Optional.empty();
        if (isExpired(entry)) {
            log.warn("Returning stale tradeable-pair cache for exchange='{}' account='{}' (age={})",
                    exchangeId, accountId, Duration.between(entry.refreshedAt(), Instant.now()));
            // mark entry as stale
            store.put(key(exchangeId, accountId), new CacheEntry(entry.pairs(), entry.refreshedAt(), true));
        }
        return Optional.of(entry.pairs());
    }

    private boolean isExpired(CacheEntry entry) {
        return Duration.between(entry.refreshedAt(), Instant.now()).compareTo(DEFAULT_TTL) > 0;
    }
}
