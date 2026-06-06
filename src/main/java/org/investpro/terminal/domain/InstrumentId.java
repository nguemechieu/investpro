package org.investpro.terminal.domain;

import java.util.Locale;

public record InstrumentId(String providerId, String symbol, String nativeSymbol) {
    public InstrumentId {
        providerId = normalize(providerId);
        symbol = normalize(symbol);
        nativeSymbol = nativeSymbol == null || nativeSymbol.isBlank() ? symbol : nativeSymbol.trim().toUpperCase(Locale.ROOT);
        if (providerId.isBlank() || symbol.isBlank()) {
            throw new IllegalArgumentException("providerId and symbol are required");
        }
    }

    public String key() {
        return providerId + ":" + symbol;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
