package org.investpro.investpro;

public enum TRADE_ORDER_TYPE {
    TRADE_ORDER,
    LIMIT_ORDER,
    STOP_ORDER,
    STOP_LIMIT_ORDER,
    STOP_MARKET_ORDER,
    TAKE_PROFIT_ORDER,
    BUY, SELL, BUY_STOP, SELL_LIMIT, SELL_STOP, ALL, BUY_LIMIT, NONE;

    public static final TRADE_ORDER_TYPE[] VALUES = values();

    public static TRADE_ORDER_TYPE valueOf(int i) {
        return VALUES[i];

    }
}
