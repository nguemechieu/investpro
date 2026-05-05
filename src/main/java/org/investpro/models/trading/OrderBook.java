package org.investpro.models.trading;

import lombok.Data;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents the order book (market depth) for a trading pair.
 * Contains bid and ask orders at various price levels.
 */
@Data
public class OrderBook {
    private TradePair tradePair;
    private List<PriceLevel> bids;
    private List<PriceLevel> asks;
    private Instant timestamp;
    private String sequence;

    public OrderBook() {
        this.bids = new ArrayList<>();
        this.asks = new ArrayList<>();
        this.timestamp = Instant.now();
    }

    public OrderBook(TradePair tradePair) {
        this();
        this.tradePair = tradePair;
    }

    public OrderBook(TradePair tradePair, List<PriceLevel> bids, List<PriceLevel> asks) {
        this();
        this.tradePair = tradePair;
        this.bids = bids;
        this.asks = asks;
    }

    /**
     * Get the best bid (highest price buyers are willing to pay)
     */
    public PriceLevel getBestBid() {
        return bids != null && !bids.isEmpty() ? bids.get(0) : null;
    }

    /**
     * Get the best ask (lowest price sellers will accept)
     */
    public PriceLevel getBestAsk() {
        return asks != null && !asks.isEmpty() ? asks.get(0) : null;
    }

    /**
     * Get the bid-ask spread (difference between best ask and best bid)
     */
    public double getSpread() {
        PriceLevel bestBid = getBestBid();
        PriceLevel bestAsk = getBestAsk();
        if (bestBid == null || bestAsk == null) {
            return -1;
        }
        return bestAsk.getPrice() - bestBid.getPrice();
    }

    /**
     * Get the mid price (average of best bid and ask)
     */
    public double getMidPrice() {
        PriceLevel bestBid = getBestBid();
        PriceLevel bestAsk = getBestAsk();
        if (bestBid == null || bestAsk == null) {
            return -1;
        }
        return (bestBid.getPrice() + bestAsk.getPrice()) / 2.0;
    }

    /**
     * Get total bid volume (sum of all bid quantities)
     */
    public double getTotalBidVolume() {
        return bids.stream().mapToDouble(PriceLevel::getSize).sum();
    }

    /**
     * Get total ask volume (sum of all ask quantities)
     */
    public double getTotalAskVolume() {
        return asks.stream().mapToDouble(PriceLevel::getSize).sum();
    }

    /**
     * Represents a single price level in the order book
     */
    @Data
    public static class PriceLevel {
        private double price;
        private double size;
        private int numOrders;

        public PriceLevel(double price, double size) {
            this.price = price;
            this.size = size;
            this.numOrders = 0;
        }

        public PriceLevel(double price, double size, int numOrders) {
            this.price = price;
            this.size = size;
            this.numOrders = numOrders;
        }
    }

    // Explicit getters (Lombok @Data not being invoked)
    public List<PriceLevel> getBids() {
        return bids;
    }

    public List<PriceLevel> getAsks() {
        return asks;
    }

    @Override
    public String toString() {
        return "OrderBook{" +
                "tradePair=" + tradePair +
                ", bestBid=" + getBestBid() +
                ", bestAsk=" + getBestAsk() +
                ", spread=" + String.format("%.8f", getSpread()) +
                ", timestamp=" + timestamp +
                '}';
    }
}
