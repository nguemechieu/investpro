package org.investpro;

import javafx.util.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

public class TradePair extends Pair<Currency, Currency> {

    private static final Logger logger = LoggerFactory.getLogger(TradePair.class);

    private long id;

    // Constructor that takes two currencies
    public TradePair(@NotNull Currency baseCurrency, @NotNull Currency counterCurrency) throws SQLException, ClassNotFoundException {
        super(baseCurrency, counterCurrency);

        if (baseCurrency.equals(counterCurrency)) {
            throw new IllegalArgumentException("Base currency and counter currency cannot be the same.");
        }

        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;

        logger.debug("TradePair created: {} / {}", baseCurrency.getCode(), counterCurrency.getCode());
    }

    // Constructor that takes currency codes
    public TradePair(@NotNull String baseCurrencyCode, @NotNull String counterCurrencyCode) throws Exception {
        super(Currency.of(baseCurrencyCode), Currency.of(counterCurrencyCode));

        if (baseCurrencyCode.equalsIgnoreCase(counterCurrencyCode)) {
            throw new IllegalArgumentException("Base currency and counter currency cannot be the same.");
        }

        this.baseCurrency = Currency.of(baseCurrencyCode);
        this.counterCurrency = Currency.of(counterCurrencyCode);
    }

    private Currency baseCurrency;

    private Currency counterCurrency;

    private double bid;

    private double ask;

    // Factory method for TradePair using currency codes
    @Contract("_, _ -> new")
    public static @NotNull TradePair of(@NotNull String baseCurrencyCode, @NotNull String counterCurrencyCode) throws Exception {
        return new TradePair(baseCurrencyCode, counterCurrencyCode);
    }

    // Factory method for TradePair using Currency objects
    @Contract("_, _ -> new")
    public static @NotNull TradePair of(@NotNull Currency baseCurrency, @NotNull Currency counterCurrency) throws SQLException, ClassNotFoundException {
        return new TradePair(baseCurrency, counterCurrency);
    }

    // Factory method for TradePair using a Pair of currencies
    @Contract("_ -> new")
    public static @NotNull TradePair of(@NotNull Pair<Currency, Currency> currencyPair) throws SQLException, ClassNotFoundException {
        return new TradePair(currencyPair.getKey(), currencyPair.getValue());
    }

    // Getter for base currency
    public Currency getBaseCurrency() {
        return baseCurrency;
    }

    public void setBaseCurrency(Currency baseCurrency) {
        this.baseCurrency = baseCurrency;
    }

    // Getter for counter currency
    public Currency getCounterCurrency() {
        return counterCurrency;
    }

    public void setCounterCurrency(Currency counterCurrency) {
        this.counterCurrency = counterCurrency;
    }

    // String representation of the TradePair with a given separator
    public String toString(@NotNull Character separator) {
        String baseCode = getBaseCurrency().getCode().toUpperCase();
        String counterCode = getCounterCurrency().getCode().toUpperCase();

        return switch (separator) {
            case '_' -> String.format("%s_%s", baseCode, counterCode);
            case '-' -> String.format("%s-%s", baseCode, counterCode);
            case '/' -> String.format("%s/%s", baseCode, counterCode);
            default -> throw new IllegalArgumentException("Invalid separator: " + separator);
        };
    }

    // Getter for TradePair ID
    public long getId() {
        return id;
    }

    // Setter for TradePair ID
    public void setId(long id) {
        this.id = id;
    }

    // Get the symbol of the trade pair, e.g., USD/EUR
    public String getSymbol() {
        return String.format("%s/%s", baseCurrency.getSymbol(), counterCurrency.getSymbol());
    }

    // Getter for bid price
    public double getBid() {
        return bid;
    }

    // Setter for bid price with validation
    public void setBid(double bid) {
        if (bid < 0) {
            throw new IllegalArgumentException("Bid price must be positive.");
        }
        this.bid = bid;
    }

    // Getter for ask price
    public double getAsk() {
        return ask;
    }

    // Setter for ask price with validation
    public void setAsk(double ask) {
        if (ask < 0) {
            throw new IllegalArgumentException("Ask price must be positive.");
        }
        this.ask = ask;
    }
}
