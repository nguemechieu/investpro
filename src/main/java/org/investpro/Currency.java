package org.investpro;


import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public abstract class Currency implements Comparable<Currency> {
    private   static final Logger logger = LoggerFactory.getLogger(Currency.class);

    public static final CryptoCurrency NULL_CRYPTO_CURRENCY;
    public static final FiatCurrency NULL_FIAT_CURRENCY;

    static ConcurrentHashMap<SymmetricPair<Currency, CurrencyType>, Currency> CURRENCIES = new ConcurrentHashMap<>();
    static Db1 db1;

    static {
        try {
            NULL_CRYPTO_CURRENCY = new NullCryptoCurrency();

            NULL_FIAT_CURRENCY = new NullFiatCurrency();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        try {

            new CryptoCurrencyDataProvider().registerCurrencies();
            new FiatCurrencyDataProvider().registerCurrencies();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    protected String code;
    protected int fractionalDigits;

    protected CurrencyType currencyType;
    protected String fullDisplayName;
    protected String shortDisplayName;

    protected String symbol;
    protected String image;



    /**
     * Protected constructor, called only by CurrencyDataProvider's.
     */
    protected Currency(CurrencyType currencyType, String fullDisplayName, String shortDisplayName, String code,
                       int fractionalDigits, String symbol, String image) throws Exception {
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

        db1 = new Db1();
        logger.info("currency registered: {}", this);



    }


    public static Currency ofCrypto(String code) throws SQLException, ClassNotFoundException {
        Objects.requireNonNull(code, "code must not be null");
        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO)) || db1.getCurrency(code).currencyType == CurrencyType.CRYPTO) {
            return db1.getCurrency(code);
        } else if (code.equals("���") || code.equals("XXX") || code.isEmpty()) {
            return NULL_CRYPTO_CURRENCY;
        }
        return db1.getCurrency(code) == null ? NULL_CRYPTO_CURRENCY : db1.getCurrency(code);


    }

    public static @Nullable Currency of(String code) throws SQLException, ClassNotFoundException {
        Objects.requireNonNull(code, "code must not be null");
        if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.CRYPTO)) || db1.getCurrency(code).currencyType == CurrencyType.CRYPTO) {
            return db1.getCurrency(code);
        } else if (CURRENCIES.containsKey(SymmetricPair.of(code, CurrencyType.FIAT)) || db1.getCurrency(code).currencyType == CurrencyType.FIAT) {
            return db1.getCurrency(code);
        } else if (code.equals("���") || code.equals("XXX") || code.isEmpty()) {
            return NULL_CRYPTO_CURRENCY;
        }
        return db1.getCurrency(code) == null ? NULL_FIAT_CURRENCY : db1.getCurrency(code);

    }

    /**
     * Get the fiat currency that has a currency code equal to the
     * given {@code}. Using {@literal "¤¤¤"} as the currency code
     * returns {@literal NULL_FIAT_CURRENCY}.
     *
     * @param code the currency code
     * @return the fiat currency
     */
    public static Currency ofFiat(@NotNull String code) throws SQLException, ClassNotFoundException {


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


    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }


    public BigDecimal getCurrentPrice() {
        // implement logic to fetch current price from external API
        //...
        return BigDecimal.ZERO;
    }

    @Override
    public int compareTo(@NotNull Currency o) {
        return 0;
    }

    public abstract int compareTo(java.util.@NotNull Currency o);
}