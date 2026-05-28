package org.investpro.exchange.normalization;

import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;

import java.time.Instant;

/**
 * A unified, cross-exchange market data snapshot normalizing raw ticker and order-book
 * data into a single, comparable structure regardless of origin exchange.
 *
 * <p>Instances are immutable once constructed. Use the static factory methods
 * {@link #fromTicker}, {@link #fromRawPrices}, and {@link #stale} to build instances.
 */
public final class NormalizedMarketSnapshot {

    // ─── Identity ────────────────────────────────────────────
    /** Source exchange name (e.g. "Coinbase", "OANDA"). */
    private final String exchangeName;
    /** Optional sub-venue identifier or raw symbol string (e.g. "SPOT", "BTC-USD"). May be null. */
    private final String venueId;
    /** The traded instrument. May be null when built from raw prices. */
    private final TradePair tradePair;
    /** Timestamp at which this snapshot was captured. */
    private final Instant capturedAt;

    // ─── Best bid/ask ────────────────────────────────────
    private final double bidPrice;
    private final double askPrice;
    private final double midPrice;
    /** Bid-ask spread expressed in basis points. */
    private final double spreadBps;

    // ─── Last trade ───────────────────────────────────────
    private final double lastTradePrice;
    private final double lastTradeSize;

    // ─── 24-hour statistics ──────────────────────────────────
    private final double volume24h;
    private final double volumeQuote24h;
    private final double high24h;
    private final double low24h;
    private final double open24h;
    private final double close24h;
    private final double priceChangePercent24h;

    // ─── Depth metadata ────────────────────────────────────
    /** Number of bid price levels available in this snapshot. */
    private final int bidLevels;
    /** Number of ask price levels available in this snapshot. */
    private final int askLevels;
    /** Total quantity summed across all available bid levels. */
    private final double totalBidLiquidity;
    /** Total quantity summed across all available ask levels. */
    private final double totalAskLiquidity;

    // ─── Freshness ─────────────────────────────────────────
    /** False when this snapshot was served from a stale cache. */
    private final boolean dataFresh;
    /** Transport/source label: "WEBSOCKET", "REST", or "STALE_CACHE". */
    private final String dataSource;

    /** Full constructor used by the builder-style static factories. */
    public NormalizedMarketSnapshot(
            String exchangeName, String venueId, TradePair tradePair, Instant capturedAt,
            double bidPrice, double askPrice, double midPrice, double spreadBps,
            double lastTradePrice, double lastTradeSize,
            double volume24h, double volumeQuote24h,
            double high24h, double low24h, double open24h, double close24h,
            double priceChangePercent24h,
            int bidLevels, int askLevels, double totalBidLiquidity, double totalAskLiquidity,
            boolean dataFresh, String dataSource
    ) {
        this.exchangeName = exchangeName;
        this.venueId = venueId;
        this.tradePair = tradePair;
        this.capturedAt = capturedAt != null ? capturedAt : Instant.now();
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.midPrice = midPrice;
        this.spreadBps = spreadBps;
        this.lastTradePrice = lastTradePrice;
        this.lastTradeSize = lastTradeSize;
        this.volume24h = volume24h;
        this.volumeQuote24h = volumeQuote24h;
        this.high24h = high24h;
        this.low24h = low24h;
        this.open24h = open24h;
        this.close24h = close24h;
        this.priceChangePercent24h = priceChangePercent24h;
        this.bidLevels = bidLevels;
        this.askLevels = askLevels;
        this.totalBidLiquidity = totalBidLiquidity;
        this.totalAskLiquidity = totalAskLiquidity;
        this.dataFresh = dataFresh;
        this.dataSource = dataSource != null ? dataSource : "UNKNOWN";
    }

    // ─── Static Factories ─────────────────────────────────────

