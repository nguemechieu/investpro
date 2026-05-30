package org.investpro.market;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.MarketQuote;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.TradePair;
import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Central orchestrator for market data flow.
 * <p>
 * Responsibilities:
 * 1. Initialize and manage MarketDataCache (all instrument states)
 * 2. Initialize and manage InstrumentRegistry (all tradable instruments)
 * 3. Initialize and manage InstrumentMetadataService (metadata enrichment)
 * 4. Initialize and manage TradingSessionService (session availability)
 * 5. Bootstrap instruments from exchange adapters
 * 6. Coordinate async metadata enrichment
 * 7. Manage cache lifecycle (cleanup, pruning of stale data)
 * <p>
 * Data flow: Exchange Adapters → MarketDataEngine → MarketDataCache → UI Panels
 */
@Slf4j
@Getter
public class MarketDataEngine {

    private final MarketDataCache marketDataCache;
    private final InstrumentRegistry instrumentRegistry;
    private final InstrumentMetadataService metadataService;
    private final TradingSessionService tradingSessionService;

    private final ScheduledExecutorService cleanupExecutor;
    private static final long CACHE_CLEANUP_INTERVAL_MILLIS = 60_000; // 1 minute
    private static final long STALE_CANDLE_THRESHOLD_MILLIS = 3_600_000; // 1 hour

    public MarketDataEngine() {
        this.instrumentRegistry = new InstrumentRegistry();
        this.marketDataCache = new MarketDataCache();
        this.metadataService = new InstrumentMetadataService(instrumentRegistry);
        this.tradingSessionService = new TradingSessionService(instrumentRegistry);
        this.cleanupExecutor = Executors.newScheduledThreadPool(1, r -> {
            Thread t = new Thread(r, "MarketDataEngine-Cleanup");
            t.setDaemon(true);
            return t;
        });

        log.info("MarketDataEngine initialized with cache, registry, and services");
        startCacheCleanup();
    }

    /**
     * Start background cache cleanup task.
     * Removes stale candles that exceed the age threshold.
     */
    private void startCacheCleanup() {
        cleanupExecutor.scheduleAtFixedRate(
                this::performCacheCleanup,
                CACHE_CLEANUP_INTERVAL_MILLIS,
                CACHE_CLEANUP_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Perform cache maintenance (prune stale data).
     */
    private void performCacheCleanup() {
        try {
            marketDataCache.pruneStaleCandles(STALE_CANDLE_THRESHOLD_MILLIS);
            log.trace("Cache cleanup completed");
        } catch (Exception e) {
            log.error("Error during cache cleanup", e);
        }
    }

    /**
     * Bootstrap instruments from an exchange adapter.
     * For each pair, enrich metadata and register in the cache.
     */
    public void bootstrapInstruments(
            @NotNull String broker,
            @NotNull List<TradePair> tradePairs) {
        log.info("Bootstrapping {} instruments for broker: {}", tradePairs.size(), broker);

        for (TradePair pair : tradePairs) {
            try {
                metadataService.registerEnriched(pair, broker);
                log.trace("Registered enriched metadata for {} on {}", pair, broker);
            } catch (Exception e) {
                log.warn("Failed to enrich metadata for {} on {}", pair, broker, e);
            }
        }

        log.info("Bootstrap complete for {}: {} instruments registered",
                broker, instrumentRegistry.size());
    }

    /**
     * Bootstrap instruments asynchronously.
     */
    public CompletableFuture<Void> bootstrapInstrumentsAsync(
            @NotNull String broker,
            @NotNull List<TradePair> tradePairs) {
        return CompletableFuture.runAsync(
                () -> bootstrapInstruments(broker, tradePairs),
                cleanupExecutor);
    }

    /**
     * Update market quote for a pair (called by exchange adapters).
     * Caches the quote and updates InstrumentMarketState.
     */
    public void updateQuote(
            @NotNull TradePair tradePair,
            double bid,
            double ask,
            double last,
            double volume) {
        try {
            MarketQuote quote = MarketQuote.builder()
                    .tradePair(tradePair)
                    .bid(bid)
                    .ask(ask)
                    .last(last)
                    .volume(volume)
                    .updatedAt(Instant.now())
                    .build();

            marketDataCache.updateQuote(tradePair, quote);
            log.trace("Updated quote for {}: bid={}, ask={}", tradePair, bid, ask);
        } catch (Exception e) {
            log.error("Failed to update quote for {}", tradePair, e);
        }
    }

    /**
     * Update order book for a pair (called by exchange adapters).
     */
    public void updateOrderBook(
            @NotNull TradePair tradePair,
            @NotNull OrderBook orderBook) {
        try {
            marketDataCache.updateOrderBook(tradePair, orderBook);
            log.trace("Updated order book for {}", tradePair);
        } catch (Exception e) {
            log.error("Failed to update order book for {}", tradePair, e);
        }
    }

    /**
     * Update candles for a pair (called by exchange adapters).
     */
    public void updateCandles(
            @NotNull TradePair tradePair,
            @NotNull Timeframe timeframe,
            @NotNull List<CandleData> candles) {
        try {
            marketDataCache.updateCandles(tradePair, timeframe, candles);
            log.trace("Updated {} candles for {} on {}",
                    candles.size(), tradePair, timeframe);
        } catch (Exception e) {
            log.error("Failed to update candles for {}", tradePair, e);
        }
    }

    /**
     * Get the current market state snapshot for a pair.
     * Returns null if the pair is not in the cache.
     */
    @Nullable
    public InstrumentMarketStateSnapshot getSnapshot(@NotNull TradePair tradePair) {
        return marketDataCache.getState(tradePair)
                .map(InstrumentMarketState::snapshot)
                .orElse(null);
    }

    /**
     * Get the current market state for a pair (mutable).
     * Use for direct updates if needed.
     */
    @Nullable
    public InstrumentMarketState getState(@NotNull TradePair tradePair) {
        return marketDataCache.getState(tradePair).orElse(null);
    }

    /**
     * Get the current quote for a pair.
     */
    @Nullable
    public MarketQuote getQuote(@NotNull TradePair tradePair) {
        return marketDataCache.getQuote(tradePair).orElse(null);
    }

    /**
     * Check if a pair is tradable now.
     */
    public boolean isTradableNow(@NotNull TradePair tradePair) {
        var metadata = instrumentRegistry.get(tradePair);
        // Assume tradable if not registered; crypto stays 24/7 even when session
        // metadata is incomplete.
        return metadata.map(state -> tradingSessionService.isTradableNow(state) || tradePair.isTradableNow())
                .orElse(tradePair.isTradableNow());

    }

    /**
     * Get the trading hours description for a pair.
     */
    @NotNull
    public String getTradingHours(@NotNull TradePair tradePair) {
        return tradingSessionService.getTradingHoursDescription(tradePair);
    }

    /**
     * Get all cached market snapshots.
     */
    @NotNull
    public List<InstrumentMarketStateSnapshot> getAllSnapshots() {
        return marketDataCache.getAllSnapshots();
    }

    /**
     * Get cache statistics.
     */
    @NotNull
    public MarketDataCache.CacheStatistics getStatistics() {
        return marketDataCache.getStatistics();
    }

    /**
     * Clear all cached data (use cautiously).
     */
    public void clearCache() {
        marketDataCache.clear();
        log.warn("Market data cache cleared");
    }

    /**
     * Shutdown the engine and cleanup resources.
     */
    public void shutdown() {
        log.info("Shutting down MarketDataEngine");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        clearCache();
        log.info("MarketDataEngine shutdown complete");
    }
}
