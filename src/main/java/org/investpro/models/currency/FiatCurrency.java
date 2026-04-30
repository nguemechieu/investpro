package org.investpro.models.currency;

import java.sql.SQLException;


public class FiatCurrency extends Currency {
    public FiatCurrency(
            String fullDisplayName,
            String shortDisplayName,
            String code,
            int fractionDigits,
            String symbol,
            String image
    ) throws SQLException, ClassNotFoundException {
        super(CurrencyType.FIAT,
                fullDisplayName,
                shortDisplayName,
                code,
                fractionDigits,
                symbol,
                image
        );

    }



}
