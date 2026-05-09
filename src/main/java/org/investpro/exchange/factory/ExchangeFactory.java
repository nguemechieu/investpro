package org.investpro.exchange.factory;

import lombok.extern.slf4j.Slf4j;

import org.investpro.exchange.*;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.credentials.ExchangeCredentialResolver;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.providers.EnvironmentCredentialProvider;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Objects;

@Slf4j
public final class ExchangeFactory {

    private final ExchangeCredentialResolver credentialResolver;

    public ExchangeFactory(@NotNull CredentialProvider credentialProvider) {
        Objects.requireNonNull(credentialProvider, "credentialProvider must not be null");
        this.credentialResolver = new ExchangeCredentialResolver(credentialProvider);
    }

    public static ExchangeFactory fromEnvironment() {
        return new ExchangeFactory(new EnvironmentCredentialProvider());
    }

    public Exchange create(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");

        String normalized = normalize(exchangeId);
        ExchangeCredentials credentials = credentialResolver.resolve(normalized);

        log.info("Creating exchange adapter: {}", normalized);

        return switch (normalized) {
            case "binanceus", "binance_us", "binance-us" -> new BinanceUs(credentials);

            case "coinbase", "coinbaseadvanced", "coinbase_advanced", "coinbase-advanced" ->
                    new Coinbase(credentials);

            case "oanda" -> new Oanda(credentials);

            case "alpaca" -> new Alpaca(credentials);
            case  "binance" -> new Binance(credentials);
            case "stellar-network"->new StellarNetwork(credentials);

            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchangeId);
        };
    }

    private String normalize(String exchangeId) {
        return exchangeId
                .trim()
                .toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("-", "_");
    }
}