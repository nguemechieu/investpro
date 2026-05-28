package org.investpro.models.currency;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.currency.spi.CurrencyProvider;
import org.jetbrains.annotations.UnmodifiableView;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cached currency metadata registry.
 *
 * ServiceLoader is used only once to discover grouped providers.
 */
@Slf4j
public final class CurrencyRegistry {

    private static final String DEFAULT_ICON_PATH = "/icons/currencies/default.svg";

    private static volatile CurrencyRegistry GLOBAL;

    private final Map<String, Currency> currenciesByCode = new LinkedHashMap<>();
    private final Map<String, Currency> currenciesBySymbol = new LinkedHashMap<>();
    private final Map<String, String> iconPathByCode = new LinkedHashMap<>();
    private final Set<String> unknownLoggedCodes = ConcurrentHashMap.newKeySet();

    private final List<CurrencyProvider> loadedProviders = new ArrayList<>();

    private CurrencyRegistry() {
    }

    public static CurrencyRegistry loadDefault() {
        CurrencyRegistry registry = new CurrencyRegistry();

        ServiceLoader<CurrencyProvider> loader = ServiceLoader.load(CurrencyProvider.class);
        for (CurrencyProvider provider : loader) {
            registry.registerProvider(provider);
        }

        return registry;
    }

    public static CurrencyRegistry global() {
        CurrencyRegistry current = GLOBAL;
        if (current == null) {
            synchronized (CurrencyRegistry.class) {
                current = GLOBAL;
                if (current == null) {
                    current = loadDefault();
                    GLOBAL = current;
                }
            }
        }
        return current;
    }

    public synchronized void registerProvider(CurrencyProvider provider) {
        if (provider == null) {
            return;
        }

        loadedProviders.add(provider);

        Collection<Currency> currencies;
        try {
            currencies = provider.getCurrencies();
        } catch (Exception exception) {
            log.warn("Currency provider {} ({}) failed to load: {}",
                    provider.providerId(),
                    provider.displayName(),
                    exception.getMessage(),
                    exception);
            return;
        }

        if (currencies == null || currencies.isEmpty()) {
            return;
        }

        for (Currency currency : currencies) {
            registerCurrency(provider, currency);
        }
    }

    public Optional<Currency> findByCode(String code) {
        return Optional.ofNullable(currenciesByCode.get(normalizeCode(code)));
    }

    public Optional<Currency> findBySymbol(String symbol) {
        return Optional.ofNullable(currenciesBySymbol.get(normalizeSymbol(symbol)));
    }

    public Currency findOrUnknown(String code) {
        String normalized = normalizeCode(code);
        Currency existing = currenciesByCode.get(normalized);
        if (existing != null) {
            return existing;
        }

        if (unknownLoggedCodes.add(normalized)) {
            log.info("Unknown currency encountered: {}", normalized);
        }

        UnknownCurrency unknown = UnknownCurrency.of(normalized);
        currenciesByCode.put(normalized, unknown);
        registerSymbol(unknown);
        iconPathByCode.put(normalized, DEFAULT_ICON_PATH);
        return unknown;
    }

    public @NonNull @UnmodifiableView Collection<Currency> getAllCurrencies() {
        return Collections.unmodifiableCollection(currenciesByCode.values());
    }

    public List<Currency> getByType(CurrencyType type) {
        if (type == null) {
            return List.of();
        }

        List<Currency> result = new ArrayList<>();
        for (Currency currency : currenciesByCode.values()) {
            if (currency != null && type == currency.getCurrencyType()) {
                result.add(currency);
            }
        }
        return result;
    }

    public boolean contains(String code) {
        return currenciesByCode.containsKey(normalizeCode(code));
    }

    public int size() {
        return currenciesByCode.size();
    }

    public Optional<String> findIconPath(String code) {
        String normalized = normalizeCode(code);
        String path = iconPathByCode.get(normalized);
        if (path == null) {
            Currency currency = currenciesByCode.get(normalized);
            if (currency == null) {
                return Optional.empty();
            }
            path = resolveDefaultIconPath(currency);
            iconPathByCode.put(normalized, path);
        }
        return Optional.of(path);
    }

    public String iconPathOrDefault(String code) {
        return findIconPath(code).orElse(DEFAULT_ICON_PATH);
    }

    public List<CurrencyProvider> getLoadedProviders() {
        return List.copyOf(loadedProviders);
    }

    private void registerCurrency(CurrencyProvider provider, Currency currency) {
        if (currency == null) {
            return;
        }

        String code = normalizeCode(currency.getCode());
        if (code.isBlank()) {
            return;
        }

        Currency existing = currenciesByCode.putIfAbsent(code, currency);
        if (existing != null) {
            log.warn("Duplicate currency code {} from provider {} (keeping existing from earlier provider)",
                    code,
                    provider.providerId());
            return;
        }

        registerSymbol(currency);
        iconPathByCode.put(code, resolveDefaultIconPath(currency));
    }

    private void registerSymbol(Currency currency) {
        String symbol = normalizeSymbol(currency.getSymbol());
        if (!symbol.isBlank() && !currenciesBySymbol.containsKey(symbol)) {
            currenciesBySymbol.put(symbol, currency);
        }
    }

    private String resolveDefaultIconPath(Currency currency) {
        String code = normalizeCode(currency.getCode()).toLowerCase(Locale.ROOT);

        String preferredBase = switch (currency.getCurrencyType()) {
            case CRYPTO -> "/icons/crypto/";
            case METAL -> "/icons/metals/";
            case INDEX -> "/icons/indices/";
            case FIAT, FOREX -> "/icons/fiat/";
            default -> "/icons/currencies/";
        };

        String preferredPath = preferredBase + code + ".svg";
        if (resourceExists(preferredPath)) {
            return preferredPath;
        }

        String pngFallback = preferredBase + code + ".png";
        if (resourceExists(pngFallback)) {
            return pngFallback;
        }

        if (resourceExists(DEFAULT_ICON_PATH)) {
            return DEFAULT_ICON_PATH;
        }

        return DEFAULT_ICON_PATH;
    }

    private boolean resourceExists(String classpathPath) {
        String normalized = classpathPath.startsWith("/") ? classpathPath.substring(1) : classpathPath;
        return CurrencyRegistry.class.getClassLoader().getResource(normalized) != null;
    }

    private String normalizeCode(String code) {
        if (code == null) {
            return "";
        }
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(String symbol) {
        if (symbol == null) {
            return "";
        }
        return symbol.trim().toUpperCase(Locale.ROOT);
    }
}
