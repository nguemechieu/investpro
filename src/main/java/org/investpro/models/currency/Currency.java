package org.investpro.models.currency;

import lombok.extern.slf4j.Slf4j;

import lombok.Getter;
import  org.investpro.data.Db1;

import  org.investpro.utils.SymmetricPair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;



@Getter
@Slf4j
public abstract class Currency {
    public static CryptoCurrency NULL_CRYPTO_CURRENCY;
    public static final FiatCurrency NULL_FIAT_CURRENCY;

    static {
        try {
            NULL_CRYPTO_CURRENCY = new NullCryptoCurrency();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    static {
        try {
            NULL_FIAT_CURRENCY = new NullFiatCurrency();
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public static final Map<SymmetricPair<String, CurrencyType>, Currency> CURRENCIES = new ConcurrentHashMap<>();
    protected static final Db1 db1;
    private static final Properties conf = new Properties();

    static {
        try {
            try (InputStream inputStream = Currency.class.getClassLoader().getResourceAsStream("conf.properties")) {
                if (inputStream != null) {
                    conf.load(inputStream);
                } else {
                    log.warn("conf.properties not found in classpath; using default currency database configuration");
                }
            } catch (IOException exception) {
                log.warn("Unable to load conf.properties; using default currency database configuration", exception);
            }
            db1 = new Db1(conf);
            db1.createTables();
        } catch (ClassNotFoundException exception) {
            throw new RuntimeException(exception);
        }
    }
    protected String code;
    protected int fractionalDigits;

    /**
     * -- GETTER --
     *  Get the cryptocurrency that has a currency code equal to the
     *  given
     * . Using
     *  as the currency code
     *  returns
     * .
     *
     * @return the cryptocurrency
     */
    private final CurrencyType currencyType;
    protected String fullDisplayName;
    protected String shortDisplayName;

    protected String symbol;
    private String image;


    /**
     * Protected constructor, called only by CurrencyDataProvider's.
     */
    protected Currency(CurrencyType currencyType, String fullDisplayName, String shortDisplayName, String code,
                       int fractionalDigits, String symbol, String image) {
        Objects.requireNonNull(currencyType, "currencyType must not be null");
        Objects.requireNonNull(fullDisplayName, "fullDisplayName must not be null");
        Objects.requireNonNull(shortDisplayName, "shortDisplayName must not be null");
        Objects.requireNonNull(code, "code must not be null");

        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractional digits must be non-negative, was: %d".formatted(fractionalDigits));
        }
        Objects.requireNonNull(symbol, "symbol must not be null");

        this.currencyType = currencyType;
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;
        this.code = code;
        this.fractionalDigits = fractionalDigits;
        this.symbol = symbol;
        this.image = image;



    }


    protected static void registerCurrency(Currency currency) throws ClassNotFoundException {
        Objects.requireNonNull(currency, "currency must not be null");

        CURRENCIES.put(SymmetricPair.of(currency.code, currency.currencyType), currency);
        log.info("registered currency: " + currency);

        for (Currency currency1 : CURRENCIES.values()) {
            db1.save(currency1);
        }
    }

    public static void registerCurrencies(List<Currency> currencies) {
        Objects.requireNonNull(currencies, "currencies must not be null");

        for (Currency currency : currencies) {
            CURRENCIES.put(SymmetricPair.of(currency.code, currency.currencyType), currency);

        }
        for (Currency currency : currencies) {
            try {
                registerCurrency(currency);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static Currency ofCrypto(String code) throws SQLException {
        Objects.requireNonNull(code, "code must not be null");

        // Return null currency for reserved codes
        if (isNullCurrencyCode(code)) {
            return NULL_CRYPTO_CURRENCY;
        }

        String normalizedCode = code.trim().toUpperCase();
        Currency currency = CurrencyRegistry.global().findByCode(normalizedCode).orElse(null);
        if (currency == null) {
            currency = CurrencyRegistry.global().findOrUnknown(normalizedCode);
        }

        return currency.getCurrencyType() == CurrencyType.CRYPTO ? currency : NULL_CRYPTO_CURRENCY;
    }
    
    /**
     * Check if code represents a null/unknown currency.
     */
    private static boolean isNullCurrencyCode(String code) {
        return code.equals("¤¤¤") || code.equals("XXX") || code.isEmpty();
    }

    public static @Nullable Currency of(String code) throws SQLException {
        Objects.requireNonNull(code, "code must not be null");

        // Return null currency for reserved codes
        if (isNullCurrencyCode(code)) {
            return NULL_CRYPTO_CURRENCY;
        }

        String normalizedCode = code.trim().toUpperCase();
        return CurrencyRegistry.global().findByCode(normalizedCode)
                .orElseGet(() -> CurrencyRegistry.global().findOrUnknown(normalizedCode));
    }

    /**
     * Get the fiat currency with the given code. Returns NULL_FIAT_CURRENCY if not found
     * or if code is a reserved null currency code like "¤¤¤" or "XXX".
     *
     * @param code the currency code (not null)
     * @return the fiat currency, or NULL_FIAT_CURRENCY if not found
     */
    public static Currency ofFiat(@NotNull String code) throws SQLException {
        Objects.requireNonNull(code, "code must not be null");

        // Return null currency for reserved codes
        if (isNullCurrencyCode(code)) {
            return NULL_FIAT_CURRENCY;
        }

        String normalizedCode = code.trim().toUpperCase();
        Currency currency = CurrencyRegistry.global().findByCode(normalizedCode).orElse(null);
        if (currency == null) {
            currency = CurrencyRegistry.global().findOrUnknown(normalizedCode);
        }

        return currency.getCurrencyType() == CurrencyType.FIAT ? currency : NULL_FIAT_CURRENCY;
    }

    /**
     * The finality of {@code equals(...)} ensures that the equality
     * contract for subclasses must be based on currency type and code alone.
     *
     * @param object the object to compare to
     * @return the result
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
     * @return the result
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
        return "%s (%s)".formatted(fullDisplayName, code);
    }


    public void setImage(String image) {
        this.image = image;
    }



}
