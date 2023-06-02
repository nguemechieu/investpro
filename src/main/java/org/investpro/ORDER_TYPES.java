package org.investpro;

public enum ORDER_TYPES {
    OP_BUY(0),
    OP_SELL(1),
    OP_BUYLIMIT(2),
    OP_SELLLIMIT(3),
    OP_BUYSTOP(4),
    OP_SELLSTOP(5), NONE(6),


    TAKE_PROFIT(12),
    STOP_LOSS(13),
    LIMIT_MAKER(14), MARKET(15), STOP_LOSS_LIMIT(16),
    LIMIT(17),
    TAKE_PROFIT_LIMIT(22);
    public static final ORDER_TYPES[] values = values();
    private final int i;

    ORDER_TYPES(int type) {
        i = type;
    }

    public int getI() {
        return i;
    }
}