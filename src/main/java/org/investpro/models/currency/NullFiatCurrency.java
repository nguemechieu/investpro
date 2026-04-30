package org.investpro.models.currency;

import java.sql.SQLException;

public class NullFiatCurrency extends FiatCurrency {

    public NullFiatCurrency() throws SQLException, ClassNotFoundException {
        super(
                "¤¤¤",
                "¤¤¤", "¤¤¤",
                2,
                "¤¤¤",
                "https://i.ibb.co/5Y3mZ5Y/null-fiat-currency.png"
        );
    }
}