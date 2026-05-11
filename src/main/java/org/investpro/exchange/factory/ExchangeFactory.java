package org.investpro.exchange.factory;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.*;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.credentials.ExchangeCredentialResolver;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class ExchangeFactory {

    private final ExchangeCredentialResolver credentialResolver;

    /**
     * Cache one exchange adapter per normalized exchange id.
     *
     * This prevents creating multiple Coinbase/OANDA/Binance objects
     * every time the UI refreshes, reconnects, or asks for an adapter.
     */
    private final Map<String, Exchange> exchangeCache = new ConcurrentHashMap<>();

    public ExchangeFactory(@NotNull CredentialProvider credentialProvider) {
        Objects.requireNonNull(credentialProvider, "credentialProvider must not be null");
        this.credentialResolver = new ExchangeCredentialResolver(credentialProvider);
    }

    /**
     * Preferred method.
     * Returns the existing adapter if already created.
     */
    public Exchange getOrCreate(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");

        String normalized = normalize(exchangeId);

        return exchangeCache.computeIfAbsent(normalized, this::createNew);
    }

    /**
     * Backward-compatible method.
     *
     * IMPORTANT:
     * This no longer blindly creates a new adapter.
     * It now returns the cached adapter.
     */
    public Exchange create(@NotNull String exchangeId) {
        return getOrCreate(exchangeId);
    }

    /**
     * Force-create a fresh adapter.
     * Use this only when credentials changed or the user explicitly resets the exchange.
     */
    public Exchange recreate(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");

        String normalized = normalize(exchangeId);

        Exchange oldExchange = exchangeCache.remove(normalized);

        if (oldExchange != null) {
            try {
                log.info("Disconnecting old exchange adapter before recreation: {}", normalized);
                oldExchange.disconnect();
            } catch (Exception exception) {
                log.warn("Failed to disconnect old exchange adapter: {}", normalized, exception);
            }
        }

        Exchange newExchange = createNew(normalized);
        exchangeCache.put(normalized, newExchange);

        return newExchange;
    }

    /**
     * Remove and disconnect one adapter.
     */
    public void remove(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");

        String normalized = normalize(exchangeId);
        Exchange exchange = exchangeCache.remove(normalized);

        if (exchange != null) {
            try {
                log.info("Disconnecting and removing exchange adapter: {}", normalized);
                exchange.disconnect();
            } catch (Exception exception) {
                log.warn("Failed to disconnect exchange adapter: {}", normalized, exception);
            }
        }
    }

    /**
     * Disconnect and clear all cached adapters.
     * Useful when the app is closing.
     */
    public void shutdown() {
        for (Map.Entry<String, Exchange> entry : exchangeCache.entrySet()) {
            String exchangeId = entry.getKey();
            Exchange exchange = entry.getValue();

            try {
                log.info("Shutting down exchange adapter: {}", exchangeId);
                exchange.disconnect();
            } catch (Exception exception) {
                log.warn("Failed to shut down exchange adapter: {}", exchangeId, exception);
            }
        }

        exchangeCache.clear();
    }

    public boolean hasCached(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        return exchangeCache.containsKey(normalize(exchangeId));
    }

    public int cachedCount() {
        return exchangeCache.size();
    }

    private Exchange createNew(@NotNull String normalized) {
        ExchangeCredentials credentials = credentialResolver.resolve(normalized);

        log.info("Creating exchange adapter: {}", normalized);

        return switch (normalized) {
            case "binanceus", "binance_us", "binance us" -> new BinanceUs(credentials);

            case "coinbase", "coinbaseadvanced", "coinbase_advanced" ->
                    new Coinbase(credentials);

            case "oanda" -> new Oanda(credentials);
            case "alpaca" -> new Alpaca(credentials);
            case "binance" -> new Binance(credentials);
            case "stellar_network" -> new StellarNetwork(credentials);
            case "bitfinex" -> new Bitfinex(credentials);
            case "interactive_brokers" -> new InteractiveBrokers(credentials);

            default -> throw new IllegalArgumentException("Unsupported exchange: " + normalized);
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