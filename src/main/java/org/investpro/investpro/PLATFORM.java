package org.investpro.investpro;

public enum PLATFORM {
    ANDROID("Android"),
    IOS("iOS"),
    MAC("Mac"),
    WINDOWS("Windows"),
    OTHER("Other"),
    BINANCE_US("BinanceUs"),
    BINANCE_EU("BinanceEu"),
    BINANCE_WORLD("BinanceWorld"),
    COINBASE_PRO("CoinbasePro"),
    OANDA("Oanda"),
    BITFINEX("Bitfinex"), BINANCE_CN(
            "BinanceCn"
    );
    private String value;

    PLATFORM(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
