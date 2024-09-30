package org.investpro;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class OrderBook {

     Instant now;
    List<OrderBookEntry> bidEntries= new ArrayList<>();
     List<OrderBookEntry> askEntries= new ArrayList<>();

    public OrderBook() {

    }

    public OrderBook(double asks, double asks1, double bids, double bids1) {
        this.asks.add(asks);
        this.asks.add(asks1);

        this.bids.add(bids);
        this.bids.add(bids1);
    }

    public OrderBook(Instant now, List<OrderBookEntry> bidEntries, List<OrderBookEntry> askEntries) {
        this.now = now;
        this.bidEntries = bidEntries;
        this.askEntries = askEntries;
    }

    public ArrayList<Double> getBids() {
        return bids;
    }

    public void setBids(ArrayList<Double> bids) {
        this.bids = bids;
    }

    public ArrayList<Double> getAsks() {
        return asks;
    }

    public void setAsks(ArrayList<Double> asks) {
        this.asks = asks;
    }

    public OrderBook(double asks, double bids) {

        this.asks.add(asks);


        this.bids.add(bids);


    }



    ArrayList<Double> bids = new ArrayList<>();
    ArrayList<Double> asks = new ArrayList<>();


    public void setTradePair(TradePair tradePair) {
            this.tradePair = tradePair;
    }
    TradePair tradePair;
    public TradePair getTradePair() {
        return tradePair;
    }
}
