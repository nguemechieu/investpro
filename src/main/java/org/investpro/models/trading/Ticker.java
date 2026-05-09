package org.investpro.models.trading;

import lombok.Data;

import java.time.Instant;

/**
 * Market ticker snapshot for one instrument/pair.
 *
 * Stores latest price, bid/ask, volume, and optional 24h statistics.
 */
@Data
public class Ticker {

    // Explicit getters (Lombok @Data not being invoked)
    private double lastPrice;
    private double bidPrice;
    private double askPrice;
    private double openPrice;
    private double highPrice;
    private double lowPrice;
    private double volume;
    private long timestamp;

    public Ticker() {
        this.timestamp = System.currentTimeMillis();
    }

    public Ticker(
            double lastPrice,
            double bidPrice,
            double askPrice,
            double volume,
            long timestamp
    ) {
        this.lastPrice = sanitizePrice(lastPrice);
        this.bidPrice = sanitizePrice(bidPrice);
        this.askPrice = sanitizePrice(askPrice);
        this.volume = sanitizeVolume(volume);
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
    }

    public Ticker(
            double lastPrice,
            double bidPrice,
            double askPrice,
            double openPrice,
            double highPrice,
            double lowPrice,
            double volume,
            long timestamp
    ) {
        this.lastPrice = sanitizePrice(lastPrice);
        this.bidPrice = sanitizePrice(bidPrice);
        this.askPrice = sanitizePrice(askPrice);
        this.openPrice = sanitizePrice(openPrice);
        this.highPrice = sanitizePrice(highPrice);
        this.lowPrice = sanitizePrice(lowPrice);
        this.volume = sanitizeVolume(volume);
        this.timestamp = timestamp > 0 ? timestamp : System.currentTimeMillis();
    }

    public static Ticker empty() {
        return new Ticker();
    }

    public static Ticker fromBidAsk(double bidPrice, double askPrice) {
        double last = 0.0;

        if (bidPrice > 0 && askPrice > 0) {
            last = (bidPrice + askPrice) / 2.0;
        } else if (bidPrice > 0) {
            last = bidPrice;
        } else if (askPrice > 0) {
            last = askPrice;
        }

        return new Ticker(
                last,
                bidPrice,
                askPrice,
                0.0,
                System.currentTimeMillis()
        );
    }

    public double getMidPrice() {
        if (bidPrice > 0 && askPrice > 0) {
            return (bidPrice + askPrice) / 2.0;
        }

        if (lastPrice > 0) {
            return lastPrice;
        }

        return Math.max(bidPrice, askPrice);
    }

    public double getSpread() {
        if (bidPrice <= 0 || askPrice <= 0) {
            return 0.0;
        }

        return Math.abs(askPrice - bidPrice);
    }

    public double getSpreadPercent() {
        double mid = getMidPrice();

        if (mid <= 0) {
            return 0.0;
        }

        return (getSpread() / mid) * 100.0;
    }

    /**
     * Absolute price change from open to last.
     */
    public double getChange() {
        if (openPrice <= 0 || lastPrice <= 0) {
            return 0.0;
        }

        return lastPrice - openPrice;
    }

    /**
     * Percent price change from open to last.
     */
    public double getChangePercent() {
        if (openPrice <= 0 || lastPrice <= 0) {
            return 0.0;
        }

        return ((lastPrice - openPrice) / openPrice) * 100.0;
    }

    public boolean hasBidAsk() {
        return bidPrice > 0 && askPrice > 0;
    }

    public boolean hasLastPrice() {
        return lastPrice > 0;
    }

    public boolean isValid() {
        return hasLastPrice() || hasBidAsk();
    }

    public Instant getInstant() {
        return Instant.ofEpochMilli(timestamp > 0 ? timestamp : System.currentTimeMillis());
    }

    public void updateBidAsk(double bidPrice, double askPrice) {
        this.bidPrice = sanitizePrice(bidPrice);
        this.askPrice = sanitizePrice(askPrice);

        if (this.bidPrice > 0 && this.askPrice > 0) {
            this.lastPrice = getMidPrice();
        }

        this.timestamp = System.currentTimeMillis();
    }

    public void updateLast(double lastPrice) {
        this.lastPrice = sanitizePrice(lastPrice);
        this.timestamp = System.currentTimeMillis();
    }

    public void updateStats(
            double openPrice,
            double highPrice,
            double lowPrice,
            double volume
    ) {
        this.openPrice = sanitizePrice(openPrice);
        this.highPrice = sanitizePrice(highPrice);
        this.lowPrice = sanitizePrice(lowPrice);
        this.volume = sanitizeVolume(volume);
        this.timestamp = System.currentTimeMillis();
    }

    private double sanitizePrice(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0.0;
        }

        return value;
    }

    private double sanitizeVolume(double value) {
        if (!Double.isFinite(value) || value < 0) {
            return 0.0;
        }

        return value;
    }

    @Override
    public String toString() {
        return "Ticker{last=%s, bid=%s, ask=%s, spread=%s, volume=%s, change=%s%%, timestamp=%s}"
                .formatted(
                        lastPrice,
                        bidPrice,
                        askPrice,
                        getSpread(),
                        volume,
                        String.format("%.2f", getChangePercent()),
                        getInstant()
                );
    }
    private TradePair tradePair;

    private double  quoteAssetVolume;

    private  double tradeCount;
}