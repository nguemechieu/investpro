package org.investpro;

public enum TRADE_PLATFORM {
    CoinbasePro("CoinbasePro"),
    BinanceEu("BinanceEu"),
    BinanceUs("BinanceUs"),
    Bittrex("Bittrex"),
    Coinbase("Coinbase"),
    Oanda("Oanda"),
    Okex("Okex"),
    OkexFutures("OkexFutures"),
    OkexSwap("OkexSwap");

    private final String coinbase;

    TRADE_PLATFORM(String coinbase) {
        this.coinbase = coinbase;
    }

    public String getCoinbase() {
        return coinbase;
    }
}
