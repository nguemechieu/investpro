package org.investpro.spi;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

@Slf4j
public final class PluginRegistry {

    private static volatile PluginRegistry defaultRegistry;

    private final List<ExchangeProvider> exchangeProviders;
    private final List<StrategyProvider> strategyProviders;
    private final List<IndicatorProvider> indicatorProviders;
    private final List<RiskModuleProvider> riskModuleProviders;
    private final List<MarketDataProvider> marketDataProviders;

    private final Map<String, ExchangeProvider> exchangesById;
    private final Map<String, StrategyProvider> strategiesById;
    private final Map<String, IndicatorProvider> indicatorsById;
    private final Map<String, RiskModuleProvider> riskModulesById;
    private final Map<String, MarketDataProvider> marketDataById;

    private PluginRegistry(
            List<ExchangeProvider> exchangeProviders,
            List<StrategyProvider> strategyProviders,
            List<IndicatorProvider> indicatorProviders,
            List<RiskModuleProvider> riskModuleProviders,
            List<MarketDataProvider> marketDataProviders) {
        this.exchangeProviders = List.copyOf(exchangeProviders);
        this.strategyProviders = List.copyOf(strategyProviders);
        this.indicatorProviders = List.copyOf(indicatorProviders);
        this.riskModuleProviders = List.copyOf(riskModuleProviders);
        this.marketDataProviders = List.copyOf(marketDataProviders);

        this.exchangesById = indexExchangeProviders(this.exchangeProviders);
        this.strategiesById = indexProviders("StrategyProvider", this.strategyProviders);
        this.indicatorsById = indexIndicatorProviders(this.indicatorProviders);
        this.riskModulesById = indexProviders("RiskModuleProvider", this.riskModuleProviders);
        this.marketDataById = indexProviders("MarketDataProvider", this.marketDataProviders);
    }

    public static PluginRegistry loadDefault() {
        PluginRegistry local = defaultRegistry;
        if (local == null) {
            synchronized (PluginRegistry.class) {
                local = defaultRegistry;
                if (local == null) {
                    local = new PluginRegistry(
                            loadProviders(ExchangeProvider.class),
                            loadProviders(StrategyProvider.class),
                            loadProviders(IndicatorProvider.class),
                            loadProviders(RiskModuleProvider.class),
                            loadProviders(MarketDataProvider.class));
                    defaultRegistry = local;
                }
            }
        }
        return local;
    }

    public static PluginRegistry of(
            List<ExchangeProvider> exchangeProviders,
            List<StrategyProvider> strategyProviders,
            List<IndicatorProvider> indicatorProviders,
            List<RiskModuleProvider> riskModuleProviders,
            List<MarketDataProvider> marketDataProviders) {
        return new PluginRegistry(
                nullToEmpty(exchangeProviders),
                nullToEmpty(strategyProviders),
                nullToEmpty(indicatorProviders),
                nullToEmpty(riskModuleProviders),
                nullToEmpty(marketDataProviders));
    }

    public List<ExchangeProvider> exchangeProviders() {
        return exchangeProviders;
    }

    public Optional<ExchangeProvider> findExchangeProvider(String exchangeId) {
        return Optional.ofNullable(exchangesById.get(normalize(exchangeId)));
    }

    public List<StrategyProvider> strategyProviders() {
        return strategyProviders;
    }

    public Optional<StrategyProvider> findStrategyProvider(String strategyId) {
        return Optional.ofNullable(strategiesById.get(normalize(strategyId)));
    }

    public List<IndicatorProvider> indicatorProviders() {
        return indicatorProviders;
    }

    public Optional<IndicatorProvider> findIndicatorProvider(String indicatorId) {
        return Optional.ofNullable(indicatorsById.get(normalize(indicatorId)));
    }

    public List<RiskModuleProvider> riskModuleProviders() {
        return riskModuleProviders;
    }

    public Optional<RiskModuleProvider> findRiskModuleProvider(String riskModuleId) {
        return Optional.ofNullable(riskModulesById.get(normalize(riskModuleId)));
    }

    public List<MarketDataProvider> marketDataProviders() {
        return marketDataProviders;
    }

