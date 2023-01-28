package org.investpro.investpro;

public enum TRADE_ERRORS {
    TRADE_NOT_FOUND(0),
    TRADE_INVALID_AMOUNT(1),
    TRADE_INVALID_CURRENCY(2),
    TRADE_INVALID_DATE(3),
    TRADE_INVALID_NUMBER(4),
    TRADE_INVALID_SIZE(5),

    TRADE_NO_DATA(6),

    TRADE_NO_ACCOUNT(7),

    TRADE_NO_INSTRUMENT(8),

    TRADE_NO_AMOUNT(9),

    TRADE_NO_CURRENCY(10),

    TRADE_NO_DATE(11),

    TRADE_NO_NUMBER(12),

    TRADE_NO_SIZE(13),

    TRADE_NO_ACCOUNT_ID(14),

    TRADE_NO_INSTRUMENT_ID(15),

    TRADE_NO_AMOUNT_OR_CURRENCY(16),

    TRADE_NO_DATE_OR_NUMBER(17),

    TRADE_NO_SIZE_OR_ACCOUNT_ID(18),
    ORDER_CANCEL(19),
    ORDER_REJECT(20),
    ORDER_REPLACE(21),
    MARGIN_WARGIN(22),
    MARGIN_STOP_ORDER(23);

    private final int value;

    TRADE_ERRORS(int value) {
        this.value = value;


    }

    public int Value() {
        return value;
    }
}
