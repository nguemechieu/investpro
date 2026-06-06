package org.investpro.models.market;

public enum LeverageMode {
    NONE,
    MARGIN,
    DERIVATIVE_LEVERAGE,
    UNKNOWN;

    public boolean isLeveraged() {
        return this == MARGIN || this == DERIVATIVE_LEVERAGE;
    }

    public boolean isDerivativeLeverage() {
        return this == DERIVATIVE_LEVERAGE;
    }
}