    /**
     * Creates a normalized snapshot from an exchange {@link Ticker}.
     *
     * @param exchangeName source exchange identifier
     * @param pair         the traded instrument
     * @param ticker       raw ticker from the exchange adapter
     * @return normalized snapshot populated from the ticker fields
     */
    public static NormalizedMarketSnapshot fromTicker(String exchangeName, TradePair pair, Ticker ticker) {
        double bid = ticker.getBidPrice();
        double ask = ticker.getAskPrice();
        double mid = ticker.getMidPrice();
        double spreadBps = computeSpreadBps(bid, ask);
        return new NormalizedMarketSnapshot(
                exchangeName, null, pair,
                Instant.ofEpochMilli(ticker.getTimestamp() > 0 ? ticker.getTimestamp() : System.currentTimeMillis()),
                bid, ask, mid, spreadBps,
                ticker.getLastPrice(), 0.0,
                ticker.getVolume(), 0.0,
                ticker.getHighPrice(), ticker.getLowPrice(), ticker.getOpenPrice(), ticker.getLastPrice(),
                ticker.getChangePercent(),
                0, 0, 0.0, 0.0,
                true, "REST"
        );
    }

    /**
     * Creates a normalized snapshot from raw double prices.
     *
     * <p>Use this when a {@link TradePair} is not yet available (e.g. in tests
     * or during normalization of simple ticker payloads).
     *
     * @param exchangeName  source exchange name
     * @param symbol        symbol string (e.g. "BTC-USD") — stored in venueId
     * @param bid           best bid price
     * @param ask           best ask price
     * @param last          last trade price
     * @param volume24h     24-hour volume
     * @param capturedAt    when the snapshot was taken
     * @return fresh normalized snapshot
     */
    public static NormalizedMarketSnapshot fromRawPrices(
            String exchangeName,
            String symbol,
            double bid,
            double ask,
            double last,
            double volume24h,
            Instant capturedAt
    ) {
        double mid = (bid + ask) / 2.0;
        double spreadBps = computeSpreadBps(bid, ask);
        return new NormalizedMarketSnapshot(
                exchangeName, symbol, null, capturedAt,
                bid, ask, mid, spreadBps,
                last, 0.0,
                volume24h, 0.0,
                last, last, last, last, 0.0,
                0, 0, 0.0, 0.0,
                true, "REST"
        );
    }

    /**
     * Creates a minimal stale placeholder snapshot for the given exchange and pair.
     * {@link #isDataFresh()} returns {@code false} and {@link #getDataSource()} returns {@code "STALE_CACHE"}.
     *
     * @param exchangeName source exchange identifier
     * @param pair         the traded instrument
     * @return a stale placeholder snapshot
     */
    public static NormalizedMarketSnapshot stale(String exchangeName, TradePair pair) {
        return new NormalizedMarketSnapshot(
                exchangeName, null, pair, Instant.now(),
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0,
                0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0,
                0, 0, 0.0, 0.0,
                false, "STALE_CACHE"
        );
    }

    /**
     * Convenience overload for {@link #stale(String, TradePair)} when only a
     * symbol string is available (e.g. "BTC-USD").
     *
     * @param exchangeName source exchange identifier
     * @param symbol       symbol string (e.g. "BTC-USD") — stored as venueId
     * @return stale placeholder snapshot
     */
    public static NormalizedMarketSnapshot stale(String exchangeName, String symbol) {
        return new NormalizedMarketSnapshot(
                exchangeName, symbol, null, Instant.now(),
                0.0, 0.0, 0.0, 0.0,
                0.0, 0.0,
                0.0, 0.0,
                0.0, 0.0, 0.0, 0.0, 0.0,
                0, 0, 0.0, 0.0,
                false, "STALE_CACHE"
        );
    }

    // ─── Derived Queries ─────────────────────────────────────

    /**
     * Returns {@code true} if bid, ask, and last trade prices are all positive,
     * indicating this snapshot has actionable market data.
     *
     * @return true if the snapshot contains complete price information
     */
    public boolean isComplete() {
        return bidPrice > 0.0 && askPrice > 0.0 && lastTradePrice > 0.0;
    }

