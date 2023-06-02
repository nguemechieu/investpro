package org.investpro;

public enum MARKET_INFOS_MODE {
    MODE_LOTSTEP(0),

    MODE_DIGITS(1),
    MODE_MAXLOT(2),
    MODE_MINLOT(3),
    MODE_TICKVALUE(4), MODE_MARGINREQUIRED(9),
    MODE_TICKSIZE(5), MODE_ASK(6),
    MODE_BID(7), MODE_POINT(8);
    public static final int init_error = 0;
    private final int value;

    MARKET_INFOS_MODE(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}
