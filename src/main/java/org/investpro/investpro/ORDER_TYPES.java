package org.investpro.investpro;

public enum ORDER_TYPES {
    OP_BUY(0),
    OP_SELL(1),
    OP_BUYLIMIT(2),
    OP_SELLLIMIT(3),
    OP_BUYSTOP(4),
    OP_SELLSTOP(5), NONE(6), LIMIT(7),
    MARKET(8),
    STOP_LOSS_LIMIT(9),
    TAKE_PROFIT_LIMIT(10),
    LIMIT_MAKER(11);
    private final int i;

    ORDER_TYPES(int type) {
        i = type;
    }

    public int getI() {
        return i;
    }
}