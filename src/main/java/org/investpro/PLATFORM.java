package org.investpro;

public enum PLATFORM {
    ANDROID("Android"),
    IOS("iOS"),
    MAC("Mac"),
    WINDOWS("Windows"),
    OTHER("Other"),
    BINANCE_US("Binance Us"),
    BINANCE_EU("Binance Eu"),
    BINANCE_WORLD("BinanceWorld"),
    COINBASE_PRO("CoinbasePro"),
    OANDA("cryptoinvestor.cryptoinvestor.CurrencyDataProvider.Oanda"),
    BITFINEX("Bitfinex"), BINANCE_CN(
            "BinanceCn"
    ), Ally_Invest("Ally Invest"), AMERICAN_TRADE("AMERI TRADE "), BINANCE_COM("BINANCE COM");
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
