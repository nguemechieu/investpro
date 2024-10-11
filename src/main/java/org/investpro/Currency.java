package org.investpro;


import jakarta.persistence.*;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;


@Entity
@Table(name = "currencies")
public class Currency {
    private   static final Logger logger = LoggerFactory.getLogger(Currency.class);
    static final DbHibernate db1 = new DbHibernate();

    static ConcurrentHashMap<SymmetricPair<CurrencyType, Currency>, Currency> CURRENCIES = new ConcurrentHashMap<>();

    public void setShortDisplayName(String shortDisplayName) {
        this.shortDisplayName = shortDisplayName;
    }

    public void setFullDisplayName(String fullDisplayName) {
        this.fullDisplayName = fullDisplayName;
    }

    public void setFractionalDigits(int fractionalDigits) {
        this.fractionalDigits = fractionalDigits;
    }
    @Column(name = "code")
    String code;
    @Column(name = "currencyType")
    String currencyType;
    @Column(name = "fullDisplayName")
    String fullDisplayName;
    @Column(name = "image")
    String image;
    @Column(name = "shortDisplayName")
    String shortDisplayName;
    @Column(name = "symbol")
    String symbol;
    @Column(name = "fractionalDigits")
    int fractionalDigits;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // This will auto-generate the ID

    @Column(name = "currency_id", updatable = false)
    private Long currency_id;

    /**
     * Protected constructor, called only by CurrencyDataProvider's.
     */
    public Currency(long currency_id, CurrencyType currencyType, String fullDisplayName, String shortDisplayName, String code,
                       int fractionalDigits, String symbol, String image) throws Exception {
        Objects.requireNonNull(currencyType, "currencyType must not be null");
        Objects.requireNonNull(fullDisplayName, "fullDisplayName must not be null");
        Objects.requireNonNull(shortDisplayName, "shortDisplayName must not be null");
        Objects.requireNonNull(code, "code must not be null");

        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractional digits must be non-negative, was: " + fractionalDigits);
        }
        Objects.requireNonNull(symbol, "symbol must not be null");

        this.currencyType = String.valueOf(currencyType);
        this.fullDisplayName = fullDisplayName;
        this.shortDisplayName = shortDisplayName;
        this.code = code;
        this.fractionalDigits = fractionalDigits;
        this.symbol = symbol;
        this.image = image;
        this.currency_id = currency_id;

        logger.info("currency registered: {}", this);
    }

    public static @Nullable Currency of(String code) throws Exception {
        Objects.requireNonNull(code, "code must not be null");

            return db1.getCurrency(code);

    }

    // Getter and Setter for currencyId
    public Long getCurrencyId() {
        return currency_id;
    }

    public Currency() {

    }



    public void setCode(String code) {
        this.code = code;
    }


    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }

    public void setCurrencyId(Long currencyId) {
        this.currency_id = currencyId;
    }

    @Override
    public String toString() {
        return "Currency{" +
                "currency_id=" + currency_id +
                ", code='" + code + '\'' +
                ", currencyType='" + currencyType + '\'' +
                ", fullDisplayName='" + fullDisplayName + '\'' +
                ", image='" + image + '\'' +
                ", shortDisplayName='" + shortDisplayName + '\'' +
                ", symbol='" + symbol + '\'' +
                ", fractionalDigits=" + fractionalDigits +
                '}';
    }

    /**
     * Get the cryptocurrency that has a currency code equal to the
     * given {@code}. Using {@literal "¤¤¤"} as the currency code
     * returns {@literal NULL_CRYPTO_CURRENCY}.
     *
     * @return the cryptocurrency
     */


    public String getCurrencyType() {
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

    public void setCurrencyType(CurrencyType currencyType) {
        this.currencyType = String.valueOf(currencyType);
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

        return Objects.equals(currencyType, other.currencyType) && code.equals(other.code);
    }

}