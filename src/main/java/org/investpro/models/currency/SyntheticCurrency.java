package org.investpro.models.currency;

import java.util.Locale;

/**
 * Generic currency-like instrument representation for non-fiat/non-crypto assets
 * such as indices, metals, and stock symbols.
 */
public final class SyntheticCurrency extends Currency {

    public SyntheticCurrency(
            CurrencyType currencyType,
            String fullDisplayName,
            String shortDisplayName,
            String code,
            int fractionalDigits,
            String symbol,
            String image) {
        super(
                currencyType == null ? CurrencyType.UNKNOWN : currencyType,
                safe(fullDisplayName, code),
                safe(shortDisplayName, code),
                normalizeCode(code),
                Math.max(0, fractionalDigits),
                safe(symbol, normalizeCode(code)),
                safe(image, normalizeCode(code)));
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "UNKNOWN";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
