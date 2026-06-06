package org.investpro.models.market;

public enum InstrumentType {
    SPOT,
    FUTURE,
    PERPETUAL,
    OPTION,
    SWAP,
    CFD,
    FORWARD,
    CRYPTO_SWAP,
    STOCK,
    ETF,
    FOREX,
    BOND,
    FUND,
    INDEX,
    WARRANT,
    COMMODITY,
    UNKNOWN;

    public boolean isDerivative() {
        return this == FUTURE
                || this == PERPETUAL
                || this == OPTION
                || this == SWAP
                || this == CFD
                || this == FORWARD
                || this == CRYPTO_SWAP;
    }

    public boolean isSecurity() {
        return this == STOCK
                || this == ETF
                || this == BOND
                || this == FUND
                || this == INDEX
                || this == WARRANT;
    }
}
