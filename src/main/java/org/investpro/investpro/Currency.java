package org.investpro.investpro;


import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


public class Currency implements Comparable<java.util.Currency> {
    public static final CryptoCurrency NULL_CRYPTO_CURRENCY = new NullCryptoCurrency();
    public static final FiatCurrency NULL_FIAT_CURRENCY = new NullFiatCurrency();
    private static final Map<SymmetricPair<String, CurrencyType>, Currency> CURRENCIES = new ConcurrentHashMap<>();

    static {
        //  FIXME: Replace with ServiceLoaders
        CryptoCurrencyDataProvider cryptoCurrencyDataProvider = new CryptoCurrencyDataProvider();
        cryptoCurrencyDataProvider.registerCurrencies();
        FiatCurrencyDataProvider fiatCurrencyDataProvider = new FiatCurrencyDataProvider();
        fiatCurrencyDataProvider.registerCurrencies();

//
//        ServiceLoader<CurrencyDataProvider> serviceLoader = ServiceLoader.load(CurrencyDataProvider.class);
//        Log.info("service loader: " + serviceLoader);
//        for (CurrencyDataProvider provider : serviceLoader) {
//            Log.info("calling provider.registerCurrencies()");
//            try {
//                provider.registerCurrencies();
//            } catch (Exception e) {
//                Log.error(TAG,"could not register currencies: "+ e);
//            }
//        }

    }

    protected String code;
    protected int fractionalDigits;
    protected String symbol;
    private final CurrencyType currencyType;
    String fullDisplayName;
    private final String shortDisplayName;

    /**
     * Private constructor used only for the {@code NULL_CURRENCY}.
     */
    protected Currency() {
        this.currencyType = CurrencyType.NULL;
        this.fullDisplayName = "";
        this.shortDisplayName = "";
        this.code = "";
        this.fractionalDigits = 0;
        this.symbol = "";
    }

    /**
     * Protected constructor, called only by CurrencyDataProvider's.
     */
    protected Currency(CurrencyType currencyType, String fullDisplayName, String shortDisplayName, String code,
                       int fractionalDigits, String symbol) {
        Objects.requireNonNull(currencyType, "currencyType must not be null");
        Objects.requireNonNull(fullDisplayName, "fullDisplayName must not be null");
        Objects.requireNonNull(shortDisplayName, "shortDisplayName must not be null");
        Objects.requireNonNull(code, "code must not be null");

        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractional digits must be non-negative, was: " + fractionalDigits);
        }
        Objects.requireNonNull(symbol, "symbol must not be null");

