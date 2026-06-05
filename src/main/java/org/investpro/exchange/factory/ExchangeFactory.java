package org.investpro.exchange.factory;

import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.*;
import org.investpro.exchange.ibkr.IbkrExchange;
import org.investpro.exchange.contracts.CredentialProvider;
import org.investpro.exchange.core.BrokerVenue;
import org.investpro.exchange.core.InstrumentType;
import org.investpro.exchange.core.VenueAwareExchange;
import org.investpro.exchange.credentials.ExchangeCredentialResolver;
import org.investpro.exchange.credentials.ExchangeCredentials;
import org.investpro.exchange.infrastructure.BrokerExchangeAdapter;
import org.investpro.exchange.infrastructure.BrokerRouter;
import org.investpro.exchange.infrastructure.ENUM_EXCHANGE_LIST;
import org.investpro.exchange.schwab.Schwab;
import org.investpro.operations.SystemActivityBus;
import org.investpro.spi.ExchangeProvider;
import org.investpro.spi.ExchangeProviderContext;
import org.investpro.spi.PluginRegistry;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class ExchangeFactory {

    private static final Map<String, String> EXCHANGE_ALIASES = buildExchangeAliases();

    private final ExchangeCredentialResolver credentialResolver;
    private final BrokerRouter brokerRouter;
    private final PluginRegistry pluginRegistry;
    private final ExchangeProviderContext providerContext;

    /**
     * Cache one exchange adapter per normalized exchange id.
     * <p>
     * This prevents creating multiple Coinbase/OANDA/Binance objects
     * every time the UI refreshes, reconnects, or asks for an adapter.
     */
    private final Map<String, Exchange> exchangeCache = new ConcurrentHashMap<>();

    public ExchangeFactory(@NotNull CredentialProvider credentialProvider) {
        this(credentialProvider, PluginRegistry.loadDefault());
    }

    public ExchangeFactory(@NotNull CredentialProvider credentialProvider, @NotNull PluginRegistry pluginRegistry) {
        Objects.requireNonNull(credentialProvider, "credentialProvider must not be null");
        Objects.requireNonNull(pluginRegistry, "pluginRegistry must not be null");
        this.credentialResolver = new ExchangeCredentialResolver(credentialProvider);
        this.brokerRouter = new BrokerRouter();
        this.pluginRegistry = pluginRegistry;
        this.providerContext = new ExchangeProviderContext(
                credentialProvider,
                credentialResolver,
                Map.of(),
                SystemActivityBus.getInstance());
    }

    /**
     * Preferred method.
     * Returns the existing adapter if already created.
     */

    public Exchange getOrCreate(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        String cacheKey = cacheKeyFor(exchangeId);
        return exchangeCache.computeIfAbsent(cacheKey, k -> createUncached(exchangeId));
    }

    public Exchange getOrCreate(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        Objects.requireNonNull(exchangeEnum, "exchangeEnum must not be null");
        return getOrCreate(exchangeEnum.name());
    }

    /**
     * Backward-compatible method.
     * <p>
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
     * Use this only when credentials changed or the user explicitly resets the
     * exchange.
     */

    public Exchange recreate(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        String cacheKey = cacheKeyFor(exchangeId);
        Exchange oldExchange = exchangeCache.remove(cacheKey);
        disconnectQuietly(cacheKey, oldExchange);
        Exchange newExchange = createUncached(exchangeId);
        exchangeCache.put(cacheKey, newExchange);
        return newExchange;
    }

    public Exchange recreate(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        Objects.requireNonNull(exchangeEnum, "exchangeEnum must not be null");
        return recreate(exchangeEnum.name());
    }

    /**
     * Remove and disconnect one adapter.
     */

    public void remove(@NotNull String exchangeId) {
        Objects.requireNonNull(exchangeId, "exchangeId must not be null");
        String cacheKey = cacheKeyFor(exchangeId);
        Exchange exchange = exchangeCache.remove(cacheKey);
        disconnectQuietly(cacheKey, exchange);
    }

    public void remove(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        Objects.requireNonNull(exchangeEnum, "exchangeEnum must not be null");
        remove(exchangeEnum.name());
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
        return exchangeCache.containsKey(cacheKeyFor(exchangeId));
    }

    public boolean hasCached(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        Objects.requireNonNull(exchangeEnum, "exchangeEnum must not be null");
        return hasCached(exchangeEnum.name());
    }

    public int cachedCount() {
        return exchangeCache.size();
    }

    private Exchange createUncached(@NotNull String exchangeId) {
        Optional<ExchangeProvider> provider = pluginRegistry.findExchangeProvider(exchangeId);
        if (provider.isPresent()) {
            ExchangeProvider exchangeProvider = provider.get();
            log.info("Creating exchange adapter from plugin provider: {}", exchangeProvider.id());
            return exchangeProvider.create(providerContext);
        }

        ENUM_EXCHANGE_LIST exchangeEnum = toEnum(exchangeId);
        log.warn("Using legacy hardcoded ExchangeFactory fallback for {}", exchangeEnum);
        return createLegacyExchange(exchangeEnum);
    }

    private Exchange createLegacyExchange(@NotNull ENUM_EXCHANGE_LIST exchangeEnum) {
        ExchangeCredentials credentials = credentialResolver.resolve(exchangeEnum.name().toLowerCase(Locale.ROOT));
        log.info("Creating exchange adapter: {}", exchangeEnum);
        return switch (exchangeEnum) {
            case ALPACA -> new Alpaca(credentials);
            case BINANCE -> new Binance(credentials);
            case BINANCE_US -> new BinanceUs(credentials);
            case BITFINEX, BITFINEX_US -> new Bitfinex(credentials);
            case COINBASE -> new Coinbase(credentials);
            case INTERACTIVE_BROKERS -> new IbkrExchange(credentials);
            case SCHWAB -> new Schwab(credentials);
            case KRAKEN -> new Kraken(credentials);
            case OANDA -> new Oanda(credentials);
            case STELLAR_NETWORK -> new StellarNetwork(credentials);
            case SOLONA_NETWORK -> new SolonaNetwork(credentials);
            case POLONIEX -> throw new IllegalArgumentException("Poloniex adapter not implemented yet");
            case IG -> throw new IllegalArgumentException("IG adapter not implemented yet");
            case BITTREX -> throw new IllegalArgumentException("Bittrex adapter not implemented yet");
            case BITMEX -> throw new IllegalArgumentException("Bitmex adapter not implemented yet");
            case KUCOIN, KUCOIN_US -> throw new IllegalArgumentException("Kucoin adapter not implemented yet");
            case BITSTAMP -> throw new IllegalArgumentException("Bitstamp adapter not implemented yet");
        };
    }

    private String cacheKeyFor(String exchangeId) {
        Optional<ExchangeProvider> provider = pluginRegistry.findExchangeProvider(exchangeId);
        return provider.map(exchangeProvider -> PluginRegistry.normalize(exchangeProvider.id())).orElseGet(() -> toEnum(exchangeId).name());
    }

    private void disconnectQuietly(String exchangeId, Exchange exchange) {
        if (exchange == null) {
            return;
        }
        try {
            log.info("Disconnecting exchange adapter: {}", exchangeId);
            if (exchange instanceof BrokerExchangeAdapter brokerExchangeAdapter) {
                brokerExchangeAdapter.stopAllStreams();
            }
            exchange.disconnect();
        } catch (Exception exception) {
            log.warn("Failed to disconnect exchange adapter: {}", exchangeId, exception);
        }
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
                "ibk", "ibkr");
        addAliases(aliases, "schwab", "schwab", "charlesschwab");
        addAliases(aliases, "oanda", "oanda", "oandafx", "oandaforex", "oandacfd", "oandafxcfd");
        addAliases(aliases, "stellar_network", "stellar", "stellarnetwork", "stellarx", "xlm");
        addAliases(aliases, "solona_network", "solona", "solonanetwork", "sol", "solonaweb3");

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
