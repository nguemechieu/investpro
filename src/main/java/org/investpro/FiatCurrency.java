package org.investpro;

import org.jetbrains.annotations.NotNull;


public class FiatCurrency extends Currency {
    protected FiatCurrency(
            String fullDisplayName,
            String shortDisplayName,
            String code,
            int fractionDigits,
            String symbol,
            String image
    ) {
        super(CurrencyType.FIAT,
                fullDisplayName,
                shortDisplayName,
                code,
                fractionDigits,
                symbol,
                image
        );

    }


    @Override
    public int compareTo(@NotNull Currency o) {
        return 0;
    }

    @Override
    public int compareTo(java.util.@NotNull Currency o) {
        return 0;
    }


}