    public Optional<MarketDataProvider> findMarketDataProvider(String providerId) {
        return Optional.ofNullable(marketDataById.get(normalize(providerId)));
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.trim()
                .replace(' ', '_')
                .replace('-', '_')
                .toUpperCase(Locale.ROOT);
    }

    private static <T extends InvestProPlugin> List<T> loadProviders(Class<T> providerType) {
        List<T> providers = new ArrayList<>();
        ServiceLoader<T> loader = ServiceLoader.load(providerType);

        try {
            for (T provider : loader) {
                if (isValidProvider(providerType.getSimpleName(), provider)) {
                    providers.add(provider);
                    logLoaded(providerType.getSimpleName(), provider);
                }
            }
        } catch (ServiceConfigurationError error) {
            log.warn("Failed to load {} via ServiceLoader: {}", providerType.getSimpleName(), error.getMessage(), error);
        } catch (RuntimeException exception) {
            log.warn("Unexpected failure loading {} providers", providerType.getSimpleName(), exception);
        }

        return providers;
    }

    private static Map<String, ExchangeProvider> indexExchangeProviders(List<ExchangeProvider> providers) {
        Map<String, ExchangeProvider> indexed = new LinkedHashMap<>();
        for (ExchangeProvider provider : providers) {
            if (!registerProvider("ExchangeProvider", indexed, provider.id(), provider)) {
                continue;
            }
            for (String alias : nullToEmpty(provider.aliases())) {
                registerProvider("ExchangeProvider alias", indexed, alias, provider);
            }
        }
        return Collections.unmodifiableMap(indexed);
    }

    private static Map<String, IndicatorProvider> indexIndicatorProviders(List<IndicatorProvider> providers) {
        Map<String, IndicatorProvider> indexed = new LinkedHashMap<>();
        for (IndicatorProvider provider : providers) {
            if (!registerProvider("IndicatorProvider", indexed, provider.id(), provider)) {
                continue;
            }
            registerProvider("IndicatorProvider name", indexed, provider.indicatorName(), provider);
        }
        return Collections.unmodifiableMap(indexed);
    }

    private static <T extends InvestProPlugin> Map<String, T> indexProviders(String type, List<T> providers) {
        Map<String, T> indexed = new LinkedHashMap<>();
        for (T provider : providers) {
            registerProvider(type, indexed, provider.id(), provider);
        }
        return Collections.unmodifiableMap(indexed);
    }

    private static <T extends InvestProPlugin> boolean registerProvider(
            String type,
            Map<String, T> indexed,
            String id,
            T provider) {
        String normalized = normalize(id);
        if (normalized.isBlank()) {
            log.warn("Ignoring {} with blank id: {}", type, provider.getClass().getName());
            return false;
        }

        T existing = indexed.putIfAbsent(normalized, provider);
        if (existing != null && existing != provider) {
            log.warn(
                    "Duplicate {} id '{}' from {} ignored; keeping {}",
                    type,
                    normalized,
                    provider.getClass().getName(),
                    existing.getClass().getName());
            return false;
        }
        return true;
    }

    private static boolean isValidProvider(String type, InvestProPlugin provider) {
        if (provider == null) {
            log.warn("Ignoring null {}", type);
            return false;
        }
        if (normalize(provider.id()).isBlank()) {
            log.warn("Ignoring {} with blank id: {}", type, provider.getClass().getName());
            return false;
        }
        return true;
    }

    private static void logLoaded(String type, InvestProPlugin provider) {
        Objects.requireNonNull(provider, "provider must not be null");
        if (provider instanceof ExchangeProvider exchangeProvider) {
            log.info(
                    "Loaded {}: {} v{} aliases={}",
                    type,
                    normalize(provider.id()),
                    provider.version(),
                    exchangeProvider.aliases());
            return;
        }

        log.info("Loaded {}: {} v{}", type, normalize(provider.id()), provider.version());
    }

    private static <T> List<T> nullToEmpty(List<T> values) {
        return values == null ? List.of() : values;
    }

    private static <T> Iterable<T> nullToEmpty(Iterable<T> values) {
        return values == null ? List.of() : values;
    }
}
