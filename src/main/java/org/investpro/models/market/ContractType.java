package org.investpro.models.market;

public enum ContractType {
    CASH,
    MARGIN,
    FUTURE,
    PERPETUAL,
    OPTION,
    FORWARD,
    SWAP,
    CFD,
    NONE,
    UNKNOWN;

    public boolean isDerivative() {
        return this == FUTURE
                || this == PERPETUAL
                || this == OPTION
                || this == FORWARD
                || this == SWAP
                || this == CFD;
    }

    public boolean isLeveragedContract() {
        return this == MARGIN
                || this == FUTURE
                || this == PERPETUAL
                || this == OPTION
                || this == CFD
                || this == SWAP;
    }

    public boolean requiresExpiry() {
        return this == FUTURE
                || this == OPTION
                || this == FORWARD;
    }

    public boolean isCashLike() {
        return this == CASH || this == NONE;
    }
}
