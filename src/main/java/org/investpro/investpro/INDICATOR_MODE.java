package org.investpro.investpro;

public enum INDICATOR_MODE {

    MODE_DEMA(2),
    MODE_DEMA_DEMA(9),
    MODE_DEMA_RSI(8),
    MODE_EMA(1),
    MODE_RSE(5),
    MODE_RSI(4),
    MODE_SMA(0),
    MODE_SMA_DEMA(7),
    MODE_SMA_RSI(6),
    MODE_TRIM(3);

    private final int value;


    INDICATOR_MODE(int i) {
        this.value = i;
    }

    public int getValue() {
        return value;
    }
}
