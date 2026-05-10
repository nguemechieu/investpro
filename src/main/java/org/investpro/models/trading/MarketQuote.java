package org.investpro.models.trading;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Immutable snapshot of current market quote for a TradePair.
 * Contains bid, ask, last price, and market metadata (volume, change,
 * timestamp).
 * Updated by MarketDataCache from real-time market feeds.
 */
@Builder
@Getter
@ToString
public class MarketQuote implements Serializable {
    private static final long serialVersionUID = 1L;

    /**
     * The instrument for this quote.
     */
    @NotNull
    private final TradePair tradePair;

    /**
     * Current bid price (what buyers are willing to pay).
     */
    private final double bid;

    /**
     * Current ask price (what sellers are asking).
     */
    private final double ask;

    /**
     * Last traded price.
     */
    private final double last;

    /**
     * 24-hour trading volume.
     */
    private final double volume;

    /**
     * 24-hour price change percentage.
     */
    private final double changePercent;

    /**
     * 24-hour high price.
     */
    private final double high24h;

    /**
     * 24-hour low price.
     */
    private final double low24h;

    /**
     * When this quote was last updated.
     */
    @NotNull
    @Builder.Default
    private final Instant updatedAt = Instant.now();

    /**
     * Source of this quote (e.g., "Binance", "OANDA", "CoinGecko").
     */
    @NotNull
    @Builder.Default
    private final String source = "Unknown";

    /**
     * Calculate the mid-price (average of bid and ask).
     */
    public double midPrice() {
        if (bid > 0 && ask > 0) {
            return (bid + ask) / 2.0;
        }
        if (last > 0) {
            return last;
        }
        return Math.max(bid, ask);
    }

    /**
     * Calculate the spread (difference between ask and bid).
     */
    public double spread() {
        if (bid <= 0 || ask <= 0) {
            return 0.0;
        }
        return Math.abs(ask - bid);
    }

    /**
     * Calculate the spread as a percentage of mid-price.
     */
    public double spreadPercent() {
        double mid = midPrice();
        if (mid <= 0) {
            return 0.0;
        }
        return (spread() / mid) * 100.0;
    }

    /**
     * Check if this quote is fresh (not older than maxAge).
     */
    public boolean isFresh(Duration maxAge) {
        if (maxAge == null || maxAge.isNegative()) {
            return true;
        }
        Instant threshold = Instant.now().minus(maxAge);
        return updatedAt.isAfter(threshold);
    }

    /**
     * Check if this quote has valid bid/ask/last prices.
     */
    public boolean hasValidPrices() {
        return bid > 0 || ask > 0 || last > 0;
    }

    /**
     * Age of this quote in milliseconds.
     */
    public long ageMillis() {
        return System.currentTimeMillis() - updatedAt.toEpochMilli();
    }

    /**
     * Age of this quote as a Duration.
     */
    public Duration age() {
        return Duration.between(updatedAt, Instant.now());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        MarketQuote that = (MarketQuote) o;
        return Objects.equals(tradePair, that.tradePair)
                && Double.compare(that.bid, bid) == 0
                && Double.compare(that.ask, ask) == 0
                && Double.compare(that.last, last) == 0
                && Objects.equals(updatedAt, that.updatedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradePair, bid, ask, last, updatedAt);
    }
}
