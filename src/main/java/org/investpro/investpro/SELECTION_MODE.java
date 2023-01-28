package org.investpro.investpro;

public enum SELECTION_MODE {
    MODE_TRADES(0),
    MODE_HISTORY(2),
    SELECT_BY_TICKET(3),
    SELECT_BY_TYPE(4), SELECT_BY_SIZE(5), SELECT_BY_PRICE(6), SELECT_BY_POS(7);

    private final int value;

    SELECTION_MODE(int i) {
        value = i;
    }

    public int getValue() {
        return value;
    }
}
