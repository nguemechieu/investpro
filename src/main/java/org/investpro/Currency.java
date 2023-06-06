package org.investpro;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents some currency. Could be a fiat currency issued by a country or a cryptocurrency.
 *
 * @author Michael Ennen
 */
public abstract class Currency {

    public static final CryptoCurrency NULL_CRYPTO_CURRENCY = new NullCryptoCurrency();
    public static final FiatCurrency NULL_FIAT_CURRENCY = new NullFiatCurrency();
    protected static final Db1 db1;
    private static final Map<SymmetricPair<String, CurrencyType>, Currency> CURRENCIES = new ConcurrentHashMap<>();

    static {
        try {
            @NotNull Properties conf = new Properties();
            conf.load(Currency.class.getResourceAsStream("/currency.properties"));
            db1 = new Db1(conf);
        } catch (ClassNotFoundException | IOException e) {
            throw new RuntimeException(e);
        }
    }
    protected String code;
    protected int fractionalDigits;

    static {
        CryptoCurrencyDataProvider cryptoCurrencyDataProvider = new CryptoCurrencyDataProvider();
        cryptoCurrencyDataProvider.registerCurrencies();
        FiatCurrencyDataProvider fiatCurrencyDataProvider = new FiatCurrencyDataProvider();
        fiatCurrencyDataProvider.registerCurrencies();
    }

    private final CurrencyType currencyType;
    protected String fullDisplayName;
    protected String shortDisplayName;
    private static final Logger logger = LoggerFactory.getLogger(Currency.class);
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
            throw new IllegalArgumentException("fractional digits must be non-negative, was: " + fractionalDigits);
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


    protected static void registerCurrency(Currency currency) {
        Objects.requireNonNull(currency, "currency must not be null");

        CURRENCIES.put(SymmetricPair.of(currency.code, currency.currencyType), currency);
        db1.save(currency);
    }

    protected static void registerCurrencies(Collection<Currency> currencies) {
        Objects.requireNonNull(currencies, "currencies must not be null");
        currencies.forEach(Currency::registerCurrency);
        db1.save(currencies.stream().findAny().get());
    }

    public static @Nullable Currency of(String code) {
        Objects.requireNonNull(code, "code must not be null");
        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.FIAT))
                && CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO))) {
            logger.error("ambiguous currency code: " + code);
            throw new IllegalArgumentException("ambiguous currency code: " + code + " (code" +
                    " is used for multiple currency types); use ofCrypto(...) or ofFiat(...) instead");
        } else {

            try {
                return db1.getCurrency(code);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Get the fiat currency that has a currency code equal to the
     * given {@code}. Using {@literal "¤¤¤"} as the currency code
     * returns {@literal NULL_FIAT_CURRENCY}.
     *
     * @param code the currency code
     * @return the fiat currency
     */
    public static Currency ofFiat(@NotNull String code) {
        if (code.equals("¤¤¤")) {
            return NULL_FIAT_CURRENCY;
        }

        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.FIAT))) {
            return CURRENCIES.get(SymmetricPair.of(code, CurrencyType.FIAT));
        } else {
            try {
                if (db1.getCurrency(code) == null) {
                    return NULL_FIAT_CURRENCY;
                } else {
                    try {
                        if (db1.getCurrency(code).currencyType == CurrencyType.FIAT) {

                            try {
                                return db1.getCurrency(code);
                            } catch (SQLException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return
                (Objects.equals(CURRENCIES.get(SymmetricPair.of(code, CurrencyType.FIAT)).code, code)) ?
                        CURRENCIES.get(SymmetricPair.of(code, CurrencyType.FIAT)) :
                        NULL_FIAT_CURRENCY;


    }

    /**
     * Get the cryptocurrency that has a currency code equal to the
     * given {@code}. Using {@literal "¤¤¤"} as the currency code
     * returns {@literal NULL_CRYPTO_CURRENCY}.
     *
     * @param code the currency code
     * @return the cryptocurrency
     */
    public static Currency ofCrypto(@NotNull String code) {
        if (code.equals("¤¤¤")) {
            return NULL_CRYPTO_CURRENCY;
        }

        try {
            return db1.getCurrency(code) == null ? NULL_CRYPTO_CURRENCY : db1.getCurrency(code);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
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
        return this.fractionalDigits;
    }

    public String getSymbol() {
        return this.symbol;
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
        return String.format("%s (%s)", fullDisplayName, code);
    }

    public abstract int compareTo(@NotNull Currency o);

    public abstract int compareTo(java.util.@NotNull Currency o);

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    private static class NullCryptoCurrency extends CryptoCurrency {
        private NullCryptoCurrency() {
            super(

                    "Null Crypto Currency",
                    "Null Crypto Currency",
                    "XXX",
                    8,
                    "¤¤¤",
                    "https://i.ibb.co/5Y3mZ5Y/null-crypto-currency.png"
            );

        }
    }

    private static class NullFiatCurrency extends FiatCurrency {

        public NullFiatCurrency() {
            super(
                    "¤¤¤",
                    "¤¤¤", "¤¤¤",
                    2,
                    "¤¤¤",
                    "https://i.ibb.co/5Y3mZ5Y/null-fiat-currency.png"
            );
        }
    }
}
