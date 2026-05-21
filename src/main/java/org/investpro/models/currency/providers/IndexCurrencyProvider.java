package org.investpro.models.currency.providers;

import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyType;
import org.investpro.models.currency.SyntheticCurrency;
import org.investpro.models.currency.spi.CurrencyProvider;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class IndexCurrencyProvider implements CurrencyProvider {

    @Override
    public String providerId() {
        return "INDEX";
    }

    @Override
    public String displayName() {
        return "Index Symbols";
    }

    @Override
    public Set<String> supportedCurrencyTypes() {
        return Set.of("INDEX");
    }

    @Override
    public Collection<Currency> getCurrencies() {
        return List.of(
                new SyntheticCurrency(CurrencyType.INDEX, "S&P 500", "SPX500", "SPX500", 2, "SPX500", "SPX500"),
                new SyntheticCurrency(CurrencyType.INDEX, "Nasdaq 100", "NAS100", "NAS100", 2, "NAS100", "NAS100"),
                new SyntheticCurrency(CurrencyType.INDEX, "Dow Jones 30", "US30", "US30", 2, "US30", "US30"),
                new SyntheticCurrency(CurrencyType.INDEX, "FTSE 100", "UK100", "UK100", 2, "UK100", "UK100"),
                new SyntheticCurrency(CurrencyType.INDEX, "DAX 40", "GER40", "GER40", 2, "GER40", "GER40"),
                new SyntheticCurrency(CurrencyType.INDEX, "CAC 40", "FRA40", "FRA40", 2, "FRA40", "FRA40"),
                new SyntheticCurrency(CurrencyType.INDEX, "Nikkei 225", "JP225", "JP225", 2, "JP225", "JP225")
        );
    }
}
