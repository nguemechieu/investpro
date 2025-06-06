package org.investpro.investpro.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.List;

@Setter
@Getter
@ToString
@NoArgsConstructor
public class PriceData {
    private double volume;
    private TradePair tradePair;
    private String instrument;
    private double price;
    private Instant timestamp;
    private double ask;
    private double bid;

    private List<PriceEntry> prices;

    public PriceData(String instrument, double bid, double ask, Instant time) {
        this.instrument = instrument;
        this.bid = bid;
        this.ask = ask;
        this.timestamp = time;
    }

    public PriceData(TradePair tradePair, double lastPrice, double bidPrice, double askPrice, double volume, long l) {
        this.tradePair = tradePair;
        this.price = lastPrice;
        this.bid = bidPrice;
        this.ask = askPrice;
        this.volume = volume;
        this.timestamp = Instant.ofEpochSecond(l);
    }

    public void add(PriceData priceData) {
        this.prices.add(priceData.prices.getFirst());
    }

    @Setter
    @Getter
    @NoArgsConstructor

    public static class PriceEntry {
        private List<AskBidEntry> asks;
        private List<AskBidEntry> bids;
        private double closeoutAsk;
        private double closeoutBid;
        private String instrument;
        private QuoteHomeConversionFactors quoteHomeConversionFactors;
        private String status;


        private String time;


        private UnitsAvailable unitsAvailable;
    }


    @Setter
    @Getter
    @ToString
    public static class AskBidEntry {
        private long liquidity;
        private double price;

    }

    @Setter
    @Getter
    @ToString
    @NoArgsConstructor
    public static class QuoteHomeConversionFactors {
        private double negativeUnits;
        private double positiveUnits;

    }

    @Setter
    @Getter
    @ToString
    @NoArgsConstructor
    public static class UnitsAvailable {
        private UnitDetails defaultUnits;
        private UnitDetails openOnly;
        private UnitDetails reduceFirst;
        private UnitDetails reduceOnly;

    }

    @Setter
    @Getter
    @ToString
    @NoArgsConstructor
    public static class UnitDetails {
        private String longValue;
        private String shortValue;

    }
}
