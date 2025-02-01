package org.investpro;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
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



    public OrderBook(double asks, double bids) {

        this.asks.add(asks);


        this.bids.add(bids);


    }



    ArrayList<Double> bids = new ArrayList<>();
    ArrayList<Double> asks = new ArrayList<>();


}
