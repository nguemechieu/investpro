package org.investpro.strategy.lab;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache for pre-computed technical indicator values.
 *
 * <p>Many strategies calculate the same indicator on the same candle window
 * (e.g., EMA-20 on BTC/USD 1h). Without a cache this computation is repeated
 * hundreds of times during a batch backtest session, wasting CPU.
 *
 * <p>Cache entries are keyed by a composite key:
 * <pre>
 *   {symbol}|{timeframeCode}|{indicatorName}|{param1},{param2},...
 * </pre>
 *
 * <p>Examples of keys:
 * <ul>
 *   <li>{@code BTC/USD|1h|EMA|20}</li>
 *   <li>{@code EUR/USD|4h|RSI|14}</li>
 *   <li>{@code ETH/USD|1d|MACD|12,26,9}</li>
 *   <li>{@code BTC/USD|1h|BOLLINGER|20,2.0}</li>
 * </ul>
 *
 * <p>The cache does <em>not</em> implement eviction on its own.  Callers
 * should call {@link #clearForSymbol(String)} when candle data changes, or
 * {@link #clearAll()} between test sessions.
 *
 * <p>All public methods are safe to call from multiple threads simultaneously.
 */
public final class IndicatorCache {

    /** Singleton – shared across all strategy runners in the same session. */
    private static final IndicatorCache INSTANCE = new IndicatorCache();

    /** Backing store: key → computed values. */
    private final Map<String, List<Double>> cache = new ConcurrentHashMap<>();

    private IndicatorCache() {}

    /** Returns the shared singleton instance. */
    public static IndicatorCache getInstance() {
        return INSTANCE;
    }

    // ─── Key helpers ─────────────────────────────────────────────────────────

    /**
     * Builds a canonical cache key.
     *
     * @param symbol        trading symbol, e.g. "BTC/USD"
     * @param timeframeCode timeframe code, e.g. "1h"
     * @param indicator     indicator name, e.g. "EMA", "RSI"
     * @param params        indicator parameters, e.g. "20" or "12,26,9"
     * @return canonical key string
     */
    public static String key(String symbol, String timeframeCode, String indicator, String params) {
        return symbol + "|" + timeframeCode + "|" + indicator.toUpperCase() + "|" + params;
    }

    // ─── Read / write ─────────────────────────────────────────────────────────

    /**
     * Returns cached indicator values, or {@code null} if not yet computed.
     *
     * @param key canonical cache key (see {@link #key})
     * @return immutable view of values or {@code null}
     */
    public List<Double> get(String key) {
        return cache.get(key);
    }

    /**
     * Checks whether a cached entry exists for the given key.
     */
    public boolean contains(String key) {
        return cache.containsKey(key);
    }

    /**
     * Stores computed indicator values under the given key.
     *
     * <p>The list is defensively copied so callers may continue mutating
     * their original list without affecting the cache.
     *
     * @param key    canonical cache key
     * @param values computed indicator values (same length as the candle window)
     */
    public void put(String key, List<Double> values) {
        if (key != null && values != null) {
            cache.put(key, List.copyOf(values));
        }
    }

    /**
     * Returns cached values if present, otherwise computes them using
     * {@code supplier}, stores the result, and returns it.
     *
     * @param key      canonical cache key
     * @param supplier computes the values if not cached
     * @return cached or freshly computed values
     */
    public List<Double> computeIfAbsent(String key, java.util.function.Supplier<List<Double>> supplier) {
        return cache.computeIfAbsent(key, k -> {
            List<Double> computed = supplier.get();
            return computed != null ? List.copyOf(computed) : List.of();
        });
    }

    // ─── Invalidation ────────────────────────────────────────────────────────

    /**
     * Removes all cache entries associated with the given symbol.
     * Call this when the candle data for a symbol is refreshed.
     */
    public void clearForSymbol(String symbol) {
        String prefix = symbol + "|";
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /**
     * Removes all entries from the cache.
     * Call this at the start of a new test session to ensure fresh calculations.
     */
    public void clearAll() {
        cache.clear();
    }

    /** Returns the number of cached indicator series. */
    public int size() {
        return cache.size();
    }
}
