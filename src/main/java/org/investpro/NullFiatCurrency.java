package org.investpro;

import java.util.Locale;

public class NullFiatCurrency extends FiatCurrency {
    public NullFiatCurrency() throws Exception {
        super(
                "NULL", "NULL", "NULL", 0, "NULL", Locale.getDefault(), "xxx", 1
        );
        this.currencyType = CurrencyType.NULL;
        this.symbol = "XXX";

        this.centralBank = "N/A";
        this.numericCode = -1;
        this.fullDisplayName = "NULL";
        this.shortDisplayName = "NULL";
        this.code = "NULL";
        this.fractionalDigits = 10;
    }


}
