package org.investpro.models.currency;

import java.sql.SQLException;

public class NullCryptoCurrency extends CryptoCurrency {
    NullCryptoCurrency() throws SQLException, ClassNotFoundException {
        super(

                "Null Crypto Currency",
                "Null Crypto Currency",
                "XXX",
                8,
                "¤¤¤",
                "https://i.ibb.co/5Y3mZ5Y/null-crypto-currency.png"
        );

    }
}