package org.investpro;

import org.jetbrains.annotations.NotNull;

/**
 * @author NOEL NGUEMECHIEU
 */
public class CryptoCurrency extends Currency {


    public CryptoCurrency(String fullDisplayName, String shortDisplayName,
                          String code,
                          int fractionalDigits,
                          String symbol, String image) throws Exception {
        super(
                CurrencyType.CRYPTO,
                fullDisplayName,
                shortDisplayName,
                code,
                fractionalDigits,
                symbol,
                image
        );


        this.code = code;
        this.fractionalDigits = fractionalDigits;
        this.symbol = symbol;
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;
        this.image = image;
        this.currencyType = CurrencyType.CRYPTO;


    }


    @Override
    public int compareTo(java.util.@NotNull Currency o) {
        return 0;
    }
}