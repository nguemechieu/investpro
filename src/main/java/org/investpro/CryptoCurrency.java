package org.investpro;

import org.jetbrains.annotations.NotNull;

public abstract class CryptoCurrency extends Currency {


    protected CryptoCurrency(String fullDisplayName, String shortDisplayName, String code, int fractionalDigits, String symbol, String image) {
        super(CurrencyType.CRYPTO, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image);

        this.code = code;
        this.fractionalDigits = fractionalDigits;
        Currency.symbol = symbol;
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;

        Currency.image = image;

    }

    @Override
    public String toString() {
        return
                "currencyType=" + currencyType +
                        ", fullDisplayName='" + fullDisplayName + '\'' +
                        ", code='" + code + '\'' +
                        ", fractionalDigits=" + fractionalDigits +
                        ", symbol='" + symbol + '\'' +
                        ", shortDisplayName='" + shortDisplayName + '\'';
    }


    public abstract int compareTo(java.util.@NotNull Currency o);
}