        this.currencyType = currencyType;
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;
        this.code = code;
        this.fractionalDigits = fractionalDigits;
        this.symbol = symbol;
    }

    protected static void registerCurrency(Currency currency) {
        Objects.requireNonNull(currency, "currency must not be null");

        CURRENCIES.put(SymmetricPair.of(currency.code, currency.currencyType), currency);
    }

    protected static void registerCurrencies(Collection<Currency> currencies) {
        Objects.requireNonNull(currencies, "currencies must not be null");
        currencies.forEach(Currency::registerCurrency);
    }

    public static Currency of(String code) {
        Objects.requireNonNull(code, "code must not be null");
        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.FIAT))) {
            if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO))) {
                Log.error("ambiguous currency code: " + code);
                throw new IllegalArgumentException("ambiguous currency code: " + code + " (code" +
                        " is used for multiple currency types); use ofCrypto(...) or ofFiat(...) instead");
            } else {
                if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO))) {
                    return CURRENCIES.get(SymmetricPair.of(code, CurrencyType.CRYPTO));
                } else {
                    return CURRENCIES.getOrDefault(SymmetricPair.of(code, CurrencyType.FIAT), NULL_CRYPTO_CURRENCY);
                }
            }
        } else {
            if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO))) {
                return CURRENCIES.get(SymmetricPair.of(code, CurrencyType.CRYPTO));
            } else {
                return CURRENCIES.getOrDefault(SymmetricPair.of(code, CurrencyType.FIAT), NULL_CRYPTO_CURRENCY);
            }
        }
    }

    /**
     * Get the fiat currency that has a currency code equal to the
     * given {@code}. Using {@literal "??????"} as the currency code
     * returns {@literal NULL_FIAT_CURRENCY}.
     */
    public static FiatCurrency ofFiat(@NotNull String code) {
        if (code.equals("??????")) {
            return NULL_FIAT_CURRENCY;
        }

        FiatCurrency result = (FiatCurrency) CURRENCIES.get(SymmetricPair.of(code, CurrencyType.FIAT));
        return result == null ? NULL_FIAT_CURRENCY : result;
    }

    /**
     * Get the crypto currency that has a currency code equal to the
     * given {@code}. Using {@literal "??????"} as the currency code
     * returns {@literal NULL_CRYPTO_CURRENCY}.
     */
    public static CryptoCurrency ofCrypto(@NotNull String code) {
        if (code.equals("??????")) {
            return NULL_CRYPTO_CURRENCY;
        }

        CryptoCurrency result = (CryptoCurrency) CURRENCIES.get(SymmetricPair.of(code, CurrencyType.CRYPTO));
        return result == null ? NULL_CRYPTO_CURRENCY : result;
    }

    public static List<FiatCurrency> getFiatCurrencies() {
        return CURRENCIES.values().stream()
                .filter(currency -> currency.getCurrencyType() == CurrencyType.FIAT)
                .map(currency -> (FiatCurrency) currency).toList();
    }

    public static Currency lookupBySymbol(String symbol) {
        // FIXME: why fiat?
        return CURRENCIES.values().stream().filter(currency -> currency.getSymbol().equals(symbol))
                .findAny().orElse(NULL_FIAT_CURRENCY);
    }

    public static FiatCurrency lookupFiatByCode(String code) {
        return (FiatCurrency) CURRENCIES.values().stream()
                .filter(currency -> currency.currencyType == CurrencyType.FIAT && currency.code.equals(code))
                .findAny().orElse(NULL_FIAT_CURRENCY);
    }

    public static FiatCurrency lookupLocalFiatCurrency() {
        return (FiatCurrency) CURRENCIES.values().stream()
                .filter(currency -> currency.currencyType == CurrencyType.FIAT)
                .findAny().orElse(NULL_FIAT_CURRENCY);
    }

    public CurrencyType getCurrencyType() {
        return this.currencyType;
    }

    public String getFullDisplayName() {
        return this.fullDisplayName;
    }

    public String getShortDisplayName() {
        return this.shortDisplayName;
    }

    public String getCode() {
        return this.code;
    }

    public int getFractionalDigits() {


        return fractionalDigits;
    }

    public String getSymbol() {
        return this.symbol;
    }

    /**
     * The finality of {@code equals(...)} ensures that the equality
     * contract for subclasses must be based on currency type and code alone.
     */
    @Override
    public final boolean equals(Object object) {
        if (object == null) {
            return false;
        }

        if (!(object instanceof Currency other)) {
            return false;
        }

        if (object == this) {
            return true;
        }

        return currencyType == other.currencyType && code.equals(other.code);
    }

    /**
     * The finality of {@code hashCode()} ensures that the equality
     * contract for subclasses must be based on currency
     * type and code alone.
     *
     */
    @Override
    public final int hashCode() {
        return Objects.hash(currencyType, code);
    }

    @Override
    public String toString() {
        if (this == NULL_CRYPTO_CURRENCY) {
            return "the null cryptocurrency";
        } else if (this == NULL_FIAT_CURRENCY) {
            return "the null fiat currency";
        }
        return String.format("%s (%s)", fullDisplayName, code);
    }


    @Override
    public int compareTo(@NotNull java.util.Currency o) {


        return 0;
    }

    private static class NullCryptoCurrency extends CryptoCurrency {
    }

    private static class NullFiatCurrency extends FiatCurrency {
    }
}