package org.investpro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@Setter
@Entity
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@Table(name = "currencies")
public   class Currency implements Comparable<Currency> {
    static final ConcurrentHashMap<String, Currency> CURRENCIES = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(Currency.class);

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "currency_id")
    protected int currencyId;

    @Column(name = "code")
    protected String code;

    @Column(name = "currency_type")
    protected String currencyType;

    @Column(name = "full_display_name")
    protected String fullDisplayName;

    @Column(name = "image")
    protected String image;

    @Column(name = "short_display_name")
    protected String shortDisplayName;

    @Column(name = "symbol")
    protected String symbol;

    @Column(name = "fractional_digits")
    protected int fractionalDigits;



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



            for (java.util.Currency currency:java.util.Currency.getAvailableCurrencies()) {
                if (!baseCurrencyCode.equals("XXX") && currency.getCurrencyCode().equals(baseCurrencyCode)) {
                    Currency cur = new Currency("FIAT", baseCurrencyCode, baseCurrencyCode, baseCurrencyCode, 4, baseCurrencyCode, baseCurrencyCode);

                    CURRENCIES.put(cur.currencyType, cur);

                }
            }

            Currency cur = new Currency("FIAT", baseCurrencyCode, baseCurrencyCode, baseCurrencyCode, 4, baseCurrencyCode, baseCurrencyCode);

            CURRENCIES.put(cur.currencyType, cur);
            return cur;


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
    public int compareTo(@NotNull Currency o) {
        return this.code.compareTo(o.code);
    }


}
