package org.investpro.investpro;

public enum TradeExchanges {
    BINANCE_US, COINBASE, OANDA,
    /**
     *
     */
 BITFINEX,
    /**
     *
     */
 BITSTAMP, BINANCE_WORLD;

    public static TradeExchanges getExchange(String exchange) {
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


