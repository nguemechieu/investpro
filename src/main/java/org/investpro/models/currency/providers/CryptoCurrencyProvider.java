package org.investpro.models.currency.providers;

import org.investpro.models.currency.CryptoCurrency;
import org.investpro.models.currency.Currency;
import org.investpro.models.currency.CurrencyType;
import org.investpro.models.currency.spi.CurrencyProvider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CryptoCurrencyProvider implements CurrencyProvider {

    @Override
    public String providerId() {
        return "CRYPTO";
    }

    @Override
    public String displayName() {
        return "Crypto Currencies";
    }

    @Override
    public Set<String> supportedCurrencyTypes() {
        return Set.of("CRYPTO");
    }

    @Override
    public Collection<Currency> getCurrencies() {
        Map<String, Currency> byCode = new LinkedHashMap<>();

        // Leverage existing static registry if any crypto has already been loaded.
        for (Currency currency : Currency.CURRENCIES.values()) {
            if (currency != null && currency.getCurrencyType() == CurrencyType.CRYPTO && currency.getCode() != null) {
                byCode.putIfAbsent(currency.getCode().toUpperCase(), currency);
            }
        }

        List<CurrencySeed> seeds = List.of(
                seed("Bitcoin", "BTC"),
                seed("Ethereum", "ETH"),
                seed("Tether", "USDT"),
                seed("USD Coin", "USDC"),
                seed("Solona", "SOL"),
                seed("XRP", "XRP"),
                seed("Cardano", "ADA"),
                seed("Dogecoin", "DOGE"),
                seed("Avalanche", "AVAX"),
                seed("Polkadot", "DOT"),
                seed("Chainlink", "LINK"),
                seed("Litecoin", "LTC"),
                seed("Bitcoin Cash", "BCH"),
                seed("Stellar", "XLM"),
                seed("Algorand", "ALGO"));

        for (CurrencySeed seed : seeds) {
            if (byCode.containsKey(seed.code())) {
                continue;
            }
            try {
                byCode.put(seed.code(),
                        new CryptoCurrency(seed.name(), seed.code(), seed.code(), 8, seed.code(), seed.code()));
            } catch (Exception ignored) {
                // Keep provider resilient.
            }
        }

        return new ArrayList<>(byCode.values());
    }

    private CurrencySeed seed(String name, String code) {
        return new CurrencySeed(name, code);
    }

    private record CurrencySeed(String name, String code) {
    }
}
