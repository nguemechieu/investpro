package org.investpro;

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

    public OrderBook() {
        this.timestamp = Instant.now(); // Use current timestamp by default
        this.bidEntries = new ArrayList<>();
        bidEntries.add(new OrderBookEntry(0.05, 1.7));
        this.askEntries = new ArrayList<>();
        askEntries.add(new OrderBookEntry(0.06, 1.8));

    }

    public OrderBook(Instant timestamp, List<OrderBookEntry> bidEntries, List<OrderBookEntry> askEntries) {
        this.timestamp = timestamp;
        this.bidEntries = bidEntries;
        this.askEntries = askEntries;
    }

    public OrderBook(TradePair tradePair, double bidPrice, double askPrice, double bidLiquidity, double askLiquidity, Instant timestamp) {
        this.tradePair = tradePair;
        this.bidEntries.add(new OrderBookEntry(bidPrice, bidLiquidity));
        this.askEntries.add(new OrderBookEntry(askPrice, askLiquidity));
        this.timestamp = timestamp;
    }

    public void addBid(double price, double liquidity) {
        bidEntries.add(new OrderBookEntry(price, liquidity));
    }

    public void addAsk(double price, double liquidity) {
        askEntries.add(new OrderBookEntry(price, liquidity));
    }

    public List<OrderBookEntry> getSortedBids() {
        bidEntries.sort((b1, b2) -> Double.compare(b2.getPrice(), b1.getPrice())); // Sort bids in descending order
        return bidEntries;
    }

    public List<OrderBookEntry> getSortedAsks() {
        askEntries.sort((a1, a2) -> Double.compare(a1.getPrice(), a2.getPrice())); // Sort asks in ascending order
        return askEntries;
    }
}
