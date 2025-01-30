package org.investpro;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Setter
@Getter
@ToString
@NoArgsConstructor
public class PriceData {
    private List<PriceEntry> prices;

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
