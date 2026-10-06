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

                || this == FUTURE
                || this == PERPETUAL

                || this == CFD

                ;


    }


    public boolean isMarket() {
        return this == MARGIN;
    }

}
