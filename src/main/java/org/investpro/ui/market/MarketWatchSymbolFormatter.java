package org.investpro.ui.market;

import org.investpro.models.market.MarketInstrument;
import org.investpro.models.trading.TradePair;

public final class MarketWatchSymbolFormatter {

    private MarketWatchSymbolFormatter() {
    }

    public static String displaySymbol(MarketInstrument instrument, TradePair fallbackPair) {
        if (instrument != null) {
            if (instrument.isDerivative()) {
                return firstNonBlank(instrument.nativeSymbol(), instrument.displaySymbol(), fallbackSymbol(fallbackPair));
            }
            return firstNonBlank(instrument.displaySymbol(), fallbackSymbol(fallbackPair), instrument.nativeSymbol());
        }
        return fallbackSymbol(fallbackPair);
    }

    public static String baseAsset(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return "";
        }
        String value = symbol.trim();
        int slash = value.indexOf('/');
        if (slash > 0) {
            return value.substring(0, slash);
        }
        int dash = value.indexOf('-');
        if (dash > 0) {
            return value.substring(0, dash);
        }
        int space = value.indexOf(' ');
        return space > 0 ? value.substring(0, space) : value;
    }

    private static String fallbackSymbol(TradePair pair) {
        return pair == null ? "" : pair.toString('/');
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
