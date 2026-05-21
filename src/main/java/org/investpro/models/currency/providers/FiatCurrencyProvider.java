package org.investpro.models.currency.providers;

import org.investpro.models.currency.Currency;
import org.investpro.models.currency.FiatCurrency;
import org.investpro.models.currency.spi.CurrencyProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class FiatCurrencyProvider implements CurrencyProvider {

    @Override
    public String providerId() {
        return "FIAT";
    }

    @Override
    public String displayName() {
        return "Fiat Currencies";
    }

    @Override
    public Set<String> supportedCurrencyTypes() {
        return Set.of("FIAT", "FOREX");
    }

    @Override
    public Collection<Currency> getCurrencies() {
        List<Currency> result = new ArrayList<>();

        add(result, "United States dollar", "U.S. dollar", "USD", 2, "$", "USD");
        add(result, "Euro", "Euro", "EUR", 2, "€", "EUR");
        add(result, "Pound sterling", "Pound", "GBP", 2, "£", "GBP");
        add(result, "Japanese yen", "Yen", "JPY", 0, "¥", "JPY");
        add(result, "Swiss franc", "Franc", "CHF", 2, "Fr.", "CHF");
        add(result, "Canadian dollar", "Canadian dollar", "CAD", 2, "CA$", "CAD");
        add(result, "Australian dollar", "Australian dollar", "AUD", 2, "A$", "AUD");
        add(result, "New Zealand dollar", "New Zealand dollar", "NZD", 2, "NZ$", "NZD");
        add(result, "Chinese yuan", "Chinese yuan", "CNY", 2, "¥", "CNY");
        add(result, "Hong Kong dollar", "Hong Kong dollar", "HKD", 2, "HK$", "HKD");
        add(result, "Singapore dollar", "Singapore dollar", "SGD", 2, "S$", "SGD");
        add(result, "South African rand", "Rand", "ZAR", 2, "R", "ZAR");
        add(result, "Mexican peso", "Mexican peso", "MXN", 2, "MX$", "MXN");
        add(result, "Brazilian real", "Real", "BRL", 2, "R$", "BRL");
        add(result, "Indian rupee", "Rupee", "INR", 2, "₹", "INR");

        return List.copyOf(new LinkedHashSet<>(result));
    }

    private void add(List<Currency> result,
                     String fullName,
                     String shortName,
                     String code,
                     int digits,
                     String symbol,
                     String image) {
        try {
            result.add(new FiatCurrency(fullName, shortName, code, digits, symbol, image));
        } catch (Exception ignored) {
            // Provider remains resilient; registry logs provider-level issues if needed.
        }
    }
}
