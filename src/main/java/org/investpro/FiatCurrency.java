package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;


public abstract class FiatCurrency extends Currency {
    Locale locale;
    String centralBank;
    int numericCode;


    protected FiatCurrency(String fullDisplayName, String shortDisplayName, String code, int fractionalDigits,
                           String symbol, Locale locale, String centralBank, int numericCode, String image) {
        super(CurrencyType.FIAT, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image);

        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(centralBank, "centralBank must not be null");

        if (numericCode < 0 || numericCode > 999) {
            throw new IllegalArgumentException("numeric code must be in range [0, 999] in" +
                    " accordance with ISO-4217, but was: " + numericCode);
        }

        this.locale = locale;
        this.centralBank = centralBank;
        this.numericCode = numericCode;
    }


    @Override
    public String toString() {
        return
                "locale=" + locale +
                        ", centralBank='" + centralBank + '\'' +
                        ", numericCode=" + numericCode +
                        ", code='" + code + '\'' +
                        ", fractionalDigits=" + fractionalDigits +
                        ", symbol='" + symbol + '\'';
    }

    public abstract int compareTo(@NotNull java.util.Currency o);
}
