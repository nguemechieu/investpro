package org.investpro.investpro.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CurrencyType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "currencies")
public class Currency implements Comparable<java.util.Currency> {
    public static final ConcurrentHashMap<String, Currency> CURRENCIES = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(Currency.class);

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "currency_id")
    int currencyId;

    @Column(name = "code")
    String code;

    @Column(name = "currency_type")
    String currencyType;

    @Column(name = "full_display_name")
    String fullDisplayName;

    @Column(name = "image")
    String image;

    @Column(name = "short_display_name")
    String shortDisplayName;

    @Column(name = "symbol")
    String symbol;

    @Column(name = "fractional_digits")
    int fractionalDigits;


    public Currency() {
        this.fullDisplayName = "XXX";
        this.shortDisplayName = "XXX";
        this.code = "XXX";
        this.currencyType = CurrencyType.UNKNOWN.name();
        this.fractionalDigits = 0;
        this.symbol = "XXX";
        this.image = "default.png";
        CURRENCIES.put(code, this);
    }

    public Currency(String currencyType, String fullDisplayName, String shortDisplayName, String code, int fractionalDigits, String symbol, String image) {

        this.currencyType = currencyType;
        this.fullDisplayName = Objects.requireNonNull(fullDisplayName, "fullDisplayName must not be null");
        this.shortDisplayName = Objects.requireNonNull(shortDisplayName, "shortDisplayName must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.fractionalDigits = Math.max(0, fractionalDigits);
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.image = image;
        logger.info(Currency.class.getName());
    }

    public static void save(Currency currency) {


        if (currency == null) {
            currency = new Currency();
            CURRENCIES.put(currency.getCode(), currency);

            return;

        }
        CURRENCIES.put(currency.getCode(), currency);

    }

    public static @NotNull Currency of(@NotNull String baseCurrencyCode) {


        for (java.util.Currency currency : java.util.Currency.getAvailableCurrencies()) {
            if (!baseCurrencyCode.equals("XXX") && currency.getCurrencyCode().equals(baseCurrencyCode)) {
                Currency cur = new Currency("FIAT", baseCurrencyCode, baseCurrencyCode, baseCurrencyCode, 4, baseCurrencyCode, baseCurrencyCode);

                CURRENCIES.put(cur.currencyType, cur);

            }
        }

        Currency cur = new Currency("FIAT", baseCurrencyCode, baseCurrencyCode, baseCurrencyCode, 4, baseCurrencyCode, baseCurrencyCode);

        CURRENCIES.put(cur.currencyType, cur);
        return cur;


    }

    public static void registerCurrencies(List<FiatCurrency> fiatCurrencies) {
        for (FiatCurrency fiatCurrency : fiatCurrencies) {
            Currency.save(fiatCurrency);
        }
    }

    @Override
    public String toString() {
        return "Currency{" +

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


    @Override
    public int compareTo(@NotNull java.util.Currency o) {
        return 0;
    }
}
