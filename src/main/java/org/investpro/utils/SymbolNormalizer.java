package org.investpro.utils;

import java.util.List;

/**
 * Stateless utility for converting trading symbol strings between exchange formats.
 * <p>
 * Supported formats:
 * <ul>
 *   <li>Slash:     EUR/USD  (canonical / internal)</li>
 *   <li>Underscore: EUR_USD (OANDA)</li>
 *   <li>Dash:      EUR-USD  (Coinbase)</li>
 *   <li>Compact:   EURUSD   (BinanceUS)</li>
 * </ul>
 */
public final class SymbolNormalizer {

    /** Known quote currency suffixes ordered longest-first to avoid ambiguous splits. */
    private static final List<String> KNOWN_QUOTES = List.of(
            "USDT", "USDC", "BUSD",
            "USD", "EUR", "GBP", "JPY", "AUD", "CHF", "CAD", "NZD",
            "SGD", "HKD", "SEK", "NOK", "DKK", "PLN", "CNH", "ZAR", "MXN",
            "BTC", "ETH"
    );

    private SymbolNormalizer() {}

    /**
     * Normalise any supported format to the canonical internal format: "BASE/QUOTE".
     */
    public static String normalize(String symbol) {
        if (symbol == null || symbol.isBlank()) return symbol;
        String s = symbol.strip().toUpperCase();
        if (s.contains("/")) return s;
        if (s.contains("_")) return s.replace('_', '/');
        if (s.contains("-")) return s.replace('-', '/');
        // compact: try to split at a known quote currency
        return splitCompact(s);
    }

    /** Convert to "BASE/QUOTE" slash format. */
    public static String toSlash(String symbol) {
        return normalize(symbol);
    }

    /** Convert to "BASE_QUOTE" underscore format (OANDA). */
    public static String toUnderscore(String symbol) {
        return normalize(symbol).replace('/', '_');
    }

    /** Convert to "BASE-QUOTE" dash format (Coinbase). */
    public static String toDash(String symbol) {
        return normalize(symbol).replace('/', '-');
    }

    /** Convert to "BASEQUOTE" compact format (BinanceUS). */
    public static String toCompact(String symbol) {
        return normalize(symbol).replace("/", "");
    }

    /**
     * Convert a symbol to the native format expected by the given exchange.
     *
     * @param symbol     any supported format
     * @param exchangeId exchange identifier (case-insensitive)
     */
    public static String forExchange(String symbol, String exchangeId) {
        if (exchangeId == null) return normalize(symbol);
        return switch (exchangeId.toLowerCase()) {
            case "oanda" -> toUnderscore(symbol);
            case "coinbase" -> toDash(symbol);
            case "binance-us", "binanceus", "binance_us" -> toCompact(symbol);
            default -> normalize(symbol);
        };
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static String splitCompact(String compact) {
        for (String quote : KNOWN_QUOTES) {
            if (compact.endsWith(quote) && compact.length() > quote.length()) {
                String base = compact.substring(0, compact.length() - quote.length());
                if (!base.isBlank()) return base + "/" + quote;
            }
        }
        // Cannot determine split; return as-is without slash so callers can handle it
        return compact;
    }
}
