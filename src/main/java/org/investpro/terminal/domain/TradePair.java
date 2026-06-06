package org.investpro.terminal.domain;

public record TradePair(Asset base, Asset quote) {
    public TradePair {
        if (base == null || quote == null) {
            throw new IllegalArgumentException("base and quote assets are required");
        }
        if (base.canonicalKey().equals(quote.canonicalKey())) {
            throw new IllegalArgumentException("base and quote assets must differ");
        }
    }

    public String displaySymbol() {
        return base.code() + "/" + quote.code();
    }

    public String canonicalKey() {
        return base.canonicalKey() + "/" + quote.canonicalKey();
    }

    public TradePair reversed() {
        return new TradePair(quote, base);
    }
}
