package org.investpro;

public enum TRADE_STATUS {

    INIT_FAILED(0),
    INIT_SUCCEEDED(1),
    CANCEL(2),
    REJECT(3),
    REJECT_FAILED(4),
    OPEN(5);

    private final int value;

    TRADE_STATUS(int i) {
        value = i;
    }

    public int getValue() {
        return value;
    }
}
