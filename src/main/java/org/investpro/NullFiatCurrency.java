package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.util.Currency;
import java.util.Locale;

public class NullFiatCurrency extends FiatCurrency {
    public NullFiatCurrency
            (String fullDisplayName, String shortDisplayName, String code, int fractionalDigits,
             String symbol, Locale locale, String centralBank, int numericCode, String image
            ) {
        super(
                fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, locale, centralBank, numericCode, image


        );

    }


    @Override
    public int compareTo(@NotNull Currency o) {
        return 0;
    }

    @Override
    public int compareTo(@NotNull org.investpro.Currency o) {
        return 0;
    }
}
