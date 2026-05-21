package org.investpro.models.currency.providers;

import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyType;
import org.investpro.models.currency.SyntheticCurrency;
import org.investpro.models.currency.spi.CurrencyProvider;

import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class MetalCurrencyProvider implements CurrencyProvider {

    @Override
    public String providerId() {
        return "METALS";
    }

    @Override
    public String displayName() {
        return "Precious Metals";
    }

    @Override
    public Set<String> supportedCurrencyTypes() {
        return Set.of("METAL", "FOREX");
    }

    @Override
    public Collection<Currency> getCurrencies() {
        return List.of(
                new SyntheticCurrency(CurrencyType.METAL, "Gold", "Gold", "XAU", 3, "XAU", "XAU"),
                new SyntheticCurrency(CurrencyType.METAL, "Silver", "Silver", "XAG", 3, "XAG", "XAG"),
                new SyntheticCurrency(CurrencyType.METAL, "Platinum", "Platinum", "XPT", 3, "XPT", "XPT"),
                new SyntheticCurrency(CurrencyType.METAL, "Palladium", "Palladium", "XPD", 3, "XPD", "XPD")
        );
    }
}