    /**
     * Returns a brief human-readable summary of this snapshot.
     *
     * @return formatted summary string
     */
    public String summary() {
        String sym = venueId != null ? venueId : (tradePair != null ? tradePair.toString() : "?");
        return "NormalizedMarketSnapshot[%s %s] bid=%.5f ask=%.5f last=%.5f spread=%.2fbps vol=%.4f fresh=%s src=%s"
                .formatted(exchangeName, sym, bidPrice, askPrice, lastTradePrice,
                        spreadBps, volume24h, dataFresh, dataSource);
    }

    // ─── Computed Utility ─────────────────────────────────────

    /**
     * Computes the bid-ask spread in basis points: {@code (ask - bid) / mid * 10_000}.
     * Returns 0 if mid price is zero or non-positive.
     *
     * @param bid best bid price
     * @param ask best ask price
     * @return spread in basis points, or 0.0 if not computable
     */
    public static double computeSpreadBps(double bid, double ask) {
        if (bid <= 0 || ask <= 0) return 0.0;
        double mid = (bid + ask) / 2.0;
        if (mid <= 0) return 0.0;
        return ((ask - bid) / mid) * 10_000.0;
    }

    // ─── Accessors ──────────────────────────────────────────

    public String getExchangeName()          { return exchangeName; }
    public String getVenueId()               { return venueId; }
    public TradePair getTradePair()          { return tradePair; }
    public Instant getCapturedAt()           { return capturedAt; }
    public double getBidPrice()              { return bidPrice; }
    public double getAskPrice()              { return askPrice; }
    public double getMidPrice()              { return midPrice; }
    public double getSpreadBps()             { return spreadBps; }
    public double getLastTradePrice()        { return lastTradePrice; }
    public double getLastTradeSize()         { return lastTradeSize; }
    public double getVolume24h()             { return volume24h; }
    public double getVolumeQuote24h()        { return volumeQuote24h; }
    public double getHigh24h()               { return high24h; }
    public double getLow24h()                { return low24h; }
    public double getOpen24h()               { return open24h; }
    public double getClose24h()              { return close24h; }
    public double getPriceChangePercent24h() { return priceChangePercent24h; }
    public int getBidLevels()               { return bidLevels; }
    public int getAskLevels()               { return askLevels; }
    public double getTotalBidLiquidity()     { return totalBidLiquidity; }
    public double getTotalAskLiquidity()     { return totalAskLiquidity; }
    public boolean isDataFresh()             { return dataFresh; }
    public String getDataSource()            { return dataSource; }

    // ─── Convenience record-style accessors (SmartExecutionRouter / normalization layer) ─
    /** Returns venueId or trade pair string as the symbol identifier. */
    public String symbol()           { return venueId != null ? venueId : (tradePair != null ? tradePair.toString() : ""); }
    /** Alias for {@link #getSpreadBps()}. */
    public double spreadBps()        { return spreadBps; }
    /** Returns total bid liquidity as BigDecimal, or null if zero. */
    public java.math.BigDecimal totalBidLiquidity() {
        return totalBidLiquidity > 0 ? java.math.BigDecimal.valueOf(totalBidLiquidity) : null;
    }
    /** Returns bid price as BigDecimal. */
    public java.math.BigDecimal bidPrice()   { return java.math.BigDecimal.valueOf(bidPrice); }
    /** Returns ask price as BigDecimal. */
    public java.math.BigDecimal askPrice()   { return java.math.BigDecimal.valueOf(askPrice); }
    /** Returns mid price as BigDecimal. */
    public java.math.BigDecimal midPrice()   { return java.math.BigDecimal.valueOf(midPrice); }
    /** Returns 24h volume as BigDecimal. */
    public java.math.BigDecimal volume24h()  { return java.math.BigDecimal.valueOf(volume24h); }
    /** Alias for {@link #getDataSource()}. */
    public String dataSource()       { return dataSource; }

    @Override
    public String toString() {
        return summary();
    }
}
