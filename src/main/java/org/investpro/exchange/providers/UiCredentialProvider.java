package org.investpro.exchange.providers;

import org.investpro.exchange.contracts.CredentialProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class UiCredentialProvider implements CredentialProvider {

    private static final Map<String, String> EXCHANGE_ALIASES = buildExchangeAliases();

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

            case "binance" -> {
                put("BINANCE_API_KEY", apiKey);
                put("BINANCE_API_SECRET", apiSecret);
            }

            case "binance_us" -> {
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

            case "interactive_brokers" -> {
                put("IBKR_API_KEY", apiKey);
                put("IBKR_API_SECRET", apiSecret);
                put("IBKR_ACCOUNT_ID", accountId);
                put("IBKR_ACCESS_TOKEN", apiSecret);
                put("IBK_API_KEY", apiKey);
                put("IBK_API_SECRET", apiSecret);
                put("IBK_ACCOUNT_ID", accountId);
                put("IBK_ACCESS_TOKEN", apiSecret);
            }

            case "stellar_network", "stellar" -> {
                put("STELLAR_PUBLIC_KEY", accountId != null && !accountId.isBlank() ? accountId : apiKey);
                put("STELLAR_SECRET_KEY", apiSecret);
                put("STELLAR_NETWORK", tradingMode);
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

        String compact = value
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        return EXCHANGE_ALIASES.getOrDefault(compact, compact);
    }

    private static Map<String, String> buildExchangeAliases() {
        Map<String, String> aliases = new HashMap<>();

        addAliases(aliases, "alpaca", "alpaca", "alpacastocks", "alpacaequities", "alpacacrypto");
        addAliases(aliases, "binance", "binance", "binanceglobal", "binanceinternational");
        addAliases(aliases, "binance_us", "binanceus", "binanceusa", "binanceamerica", "binanceunitedstates");
        addAliases(aliases, "bitfinex", "bitfinex", "bitfinexus");
        addAliases(aliases, "coinbase", "coinbase", "coinbasepro", "coinbaseadvanced", "coinbaseadvancedtrade",
                "coinbaseat", "coinbasebrokerage");
        addAliases(aliases, "interactive_brokers", "interactivebrokers", "interactivebroker", "ib",
                "ibk", "ibkr", "schwab", "charlesschwab");
        addAliases(aliases, "oanda", "oanda", "oandafx", "oandaforex", "oandacfd", "oandafxcfd");
        addAliases(aliases, "stellar_network", "stellar", "stellarnetwork", "stellarx", "xlm");

        return Collections.unmodifiableMap(aliases);
    }

    private static void addAliases(Map<String, String> aliases, String canonical, String... values) {
        for (String value : values) {
            aliases.put(value, canonical);
        }
    }
}
