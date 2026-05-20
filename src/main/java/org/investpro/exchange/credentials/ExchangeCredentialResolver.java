package org.investpro.exchange.credentials;


import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.contracts.CredentialProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;


@Slf4j
public record ExchangeCredentialResolver(CredentialProvider provider) {

    private static final Map<String, String> EXCHANGE_ALIASES = buildExchangeAliases();

    public ExchangeCredentials resolve(String exchangeId) {
        String id = normalize(exchangeId);

        return switch (id) {
            case "coinbase" -> resolveCoinbase();
            case "binance" -> resolveBinance();
            case "binance_us" -> resolveBinanceUs();
            case "bitfinex" -> resolveBitfinex();
            case "oanda" -> resolveOanda();
            case "alpaca" -> resolveAlpaca();
            case "interactive_brokers" -> resolveInteractiveBrokers();
            case "stellar_network" -> resolveStellar();
            default -> new ExchangeCredentials(id, null, null, null, null, null, null, false);
        };
    }

    private ExchangeCredentials resolveInteractiveBrokers() {
        return new ExchangeCredentials(
                "interactive_brokers",
                firstPresent(provider.getOrNull("IBKR_API_KEY"), provider.getOrNull("IBK_API_KEY")),
                firstPresent(provider.getOrNull("IBKR_API_SECRET"), provider.getOrNull("IBK_API_SECRET")),
                null,
                null,
                firstPresent(provider.getOrNull("IBKR_ACCESS_TOKEN"), provider.getOrNull("IBK_ACCESS_TOKEN")),
                firstPresent(provider.getOrNull("IBKR_ACCOUNT_ID"), provider.getOrNull("IBK_ACCOUNT_ID")),
                Boolean.parseBoolean(firstPresent(provider.getOrNull("IBKR_SANDBOX"), provider.getOrNull("IBK_SANDBOX")))
        );
    }

    private ExchangeCredentials resolveCoinbase() {
        return new ExchangeCredentials(
                "coinbase",
                provider.getOrNull("COINBASE_API_KEY"),
                provider.getOrNull("COINBASE_API_SECRET"),
                provider.getOrNull("COINBASE_KEY_NAME"),
                provider.getOrNull("COINBASE_PRIVATE_KEY"),
                null,
                null,
                false
        );
    }

    private ExchangeCredentials resolveBinance() {
        return new ExchangeCredentials(
                "binance",
                provider.getOrNull("BINANCE_API_KEY"),
                provider.getOrNull("BINANCE_API_SECRET"),
                null,
                null,
                null,
                null,
                false
        );
    }

    private ExchangeCredentials resolveBinanceUs() {
        return new ExchangeCredentials(
                "binance_us",
                provider.getOrNull("BINANCE_US_API_KEY"),
                provider.getOrNull("BINANCE_US_API_SECRET"),
                null,
                null,
                null,
                null,
                false
        );
    }

    private ExchangeCredentials resolveBitfinex() {
        return new ExchangeCredentials(
                "bitfinex",
                provider.getOrNull("BITFINEX_API_KEY"),
                provider.getOrNull("BITFINEX_API_SECRET"),
                null,
                null,
                null,
                null,
                false
        );
    }

    private ExchangeCredentials resolveOanda() {
        return new ExchangeCredentials(
                "oanda",
                provider.getOrNull("OANDA_API_KEY"),
                provider.getOrNull("OANDA_API_SECRET"),
                null,
                null,
                null,
                provider.getOrNull("OANDA_ACCOUNT_ID"),
                Boolean.parseBoolean(provider.getOrNull("OANDA_SANDBOX"))
        );
    }

    private ExchangeCredentials resolveAlpaca() {
        return new ExchangeCredentials(
                "alpaca",
                provider.getOrNull("ALPACA_API_KEY"),
                provider.getOrNull("ALPACA_API_SECRET"),
                null,
                null,
                null,
                null,
                Boolean.parseBoolean(provider.getOrNull("ALPACA_PAPER"))
        );
    }

    private ExchangeCredentials resolveStellar() {
        String publicKey = firstPresent(
                provider.getOrNull("STELLAR_PUBLIC_KEY"),
                provider.getOrNull("STELLAR_NETWORK_API_KEY"),
                provider.getOrNull("STELLAR_NETWORK_ACCOUNT_ID"));
        String secretKey = firstPresent(
                provider.getOrNull("STELLAR_SECRET_KEY"),
                provider.getOrNull("STELLAR_NETWORK_API_SECRET"));
        String network = firstPresent(
                provider.getOrNull("STELLAR_NETWORK"),
                provider.getOrNull("STELLAR_NETWORK_TRADING_MODE"));
        return new ExchangeCredentials(
                "stellar_network",
                publicKey,
                secretKey,
                null,
                secretKey,
                null,
                publicKey,
                isPaperStellarNetwork(network)
        );
    }

    private String firstPresent(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private boolean isPaperStellarNetwork(String network) {
        if (network == null || network.isBlank()) {
            return false;
        }
        String normalized = network.trim();
        return "TESTNET".equalsIgnoreCase(normalized)
                || "SANDBOX".equalsIgnoreCase(normalized)
                || "PAPER".equalsIgnoreCase(normalized)
                || "PAPER TRADING".equalsIgnoreCase(normalized);
    }

    private String normalize(String exchangeId) {
        if (exchangeId == null) {
            return "";
        }

        String compact = exchangeId
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
