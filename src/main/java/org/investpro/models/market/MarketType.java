package org.investpro.models.market;

public enum MarketType {
    SPOT,
    DERIVATIVES,
    SECURITIES,
    CRYPTO,
    MARGIN,
    DERIVATIVE,
    FUTURE,
    PERPETUAL,
    OPTION,
    FX,
    FOREX,
    CFD,
    STOCK,
    ETF,
    INDEX,
    BOND,
    FUND,
    WARRANT,
    CRYPTO_SWAP,
    OTC,
    SYNTHETIC,
    UNKNOWN;

    public boolean isDerivative() {
        return this == DERIVATIVE
                || this == DERIVATIVES
                || this == FUTURE
                || this == PERPETUAL
                || this == OPTION
                || this == CFD
                || this == CRYPTO_SWAP;
    }

    public boolean isFx() {
        return this == FX || this == FOREX;
    }
}
