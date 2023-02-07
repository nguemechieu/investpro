package org.investpro.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public enum TradeExchanges {
    BINANCE_US, COINBASE, OANDA,
    /**
     *
     */
    BITFINEX,
    BITSTAMP, BINANCE_WORLD;

    @Contract(pure = true)
    public static TradeExchanges getExchange(@NotNull String exchange) {
        return switch (exchange.toUpperCase()) {
            case "COINBASE_PRO", "BINANCE_US" -> BINANCE_US;
            case "COINBASE" -> COINBASE;
            case "OANDA" -> OANDA;
            case "BITFINEX" -> BITFINEX;
            case "BITS TAMP" -> BITSTAMP;
            case "BINANCE_WORLD" -> BINANCE_WORLD;
            default -> null;
        };
    }
}


