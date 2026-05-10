package org.investpro.market;

import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.MarketQuote;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe cache of current market state for all tradable instruments.
 * Central hub for market data aggregation from various providers.
 * <p>
 * Responsibilities:
 * - Store InstrumentMarketState for each TradePair
 * - Provide thread-safe updates from market feeds
 * - Aggregate multi-timeframe candles
 * - Support efficient lookups by TradePair
 */
@Slf4j
public class MarketDataCache {

    /**
     * Thread-safe map: TradePair symbol -> InstrumentMarketState
     */
    private final Map<String, InstrumentMarketState> cache = new ConcurrentHashMap<>();

    /**
     * Get or create market state for a TradePair.
     */
    @NotNull
    public InstrumentMarketState getOrCreateState(@NotNull TradePair tradePair) {
        String key = tradePair.toCompactSymbol();
        return cache.computeIfAbsent(key, k -> {
            InstrumentMarketState state = new InstrumentMarketState(tradePair);
            log.debug("Created market state for {}", tradePair);
            return state;
        });
    }

    /**
     * Get existing market state for a TradePair.
     */
    @NotNull
    public Optional<InstrumentMarketState> getState(@NotNull TradePair tradePair) {
        String key = tradePair.toCompactSymbol();
        return Optional.ofNullable(cache.get(key));
    }

    /**
     * Update market quote for a TradePair.
     */
    public void updateQuote(@NotNull TradePair tradePair, @Nullable MarketQuote quote) {
        InstrumentMarketState state = getOrCreateState(tradePair);
        state.updateQuote(quote);
        log.trace("Updated quote for {}: {}", tradePair, quote);
    }

    /**
     * Get current quote for a TradePair.
     */
    @NotNull
    public Optional<MarketQuote> getQuote(@NotNull TradePair tradePair) {
        return getState(tradePair).flatMap(state -> Optional.ofNullable(state.getQuote()));
    }

    /**
     * Update order book for a TradePair.
     */
    public void updateOrderBook(@NotNull TradePair tradePair, @Nullable OrderBook orderBook) {
        InstrumentMarketState state = getOrCreateState(tradePair);
        state.updateOrderBook(orderBook);
        log.trace("Updated order book for {}", tradePair);
    }

    /**
     * Get current order book for a TradePair.
     */
    @NotNull
    public Optional<OrderBook> getOrderBook(@NotNull TradePair tradePair) {
        return getState(tradePair).flatMap(state -> Optional.ofNullable(state.getOrderBook()));
    }

    /**
     * Update candles for a TradePair and timeframe.
     */
    public void updateCandles(
            @NotNull TradePair tradePair,
            @NotNull Timeframe timeframe,
            @NotNull List<CandleData> candles) {
        InstrumentMarketState state = getOrCreateState(tradePair);
        state.updateCandles(timeframe, candles);
        log.trace("Updated {} candles for {}: {} bars", timeframe, tradePair, candles.size());
    }

    /**
     * Get candles for a TradePair and timeframe.
     */
    @NotNull
    public List<CandleData> getCandles(
            @NotNull TradePair tradePair,
            @NotNull Timeframe timeframe) {
        return getState(tradePair)
                .map(state -> state.getCandles(timeframe))
                .orElse(Collections.emptyList());
    }

    /**
     * Get all candles across all timeframes for a TradePair.
     */
    @NotNull
    public Map<Timeframe, List<CandleData>> getAllCandles(@NotNull TradePair tradePair) {
        return getState(tradePair)
                .map(state -> Collections.unmodifiableMap(state.getCandlesByTimeframe()))
                .orElse(Collections.emptyMap());
    }

    /**
     * Get all cached market states.
     */
    @NotNull
    public Collection<InstrumentMarketState> getAllStates() {
        return Collections.unmodifiableCollection(cache.values());
    }

    /**
     * Get all cached market state snapshots for lightweight reads.
     */
    @NotNull
    public List<InstrumentMarketStateSnapshot> getAllSnapshots() {
        return cache.values().stream()
                .map(InstrumentMarketState::snapshot)
                .toList();
    }

    /**
     * Check if state exists for a TradePair.
     */
    public boolean contains(@NotNull TradePair tradePair) {
        String key = tradePair.toCompactSymbol();
        return cache.containsKey(key);
    }

    /**
     * Remove market state for a TradePair (e.g., instrument delisted).
     */
    public void remove(@NotNull TradePair tradePair) {
        String key = tradePair.toCompactSymbol();
        InstrumentMarketState removed = cache.remove(key);
        if (removed != null) {
            log.info("Removed market state for {}", tradePair);
        }
    }

    /**
     * Clear all cached market state.
     */
    public void clear() {
        int size = cache.size();
        cache.clear();
        log.info("Cleared market data cache ({} entries removed)", size);
    }

    /**
     * Get cache size.
     */
    public int size() {
        return cache.size();
    }

    /**
     * Get all TradePairs currently in cache.
     */
    @NotNull
    public List<TradePair> getAllTradePairs() {
        return cache.values().stream()
                .map(InstrumentMarketState::getTradePair)
                .toList();
    }

    /**
     * Clean up stale data (e.g., candles older than retention period).
     * Called periodically by MarketDataEngine or scheduler.
     */
    public void pruneStaleCandles(long maxAgeMillis) {
        long threshold = System.currentTimeMillis() - maxAgeMillis;
        int pruned = 0;

        for (InstrumentMarketState state : cache.values()) {
            for (Timeframe timeframe : new ArrayList<>(state.getCandlesByTimeframe().keySet())) {
                List<CandleData> candles = state.getCandles(timeframe);
                List<CandleData> fresh = candles.stream()
                        .filter(c -> (long) c.openTime() * 1000L >= threshold)
                        .toList();

                if (fresh.size() < candles.size()) {
                    state.updateCandles(timeframe, fresh);
                    pruned += candles.size() - fresh.size();
                }
            }
        }

        if (pruned > 0) {
            log.debug("Pruned {} stale candles from cache", pruned);
        }
    }

    /**
     * Get cache statistics for monitoring.
     */
    @NotNull
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
                cache.size(),
                cache.values().stream().mapToInt(s -> s.getCandlesByTimeframe().size()).sum(),
                cache.values().stream()
                        .mapToInt(s -> s.getCandlesByTimeframe().values()
                                .stream().mapToInt(List::size).sum())
                        .sum());
    }

    /**
     * Immutable statistics snapshot for monitoring.
     */
    public record CacheStatistics(
            int instrumentsCount,
            int timeframesCount,
            int candlesCount) {
    }
}
