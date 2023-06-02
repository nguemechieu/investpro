package org.investpro;

public class SymbolDataBuilder {
    private String symbol;
    private String bid;
    private String ask;

    public SymbolDataBuilder setSymbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    public SymbolDataBuilder setBid(String bid) {
        this.bid = bid;
        return this;
    }

    public SymbolDataBuilder setAsk(String ask) {
        this.ask = ask;
        return this;
    }

    public SymbolData createSymbolData() {
        return new SymbolData(symbol, bid, ask);
    }
}