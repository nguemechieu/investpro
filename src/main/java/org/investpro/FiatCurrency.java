package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;


/**
 * @author NOEL NGUEMECHIEU
 */
public class FiatCurrency extends Currency {
    Locale locale;
    String centralBank;
    int numericCode;

    protected FiatCurrency() throws Exception {
        super(
                CurrencyType.FIAT,
                "United States Dollar",
                "USD",
                "USD",
                2,
                "$",
                "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4/Flag_of_the_United_States.svg/320px-Flag_of_the_United_States.svg.png"
        );
        locale = Locale.US;
        centralBank = "XXX";
        numericCode = -1;
    }

    protected FiatCurrency(String fullDisplayName, String shortDisplayName, String code, int fractionalDigits,
                           String symbol, Locale locale, String centralBank, int numericCode) throws Exception {
        super(CurrencyType.FIAT, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, "https://upload.wikimedia.org/wikipedia/commons/thumb/a/a4");

        Objects.requireNonNull(locale, "locale must not be null");
        Objects.requireNonNull(centralBank, "centralBank must not be null");

        if (numericCode < 0 || numericCode > 999) {
            throw new IllegalArgumentException(STR."numeric code must be in range [0, 999] in accordance with ISO-4217, but was: \{numericCode}");
        }

        this.locale = locale;
        this.centralBank = centralBank;
        this.numericCode = numericCode;
    }

    public Locale getLocale() {
        return locale;
    }

    @Override
    public int compareTo(java.util.@NotNull Currency o) {
        return 0;
    }
}
