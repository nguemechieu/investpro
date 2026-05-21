package org.investpro.models.currency;

import java.util.Locale;

/**
 * Placeholder currency used when an exchange returns an unknown asset code.
 */
public final class UnknownCurrency extends Currency {

    private static final int DEFAULT_FRACTIONAL_DIGITS = 8;

    private UnknownCurrency(String code) {
        super(
                CurrencyType.UNKNOWN,
                "Unknown " + code,
                code,
                normalizeCode(code),
                DEFAULT_FRACTIONAL_DIGITS,
                normalizeCode(code),
                normalizeCode(code));
    }

    public static UnknownCurrency of(String code) {
        return new UnknownCurrency(code);
    }

    private static String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            return "UNKNOWN";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }
}
