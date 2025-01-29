package org.investpro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@Entity
@Table(name = "currencies")
public class Currency {
    static final ConcurrentHashMap<String, Currency> CURRENCIES = new ConcurrentHashMap<String, Currency>();
    private static final Logger logger = LoggerFactory.getLogger(Currency.class);
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "currency_id", nullable = false, updatable = false)
    private long currency_id;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "currencyType", nullable = false)
    private String currencyType;

    @Column(name = "fullDisplayName", nullable = false)
    private String fullDisplayName;

    @Column(name = "image")
    private String image;

    @Column(name = "shortDisplayName", nullable = false)
    private String shortDisplayName;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "fractionalDigits", nullable = false)
    private int fractionalDigits;

    static {
        try {
            new CurrencyDataProvider().registerCurrencies();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // Default constructor
    public Currency() {
    }

    // Parameterized constructor
    public Currency(
            @NotNull CurrencyType currencyType,
            String fullDisplayName,
            String shortDisplayName,
            String code,
            int fractionalDigits,
            String symbol,
            String image) {
        this.currencyType = currencyType.name();
        this.fullDisplayName = Objects.requireNonNull(fullDisplayName, "fullDisplayName must not be null");
        this.shortDisplayName = Objects.requireNonNull(shortDisplayName, "shortDisplayName must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");

        if (fractionalDigits < 0) {
            throw new IllegalArgumentException("fractional digits must be non-negative, was: " + fractionalDigits);
        }
        this.fractionalDigits = fractionalDigits;
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.image = image;

        logger.info("Currency registered: {}", this);
    }

    // Factory method
    public static @NotNull Currency of(String code) throws Exception {
        Objects.requireNonNull(code, "code must not be null");
        if (CURRENCIES.isEmpty() || CURRENCIES.containsKey(code)) {
            for (java.util.Currency currency : java.util.Currency.getAvailableCurrencies()) {
                Currency curr = new Currency();
                curr.code = currency.getCurrencyCode();
                curr.currencyType = CurrencyType.FIAT.name();
                curr.fullDisplayName = currency.getDisplayName();
                curr.shortDisplayName = currency.getCurrencyCode();
                curr.symbol = currency.getSymbol() + ".png";
                curr.image = currency.getCurrencyCode();
                curr.fractionalDigits = currency.getDefaultFractionDigits();
                CURRENCIES.put(curr.currencyType, curr);
            }
        }

        Currency cur = CURRENCIES.get(code);

        if (cur == null) {
            cur = new Currency();
            cur.code = code;
            cur.currencyType = CurrencyType.FIAT.name();
            cur.shortDisplayName = code;
            cur.symbol = code;
            cur.image = code + ".png";
            cur.fractionalDigits = 3;
            return cur;
        }

        return CURRENCIES.get(code);

    }

    public static void save(ArrayList<Currency> collect) {

        for (java.util.Currency currency : java.util.Currency.getAvailableCurrencies()) {


            Currency curr = new Currency();
            curr.code = currency.getCurrencyCode();
            curr.currencyType = CurrencyType.FIAT.name();
            curr.fullDisplayName = currency.getDisplayName();
            curr.shortDisplayName = currency.getCurrencyCode();
            curr.symbol = currency.getSymbol() + ".png";
            curr.image = currency.getCurrencyCode();
            curr.fractionalDigits = currency.getDefaultFractionDigits();
            CURRENCIES.put(curr.currencyType, curr);
        }

        for (Currency currency : collect) {
            CURRENCIES.putIfAbsent(
                    currency.currencyType,
                    currency
            );
        }
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

    @Override
    public final int hashCode() {
        return Objects.hash(currencyType, code);
    }

    @Override
    public final boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        Currency currency = (Currency) object;
        return Objects.equals(currencyType, currency.currencyType) && Objects.equals(code, currency.code);
    }
}
