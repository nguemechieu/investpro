package org.investpro.asset;

import java.util.Locale;

public enum ExchangeId {
    COINBASE("coinbase"),
    OANDA("oanda"),
    BINANCE_US("binance-us"),
    STELLAR("stellar"),
    ALPACA("alpaca"),
    IBKR("ibkr"),
    UNKNOWN("unknown");

    private final String id;

    ExchangeId(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static ExchangeId from(String value) {
        String text = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (text.contains("coinbase")) {
            return COINBASE;
        }
        if (text.contains("oanda")) {
            return OANDA;
        }
        if (text.contains("binance")) {
            return BINANCE_US;
        }
        if (text.contains("stellar")) {
            return STELLAR;
        }
        if (text.contains("alpaca")) {
            return ALPACA;
        }
        if (text.contains("ibkr") || text.contains("interactive")) {
            return IBKR;
        }
        return UNKNOWN;
    }
}
