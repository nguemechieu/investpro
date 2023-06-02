package org.investpro;

import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Currency;

public class NullFiatCurrency extends FiatCurrency {
    public NullFiatCurrency(CurrencyType fiat, String xxx, String xxx1, String xxx2, int i, String xxx3, String xxxx) throws SQLException {
        super(
                fiat,
                xxx,
                xxx1,
                xxx2,
                i,
                xxx3,
                xxxx
        );
    }

    @Override
    public int compareTo(cryptoinvestor.cryptoinvestor.@NotNull Currency o) {
        return 0;
    }

    @Override
    public int compareTo(@NotNull Currency o) {
        return 0;
    }

}
