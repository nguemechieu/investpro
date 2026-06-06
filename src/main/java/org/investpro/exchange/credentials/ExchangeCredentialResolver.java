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
            case "kraken" -> resolveKraken();
            case "oanda" -> resolveOanda();
            case "alpaca" -> resolveAlpaca();
            case "interactive_brokers" -> resolveInteractiveBrokers();
            case "stellar_network" -> resolveStellar();
            default -> new ExchangeCredentials(id, null, null, null, null, null, null, false);
        };
    }

    private ExchangeCredentials resolveInteractiveBrokers() {
        Map<String, String> params = new HashMap<>();
        putIfPresent(params, "host", provider.getOrNull("IBKR_HOST"), provider.getOrNull("IBK_HOST"));
        putIfPresent(params, "port", provider.getOrNull("IBKR_PORT"), provider.getOrNull("IBK_PORT"));
        putIfPresent(params, "clientId", provider.getOrNull("IBKR_CLIENT_ID"), provider.getOrNull("IBK_CLIENT_ID"));
        putIfPresent(params, "environment", provider.getOrNull("IBKR_ENVIRONMENT"), provider.getOrNull("IBK_ENVIRONMENT"));
        putIfPresent(params, "authMode", provider.getOrNull("IBKR_AUTH_MODE"), provider.getOrNull("IBK_AUTH_MODE"));
        putIfPresent(params, "clientPortalUrl",
                provider.getOrNull("IBKR_CLIENT_PORTAL_URL"),
                provider.getOrNull("IBK_CLIENT_PORTAL_URL"));
        putIfPresent(params, "allowCompeteTakeover",
                provider.getOrNull("IBKR_ALLOW_COMPETE_TAKEOVER"),
                provider.getOrNull("IBK_ALLOW_COMPETE_TAKEOVER"));
        putIfPresent(params, "username", provider.getOrNull("IBKR_USERNAME"), provider.getOrNull("IBK_USERNAME"));
        putIfPresent(params, "password", provider.getOrNull("IBKR_PASSWORD"), provider.getOrNull("IBK_PASSWORD"));
        putIfPresent(params, "twoFactorCode",
                provider.getOrNull("IBKR_TWO_FACTOR_CODE"),
                provider.getOrNull("IBK_TWO_FACTOR_CODE"));

        String sandbox = firstPresent(
                provider.getOrNull("IBKR_SANDBOX"),
                provider.getOrNull("IBK_SANDBOX"));
        String environment = firstPresent(
                provider.getOrNull("IBKR_ENVIRONMENT"),
                provider.getOrNull("IBK_ENVIRONMENT"));

        return new ExchangeCredentials(
                "interactive_brokers",
                firstPresent(provider.getOrNull("IBKR_API_KEY"), provider.getOrNull("IBK_API_KEY"),
                        provider.getOrNull("IBKR_USERNAME"), provider.getOrNull("IBK_USERNAME")),
                firstPresent(provider.getOrNull("IBKR_API_SECRET"), provider.getOrNull("IBK_API_SECRET"),
                        provider.getOrNull("IBKR_PASSWORD"), provider.getOrNull("IBK_PASSWORD")),
                firstPresent(provider.getOrNull("IBKR_USERNAME"), provider.getOrNull("IBK_USERNAME")),
                firstPresent(provider.getOrNull("IBKR_PASSWORD"), provider.getOrNull("IBK_PASSWORD")),
                firstPresent(provider.getOrNull("IBKR_ACCESS_TOKEN"), provider.getOrNull("IBK_ACCESS_TOKEN"),
                        provider.getOrNull("IBKR_TWO_FACTOR_CODE"), provider.getOrNull("IBK_TWO_FACTOR_CODE")),
                firstPresent(provider.getOrNull("IBKR_ACCOUNT_ID"), provider.getOrNull("IBK_ACCOUNT_ID")),
                isSandbox(sandbox, environment),
                params);
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
                false);
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
                false);
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
                false);
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
                false);
    }

    private ExchangeCredentials resolveKraken() {
        return new ExchangeCredentials(
                "kraken",
                provider.getOrNull("KRAKEN_API_KEY"),
                provider.getOrNull("KRAKEN_API_SECRET"),
                null,
                null,
                provider.getOrNull("KRAKEN_API_PASSPHRASE"),
                provider.getOrNull("KRAKEN_ACCOUNT_ID"),
                Boolean.parseBoolean(provider.getOrNull("KRAKEN_SANDBOX")));
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
                Boolean.parseBoolean(provider.getOrNull("OANDA_SANDBOX")));
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
                Boolean.parseBoolean(provider.getOrNull("ALPACA_PAPER")));
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
                isPaperStellarNetwork(network));
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

    private void putIfPresent(Map<String, String> target, String key, String... values) {
        String value = firstPresent(values);
        if (value != null) {
            target.put(key, value);
            target.put(key.toLowerCase(Locale.ROOT), value);
        }
    }

    private boolean isSandbox(String sandbox, String environment) {
        if (sandbox != null && !sandbox.isBlank()) {
            return Boolean.parseBoolean(sandbox.trim());
        }
        if (environment == null || environment.isBlank()) {
            return false;
        }
        String normalized = environment.trim();
        return "paper".equalsIgnoreCase(normalized)
                || "sandbox".equalsIgnoreCase(normalized)
                || "demo".equalsIgnoreCase(normalized)
                || "practice".equalsIgnoreCase(normalized);
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
        addAliases(aliases, "kraken", "kraken", "krakenpro");
        addAliases(aliases, "coinbase", "coinbase", "coinbasepro", "coinbaseadvanced", "coinbaseadvancedtrade",
                "coinbaseat", "coinbasebrokerage");
        addAliases(aliases, "interactive_brokers", "interactivebrokers", "interactivebroker", "ib",
                "ibk", "ibkr");
        addAliases(aliases, "schwab", "schwab", "charlesschwab");
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
