package org.investpro;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;



public abstract class Currency {
    private   static final Logger logger = LoggerFactory.getLogger(Currency.class);

    public static final CryptoCurrency NULL_CRYPTO_CURRENCY = new NullCryptoCurrency();
    public static final FiatCurrency NULL_FIAT_CURRENCY = new NullFiatCurrency();
    static final Map<SymmetricPair<String, CurrencyType>, Currency> CURRENCIES = new ConcurrentHashMap<>();
    protected static final Db1 db1;
    private static final Properties conf = new Properties();

    static {
        try {
            try {
                conf.load(Currency.class.getClassLoader().getResourceAsStream("conf.properties"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            db1 = new Db1(conf);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }
    protected String code;
    protected int fractionalDigits;

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
            throw new IllegalArgumentException(STR."fractional digits must be non-negative, was: \{fractionalDigits}");
        }
        Objects.requireNonNull(symbol, "symbol must not be null");

        this.currencyType = currencyType;
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;
        this.code = code;
        this.fractionalDigits = fractionalDigits;
        this.symbol = symbol;
        this.image = image;

        for (java.util.Currency currency : java.util.Currency.getAvailableCurrencies()) {
            if (currencyType == CurrencyType.FIAT && !currency.getCurrencyCode().equals(code)) {
                java.util.Currency.getAvailableCurrencies().add(currency);

            }
            if (currencyType == CurrencyType.CRYPTO && !currency.getCurrencyCode().equals(code)) {
                java.util.Currency.getAvailableCurrencies().add(currency);

            }
        }
    }


    protected static void registerCurrency(Currency currency) throws ClassNotFoundException {
        Objects.requireNonNull(currency, "currency must not be null");

        CURRENCIES.put(SymmetricPair.of(currency.code, currency.currencyType), currency);
        logger.info("registered currency: %s".formatted(currency));

        for (Currency currency1 : CURRENCIES.values()) {
            db1.save(currency1);
        }
    }

    protected static void registerCurrencies(List<Currency> currencies) {
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
        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO)) || db1.getCurrency(code).currencyType == CurrencyType.CRYPTO) {
            return db1.getCurrency(code);
        } else if (code.equals("���") || code.equals("XXX") || code.isEmpty()) {
            return NULL_CRYPTO_CURRENCY;
        }
        return db1.getCurrency(code) == null ? NULL_CRYPTO_CURRENCY : db1.getCurrency(code);


    }

    public static @Nullable Currency of(String code) throws SQLException {
        Objects.requireNonNull(code, "code must not be null");
        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO)) && db1.getCurrency(code).currencyType == CurrencyType.CRYPTO) {
            return CURRENCIES.get(SymmetricPair.of(code, CurrencyType.CRYPTO));
        } else if (code.equals("���") || code.equals("XXX") || code.isEmpty()) {
            return NULL_CRYPTO_CURRENCY;
        }
        return db1.getCurrency(code) == null ? NULL_CRYPTO_CURRENCY : db1.getCurrency(code);

    }

    /**
     * Get the fiat currency that has a currency code equal to the
     * given {@code}. Using {@literal "¤¤¤"} as the currency code
     * returns {@literal NULL_FIAT_CURRENCY}.
     *
     * @param code the currency code
     * @return the fiat currency
     */
    public static Currency ofFiat(@NotNull String code) throws SQLException {


        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.FIAT)) || db1.getCurrency(code).currencyType == CurrencyType.FIAT) {
            return db1.getCurrency(code);
        }
        return NULL_FIAT_CURRENCY;
    }

    /**
     * Get the cryptocurrency that has a currency code equal to the
     * given {@code}. Using {@literal "¤¤¤"} as the currency code
     * returns {@literal NULL_CRYPTO_CURRENCY}.
     *
     * @return the cryptocurrency
     */


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