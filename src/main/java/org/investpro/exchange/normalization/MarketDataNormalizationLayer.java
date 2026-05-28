package org.investpro.exchange.normalization;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Normalizes raw exchange data — tickers, bid/ask quotes, and order books —
 * into {@link NormalizedMarketSnapshot} instances for cross-exchange comparison.
 *
 * <p>Maintains an internal LRU-style cache keyed by {@code "EXCHANGE:BASE/QUOTE"}.
 * Callers can retrieve the latest cached snapshot via {@link #getLatestSnapshot}.
 */
@Slf4j
public class MarketDataNormalizationLayer {

    private final ConcurrentHashMap<String, NormalizedMarketSnapshot> cache = new ConcurrentHashMap<>();

    /**
     * Constructs a new normalization layer with an empty snapshot cache.
     */
    public MarketDataNormalizationLayer() {
        log.info("MarketDataNormalizationLayer initialized");
    }

    // ─── Normalization ─────────────────────────────────────────

    /**
     * Normalizes a raw {@link Ticker} into a {@link NormalizedMarketSnapshot} and caches the result.
     *
     * @param exchangeName source exchange identifier
     * @param pair         the traded instrument
     * @param ticker       raw ticker from the exchange adapter
     * @return normalized snapshot
     */
    public NormalizedMarketSnapshot normalize(
            @NotNull String exchangeName,
            @NotNull TradePair pair,
            @NotNull Ticker ticker
    ) {
        NormalizedMarketSnapshot snapshot = NormalizedMarketSnapshot.fromTicker(exchangeName, pair, ticker);
        cache.put(cacheKey(exchangeName, pair), snapshot);
        log.debug("Normalized ticker for {}:{} — {}", exchangeName, pair, snapshot.summary());
        return snapshot;
    }

    /**
     * Normalizes manually provided price and volume fields into a {@link NormalizedMarketSnapshot}
     * and caches the result.
     *
     * @param exchangeName source exchange identifier
     * @param pair         the traded instrument
     * @param bid          best bid price
     * @param ask          best ask price
     * @param last         last trade price
     * @param volume       24-hour base volume
     * @return normalized snapshot
     */
    public NormalizedMarketSnapshot normalize(
            @NotNull String exchangeName,
            @NotNull TradePair pair,
            double bid,
            double ask,
            double last,
            double volume
    ) {
        double mid = (bid > 0 && ask > 0) ? (bid + ask) / 2.0 : Math.max(bid, ask);
        double spreadBps = NormalizedMarketSnapshot.computeSpreadBps(bid, ask);

        // Build via fromTicker-equivalent using a synthetic Ticker
        Ticker synthetic = new Ticker(last, bid, ask, volume, System.currentTimeMillis());
        NormalizedMarketSnapshot snapshot = NormalizedMarketSnapshot.fromTicker(exchangeName, pair, synthetic);
        cache.put(cacheKey(exchangeName, pair), snapshot);
        log.debug("Normalized manual quote for {}:{} bid={} ask={} last={}", exchangeName, pair, bid, ask, last);
        return snapshot;
    }

    /**
     * Normalizes a {@link Ticker} and enriches the result with order-book depth information.
     *
     * @param exchangeName source exchange identifier
     * @param pair         the traded instrument
     * @param ticker       raw ticker from the exchange adapter
     * @param orderBook    current order book for depth/liquidity data
     * @return normalized snapshot including depth metadata
     */
    public NormalizedMarketSnapshot normalizeWithDepth(
            @NotNull String exchangeName,
            @NotNull TradePair pair,
            @NotNull Ticker ticker,
            @NotNull OrderBook orderBook
    ) {
        double bid = orderBook.getBestBid() != null ? orderBook.getBestBid().getPrice() : ticker.getBidPrice();
        double ask = orderBook.getBestAsk() != null ? orderBook.getBestAsk().getPrice() : ticker.getAskPrice();
        double mid = (bid > 0 && ask > 0) ? (bid + ask) / 2.0 : ticker.getMidPrice();
        double spreadBps = NormalizedMarketSnapshot.computeSpreadBps(bid, ask);

        int bidLevels = orderBook.getBids() != null ? orderBook.getBids().size() : 0;
        int askLevels = orderBook.getAsks() != null ? orderBook.getAsks().size() : 0;
        double totalBidLiquidity = orderBook.getTotalBidVolume();
        double totalAskLiquidity = orderBook.getTotalAskVolume();

        // Build base from ticker then enrich with depth
        NormalizedMarketSnapshot base = NormalizedMarketSnapshot.fromTicker(exchangeName, pair, ticker);

        // Construct enriched snapshot using the same fields, overriding depth
        NormalizedMarketSnapshot enriched = new NormalizedMarketSnapshotBuilder()
                .exchangeName(exchangeName)
                .tradePair(pair)
                .capturedAt(base.getCapturedAt())
                .bidPrice(bid)
                .askPrice(ask)
                .midPrice(mid)
                .spreadBps(spreadBps)
                .lastTradePrice(base.getLastTradePrice())
                .lastTradeSize(base.getLastTradeSize())
                .volume24h(base.getVolume24h())
                .volumeQuote24h(base.getVolumeQuote24h())
                .high24h(base.getHigh24h())
                .low24h(base.getLow24h())
                .open24h(base.getOpen24h())
                .close24h(base.getClose24h())
                .priceChangePercent24h(base.getPriceChangePercent24h())
                .bidLevels(bidLevels)
                .askLevels(askLevels)
                .totalBidLiquidity(totalBidLiquidity)
                .totalAskLiquidity(totalAskLiquidity)
                .dataFresh(true)
                .dataSource("WEBSOCKET")
                .build();

        cache.put(cacheKey(exchangeName, pair), enriched);
        log.debug("Normalized with depth for {}:{} — bidLevels={} askLevels={}", exchangeName, pair, bidLevels, askLevels);
        return enriched;
    }

    // ─── Cache Access ──────────────────────────────────────────

    /**
     * Returns the latest cached snapshot for the given exchange and pair, if present.
     *
     * @param exchangeName source exchange identifier
     * @param pair         the traded instrument
     * @return an {@link Optional} containing the cached snapshot, or empty
     */
    public Optional<NormalizedMarketSnapshot> getLatestSnapshot(
            @NotNull String exchangeName,
            @NotNull TradePair pair
    ) {
        return Optional.ofNullable(cache.get(cacheKey(exchangeName, pair)));
    }

    /**
     * Returns an unmodifiable view of all currently cached snapshots.
     *
     * @return unmodifiable collection of snapshots
     */
    public Collection<NormalizedMarketSnapshot> getAllSnapshots() {
        return Collections.unmodifiableCollection(cache.values());
    }

    /**
     * Clears all cached snapshots.
     */
    public void clearCache() {
        int size = cache.size();
        cache.clear();
        log.info("Cleared {} snapshot(s) from normalization cache", size);
    }

    // ─── Utilities ───────────────────────────────────────────

    /**
     * Computes the bid-ask spread in basis points.
     *
     * @param bid best bid price
     * @param ask best ask price
     * @return spread in basis points, or 0.0 if not computable
     */
    public static double computeSpreadBps(double bid, double ask) {
        return NormalizedMarketSnapshot.computeSpreadBps(bid, ask);
    }

    private String cacheKey(String exchangeName, TradePair pair) {
        return exchangeName + ":" + pair;
    }

    // ─── Inner Builder ──────────────────────────────────────────

    /** Fluent builder used internally to construct enriched snapshots. */
    private static final class NormalizedMarketSnapshotBuilder {
        private String exchangeName;
        private String venueId;
        private TradePair tradePair;
        private Instant capturedAt;
        private double bidPrice, askPrice, midPrice, spreadBps;
        private double lastTradePrice, lastTradeSize;
        private double volume24h, volumeQuote24h;
        private double high24h, low24h, open24h, close24h;
        private double priceChangePercent24h;
        private int bidLevels, askLevels;
        private double totalBidLiquidity, totalAskLiquidity;
        private boolean dataFresh;
        private String dataSource;

        NormalizedMarketSnapshotBuilder exchangeName(String v) { this.exchangeName = v; return this; }
        NormalizedMarketSnapshotBuilder venueId(String v) { this.venueId = v; return this; }
        NormalizedMarketSnapshotBuilder tradePair(TradePair v) { this.tradePair = v; return this; }
        NormalizedMarketSnapshotBuilder capturedAt(Instant v) { this.capturedAt = v; return this; }
        NormalizedMarketSnapshotBuilder bidPrice(double v) { this.bidPrice = v; return this; }
        NormalizedMarketSnapshotBuilder askPrice(double v) { this.askPrice = v; return this; }
        NormalizedMarketSnapshotBuilder midPrice(double v) { this.midPrice = v; return this; }
        NormalizedMarketSnapshotBuilder spreadBps(double v) { this.spreadBps = v; return this; }
        NormalizedMarketSnapshotBuilder lastTradePrice(double v) { this.lastTradePrice = v; return this; }
        NormalizedMarketSnapshotBuilder lastTradeSize(double v) { this.lastTradeSize = v; return this; }
        NormalizedMarketSnapshotBuilder volume24h(double v) { this.volume24h = v; return this; }
        NormalizedMarketSnapshotBuilder volumeQuote24h(double v) { this.volumeQuote24h = v; return this; }
        NormalizedMarketSnapshotBuilder high24h(double v) { this.high24h = v; return this; }
        NormalizedMarketSnapshotBuilder low24h(double v) { this.low24h = v; return this; }
        NormalizedMarketSnapshotBuilder open24h(double v) { this.open24h = v; return this; }
        NormalizedMarketSnapshotBuilder close24h(double v) { this.close24h = v; return this; }
        NormalizedMarketSnapshotBuilder priceChangePercent24h(double v) { this.priceChangePercent24h = v; return this; }
        NormalizedMarketSnapshotBuilder bidLevels(int v) { this.bidLevels = v; return this; }
        NormalizedMarketSnapshotBuilder askLevels(int v) { this.askLevels = v; return this; }
        NormalizedMarketSnapshotBuilder totalBidLiquidity(double v) { this.totalBidLiquidity = v; return this; }
        NormalizedMarketSnapshotBuilder totalAskLiquidity(double v) { this.totalAskLiquidity = v; return this; }
        NormalizedMarketSnapshotBuilder dataFresh(boolean v) { this.dataFresh = v; return this; }
        NormalizedMarketSnapshotBuilder dataSource(String v) { this.dataSource = v; return this; }

        NormalizedMarketSnapshot build() {
            return new NormalizedMarketSnapshot(
                    exchangeName, venueId, tradePair, capturedAt,
                    bidPrice, askPrice, midPrice, spreadBps,
                    lastTradePrice, lastTradeSize,
                    volume24h, volumeQuote24h,
                    high24h, low24h, open24h, close24h,
                    priceChangePercent24h,
                    bidLevels, askLevels, totalBidLiquidity, totalAskLiquidity,
                    dataFresh, dataSource
            );
        }
    }
}
