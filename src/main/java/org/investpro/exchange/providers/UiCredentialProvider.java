package org.investpro.exchange.providers;

import org.investpro.exchange.contracts.CredentialProvider;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UiCredentialProvider implements CredentialProvider {

    private final Map<String, String> values = new ConcurrentHashMap<>();

    public UiCredentialProvider(
            String exchangeId,
            String apiKey,
            String apiSecret,
            String accountId,
            String tradingMode
    ) {
        String prefix = normalize(exchangeId);

        put(prefix + "_API_KEY", apiKey);
        put(prefix + "_API_SECRET", apiSecret);
        put(prefix + "_ACCOUNT_ID", accountId);
        put(prefix + "_TRADING_MODE", tradingMode);

        switch (prefix) {
            case "coinbase" -> {
                put("COINBASE_API_KEY", apiKey);
                put("COINBASE_API_SECRET", apiSecret);

                // If user pastes Advanced Trade key name/private key into these fields.
                put("COINBASE_KEY_NAME", apiKey);
                put("COINBASE_PRIVATE_KEY", apiSecret);
            }

            case "binanceus" -> {
                put("BINANCE_US_API_KEY", apiKey);
                put("BINANCE_US_API_SECRET", apiSecret);
            }

            case "oanda" -> {
                put("OANDA_API_KEY", apiKey);
                put("OANDA_API_SECRET", apiSecret);
                put("OANDA_ACCOUNT_ID", accountId);
            }

            case "alpaca" -> {
                put("ALPACA_API_KEY", apiKey);
                put("ALPACA_API_SECRET", apiSecret);
            }

            case "bitfinex" -> {
                put("BITFINEX_API_KEY", apiKey);
                put("BITFINEX_API_SECRET", apiSecret);
            }
        }
    }

    @Override
    public Optional<String> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }

        String value = values.get(key);

        if (value == null || value.isBlank()) {
            return Optional.empty();
        }

        return Optional.of(value);
    }

    private void put(String key, String value) {
        if (key != null && value != null && !value.isBlank()) {
            values.put(key, value.trim());
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .trim()
                .toLowerCase(java.util.Locale.ROOT)
                .replace(" ", "")
                .replace("-", "_");
    }
}