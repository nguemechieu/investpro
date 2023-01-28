package org.investpro.investpro;

public enum OrderStatus {
    None(0),
    NEW(1),
    PARTIALLY_FILLED(2),
    FILLED(3),
    CANCELED(4),
    EXPIRED(5),
    EXPIRED_AND_CANCELED(6),
    CANCELED_AND_FILLED(7),
    ;

    private final int value;

    OrderStatus(int i) {
        value = i;
    }

    public int getValue() {
        return value;
    }
}
