package org.investpro.investpro.model;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class OrderBook {

    private TradePair tradePair;
    private Instant timestamp;
    private List<OrderBookEntry> bidEntries;
    private List<OrderBookEntry> askEntries;

    // Constructor with single bid/ask entry
    public OrderBook(TradePair tradePair, double bidPrice, double askPrice, double bidLiquidity, double askLiquidity, Instant time) {
        this.tradePair = tradePair;
        this.timestamp = time != null ? time : Instant.now();
        this.bidEntries = new ArrayList<>();
        this.askEntries = new ArrayList<>();
        this.bidEntries.add(new OrderBookEntry(bidPrice, bidLiquidity));
        this.askEntries.add(new OrderBookEntry(askPrice, askLiquidity));
    }

    // Full constructor
    public OrderBook(TradePair tradePair, Instant timestamp, List<OrderBookEntry> bidEntries, List<OrderBookEntry> askEntries) {
        this.tradePair = tradePair;
        this.timestamp = timestamp;
        this.bidEntries = bidEntries != null ? bidEntries : new ArrayList<>();
        this.askEntries = askEntries != null ? askEntries : new ArrayList<>();
    }

    // Empty constructor
    public OrderBook() {
        this.bidEntries = new ArrayList<>();
        this.askEntries = new ArrayList<>();
        this.timestamp = Instant.now();
    }

    public OrderBook(Instant now, List<OrderBookEntry> bidEntries, List<OrderBookEntry> askEntries) {
        this.timestamp = now;
        this.bidEntries = bidEntries;
        this.askEntries = askEntries;
    }

    public void addBid(double price, double liquidity) {
        bidEntries.add(new OrderBookEntry(price, liquidity));
    }

    public void addAsk(double price, double liquidity) {
        askEntries.add(new OrderBookEntry(price, liquidity));
    }

    public List<OrderBookEntry> getSortedBids() {
        bidEntries.sort((b1, b2) -> Double.compare(b2.getPrice(), b1.getPrice())); // descending
        return bidEntries;
    }

    public List<OrderBookEntry> getSortedAsks() {
        askEntries.sort((a1, a2) -> Double.compare(a1.getPrice(), a2.getPrice())); // ascending
        return askEntries;
    }

}
