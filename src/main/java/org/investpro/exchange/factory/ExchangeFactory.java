package org.investpro.exchange.factory;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.*;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.core.BrokerVenue;
import org.investpro.exchange.core.InstrumentType;
import org.investpro.exchange.core.VenueAwareExchange;
import org.investpro.exchange.credentials.ExchangeCredentialResolver;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.BrokerExchangeAdapter;
import org.investpro.exchange.infrastructure.BrokerRouter;
import org.investpro.exchange.infrastructure.ENUM_EXCHANGE_LIST;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class ExchangeFactory {

    private static final Map<String, String> EXCHANGE_ALIASES = buildExchangeAliases();


    private final ExchangeCredentialResolver credentialResolver;
    private final BrokerRouter brokerRouter;

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
        this.brokerRouter = new BrokerRouter();
    }

    /**
     * Preferred method.
     * Returns the existing adapter if already created.
     */

    public Exchange getOrCreate(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        ENUM_EXCHANGE_LIST exchangeEnum = toEnum(exchangeId);
        return exchangeCache.computeIfAbsent(exchangeEnum.name(), k -> createNew(exchangeEnum));
    }

    public Exchange getOrCreate(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        Objects.requireNonNull(exchangeEnum, "exchangeEnum must not be null");
        return exchangeCache.computeIfAbsent(exchangeEnum.name(), k -> createNew(exchangeEnum));
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

    public Exchange create(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        return getOrCreate(exchangeEnum);
    }

    /**
     * Venue-aware broker path for newer integrations that need capability-based
     * routing instead of the legacy Exchange surface.
     */
    public VenueAwareExchange getOrCreateVenueAware(
            @NotNull String exchangeId,
            @NotNull BrokerVenue venue) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        Objects.requireNonNull(venue, "venue must not be null");

        ENUM_EXCHANGE_LIST exchangeEnum = toEnum(exchangeId);
        ExchangeCredentials credentials = credentialResolver.resolve(exchangeEnum.name().toLowerCase(Locale.ROOT));
        return brokerRouter.getExchange(
                exchangeEnum.name(),
                venue,
                preferredApiKey(credentials),
                preferredApiSecret(credentials));
    }

    public VenueAwareExchange getOrCreateVenueAware(
            @NotNull String exchangeId,
            @NotNull InstrumentType instrumentType) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        Objects.requireNonNull(instrumentType, "instrumentType must not be null");

        ENUM_EXCHANGE_LIST exchangeEnum = toEnum(exchangeId);
        BrokerVenue venue = brokerRouter.getRecommendedVenue(exchangeEnum.name(), instrumentType);
        if (venue == BrokerVenue.UNKNOWN) {
            throw new IllegalArgumentException("No venue-aware route for " + exchangeId + " / " + instrumentType);
        }
        return getOrCreateVenueAware(exchangeEnum.name(), venue);
    }

    /**
     * Force-create a fresh adapter.
     * Use this only when credentials changed or the user explicitly resets the exchange.
     */

    public Exchange recreate(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        ENUM_EXCHANGE_LIST exchangeEnum = toEnum(exchangeId);
        return recreate(exchangeEnum);
    }

    public Exchange recreate(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        Objects.requireNonNull(exchangeEnum, "exchangeEnum must not be null");
        Exchange oldExchange = exchangeCache.remove(exchangeEnum.name());
        if (oldExchange != null) {
            try {
                log.info("Disconnecting old exchange adapter before recreation: {}", exchangeEnum);
                if (oldExchange instanceof BrokerExchangeAdapter brokerExchangeAdapter) {
                    brokerExchangeAdapter.stopAllStreams();
                }
                oldExchange.disconnect();
            } catch (Exception exception) {
                log.warn("Failed to disconnect old exchange adapter: {}", exchangeEnum, exception);
            }
        }
        Exchange newExchange = createNew(exchangeEnum);
        exchangeCache.put(exchangeEnum.name(), newExchange);
        return newExchange;
    }

    /**
     * Remove and disconnect one adapter.
     */

    public void remove(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        ENUM_EXCHANGE_LIST exchangeEnum = toEnum(exchangeId);
        remove(exchangeEnum);
    }

    public void remove(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        Objects.requireNonNull(exchangeEnum, "exchangeEnum must not be null");
        Exchange exchange = exchangeCache.remove(exchangeEnum.name());
        if (exchange != null) {
            try {
                log.info("Disconnecting and removing exchange adapter: {}", exchangeEnum);
                if (exchange instanceof BrokerExchangeAdapter brokerExchangeAdapter) {
                    brokerExchangeAdapter.stopAllStreams();
                }
                exchange.disconnect();
            } catch (Exception exception) {
                log.warn("Failed to disconnect exchange adapter: {}", exchangeEnum, exception);
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
                if (exchange instanceof BrokerExchangeAdapter brokerExchangeAdapter) {
                    brokerExchangeAdapter.stopAllStreams();
                }
                exchange.disconnect();
            } catch (Exception exception) {
                log.warn("Failed to shut down exchange adapter: {}", exchangeId, exception);
            }
        }

        exchangeCache.clear();
        brokerRouter.closeAll();
    }


    public boolean hasCached(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        ENUM_EXCHANGE_LIST exchangeEnum = toEnum(exchangeId);
        return hasCached(exchangeEnum);
    }

    public boolean hasCached(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        Objects.requireNonNull(exchangeEnum, "exchangeEnum must not be null");
        return exchangeCache.containsKey(exchangeEnum.name());
    }

    public int cachedCount() {
        return exchangeCache.size();
    }


    private Exchange createNew(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        ExchangeCredentials credentials = credentialResolver.resolve(exchangeEnum.name().toLowerCase(Locale.ROOT));
        log.info("Creating exchange adapter: {}", exchangeEnum);
        return switch (exchangeEnum) {
            case ALPACA -> new Alpaca(credentials);
            case BINANCE -> new Binance(credentials);
            case BINANCE_US -> new BinanceUs(credentials);
            case BITFINEX, BITFINEX_US -> new Bitfinex(credentials);
            case COINBASE -> new Coinbase(credentials);
            case INTERACTIVE_BROKERS -> new InteractiveBrokers(credentials);
            case OANDA -> new Oanda(credentials);
            case STELLAR_NETWORK -> new StellarNetwork(credentials);
            case POLONIEX -> throw new IllegalArgumentException("Poloniex adapter not implemented yet");
            case IG -> throw new IllegalArgumentException("IG adapter not implemented yet");
            case KRAKEN -> throw new IllegalArgumentException("Kraken adapter not implemented yet");
            case BITTREX -> throw new IllegalArgumentException("Bittrex adapter not implemented yet");
            case BITMEX -> throw new IllegalArgumentException("Bitmex adapter not implemented yet");
            case KUCOIN, KUCOIN_US -> throw new IllegalArgumentException("Kucoin adapter not implemented yet");
            case BITSTAMP -> throw new IllegalArgumentException("Bitstamp adapter not implemented yet");
            default -> throw new IllegalArgumentException("Unsupported exchange: " + exchangeEnum);
        };
    }


    private ENUM_EXCHANGE_LIST toEnum(String exchangeId) {
        String compact = exchangeId
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
        String canonical = EXCHANGE_ALIASES.getOrDefault(compact, compact);
        for (ENUM_EXCHANGE_LIST e : ENUM_EXCHANGE_LIST.values()) {
            if (e.name().replace("_", "").equalsIgnoreCase(canonical.replace("_", ""))) {
                return e;
            }
        }
        throw new IllegalArgumentException("Unknown exchange: " + exchangeId);
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

        addAliases(aliases, "bittrex", "bittrex");
        addAliases(aliases, "bitmex", "bitmex");
        addAliases(aliases, "bitstamp", "bitstamp");
        addAliases(aliases, "ig", "ig", "igmarkets");
        addAliases(aliases, "kraken", "kraken");
        addAliases(aliases, "kucoin", "kucoin", "kucoinus");
        addAliases(aliases, "poloniex", "poloniex");

        return Collections.unmodifiableMap(aliases);
    }

    private static void addAliases(Map<String, String> aliases, String canonical, String... values) {
        for (String value : values) {
            aliases.put(value, canonical);
        }
    }

    private static String preferredApiKey(ExchangeCredentials credentials) {
        if (credentials == null) {
            return "";
        }
        if (credentials.keyName() != null && !credentials.keyName().isBlank()) {
            return credentials.keyName();
        }
        return credentials.apiKey() == null ? "" : credentials.apiKey();
    }

    private static String preferredApiSecret(ExchangeCredentials credentials) {
        if (credentials == null) {
            return "";
        }
        if (credentials.privateKey() != null && !credentials.privateKey().isBlank()) {
            return credentials.privateKey();
        }
        return credentials.apiSecret() == null ? "" : credentials.apiSecret();
    }
}
