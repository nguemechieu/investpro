package org.investpro.models.currency;

import java.sql.SQLException;

/**
 * @author NOEL NGUEMECHIEU
 */
public class CryptoCurrency extends Currency {


    public CryptoCurrency(String fullDisplayName, String shortDisplayName, String code,
                          int fractionalDigits, String symbol, String image

    ) throws SQLException, ClassNotFoundException {
        super(CurrencyType.CRYPTO, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, image);

        this.code = code;
        this.fractionalDigits = fractionalDigits;
        this.symbol = symbol;
        this.setImage(image);
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;


    }


}
