package org.investpro;

import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

@Getter
@Setter
public class FiatCurrency extends Currency {

    private final Locale locale;
    private final String centralBank;
    private final int numericCode;

    protected FiatCurrency() {
        locale = Locale.US;
        centralBank = "";
        numericCode = -1;
    }

    protected FiatCurrency(String fullDisplayName, String shortDisplayName, String code, int fractionalDigits,
                           String symbol, Locale locale, String centralBank, int numericCode) {


        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(centralBank, "centralBank must not be null");

        if (numericCode < 0 || numericCode > 999) {
            throw new IllegalArgumentException("numeric code must be in range [0, 999] in" +
                    " accordance with ISO-4217, but was: " + numericCode);
        }

        this.locale = locale;
        this.centralBank = centralBank;
        this.numericCode = numericCode;
        this.currencyType = CurrencyType.FIAT.name();
        this.fractionalDigits = fractionalDigits;
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;
        this.code = code;
    }

    @Override
    public int compareTo(@NotNull Currency o) {
        return 0;
    }

}
