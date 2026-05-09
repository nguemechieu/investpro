package org.investpro.exchange.credentials;


import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.contracts.CredentialProvider;


@Slf4j
public record ExchangeCredentialResolver(CredentialProvider provider) {

    public ExchangeCredentials resolve(String exchangeId) {
        String id = exchangeId == null ? "" : exchangeId.trim().toLowerCase();

        return switch (id) {
            case "coinbase", "coinbaseadvanced", "coinbase_advanced" -> resolveCoinbase();
            case "binanceus", "binance_us", "binance" -> resolveBinanceUs();
            case "oanda" -> resolveOanda();
            case "alpaca" -> resolveAlpaca();
            case "ibk","interactive broker"->resolveIbk();
            default -> new ExchangeCredentials(id, null, null, null, null, null, null, false);
        };
    }

    private ExchangeCredentials resolveIbk() {
        return new ExchangeCredentials(
                "ibk",
                provider.getOrNull("IBK_API_KEY"),
                provider.getOrNull("IBK_API_SECRET"),
                null,
                null,
                null,
                provider.getOrNull("IBK_ACCOUNT_ID"),
                Boolean.parseBoolean(provider.getOrNull("IBK_SANDBOX"))
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

    private ExchangeCredentials resolveBinanceUs() {
        return new ExchangeCredentials(
                "binanceus",
                provider.getOrNull("BINANCE_US_API_KEY"),
                provider.getOrNull("BINANCE_US_API_SECRET"),
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
}