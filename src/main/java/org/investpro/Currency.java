package org.investpro;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.investpro.ui.TradingWindow.db1;


@Getter
@Setter
@Entity

@Inheritance(strategy = InheritanceType.SINGLE_TABLE)  // or
@Table(name = "currencies")
public abstract class Currency implements Comparable<Currency> {  // ✅ Remove "abstract"
    static final ConcurrentHashMap<String, Currency> CURRENCIES = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(Currency.class);


    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "currency_id", nullable = false, updatable = false)
    private long currencyId;  // Use wrapper class `Long` to handle null values

    @Column(name = "code", nullable = false, unique = true)
    protected String code;

    @Column(name = "currency_type", nullable = false)
    protected String currencyType;

    @Column(name = "full_display_name", nullable = false)
    private String fullDisplayName;

    @Column(name = "image")
    private String image;

    @Column(name = "short_display_name", nullable = false)
    private String shortDisplayName;

    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "fractional_digits", nullable = false)
    private int fractionalDigits;


    // ✅ Default constructor required by JPA
    public Currency() {
    }

    // ✅ Parameterized constructor
    public Currency(@NotNull CurrencyType currencyType, String fullDisplayName, String shortDisplayName,
                    String code, int fractionalDigits, String symbol, String image) {
        this.currencyType = currencyType.name();
        this.fullDisplayName = Objects.requireNonNull(fullDisplayName, "fullDisplayName must not be null");
        this.shortDisplayName = Objects.requireNonNull(shortDisplayName, "shortDisplayName must not be null");
        this.code = Objects.requireNonNull(code, "code must not be null");
        this.fractionalDigits = Math.max(0, fractionalDigits);
        this.symbol = Objects.requireNonNull(symbol, "symbol must not be null");
        this.image = image;

        logger.info("✅ Currency registered: {}", this);
    }

    public static void save(Currency currency) {


        CURRENCIES.put(currency.getCode(), currency);
        //db1.save(currency);


    }

    public static Currency of(@NotNull String baseCurrencyCode) {


        // Return if already cached
        if (CURRENCIES.containsKey(baseCurrencyCode)) {
            return CURRENCIES.get(baseCurrencyCode);
        }

        // Fetch from DB if not in cache
        Currency cur = db1.getCurrency(baseCurrencyCode);
        if (cur != null) {
            CURRENCIES.put(baseCurrencyCode, cur);
            return cur;
        }


        // Return a fallback empty Currency object
        return new Currency() {
            @Override
            public int compareTo(java.util.@NotNull Currency o) {
                return 0;
            }
        };
    }


    @Override
    public String toString() {
        return "Currency{" +
                "currencyId=" + currencyId +
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

    public abstract int compareTo(java.util.@NotNull Currency o);
}
