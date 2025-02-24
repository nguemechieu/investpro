package org.investpro;

public enum SIGNAL {
    BUY,
    SELL,
    HOLD,
    CLOSE_SELL,
    CLOSE_BUY,
    STRONG_BUY, STRONG_SELL, STOP_LOSS, CLOSE_ALL;

    public boolean isLong() {
        return this == STRONG_BUY;
    }

    public boolean isShort() {
        return this == STRONG_SELL;
    }
}
