package org.investpro.models.market;

public enum MarketType {


        SPOT,
        MARGIN,
        FUTURE,
        PERPETUAL,
        CFD,
        DERIVATIVE,
        DERIVATIVES,
        UNKNOWN;


    public boolean isDerivative() {
        return this == DERIVATIVE
                || this == DERIVATIVES

                || this == FUTURE
                || this == PERPETUAL

                || this == CFD

                ;


    }


    public boolean isMarket() {
        return this == MARGIN;
    }

    public MarketCategory category() {
        if (isDerivative()) return MarketCategory.DERIVATIVES;
        return this == SPOT || this == MARGIN ? MarketCategory.SPOT : MarketCategory.UNKNOWN;
    }

}
