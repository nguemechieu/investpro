package org.investpro;

import javafx.util.Pair;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;

@Getter
public class TradePair extends Pair<Currency, Currency> {

    private static final Logger logger = LoggerFactory.getLogger(TradePair.class);

    // Getter for TradePair ID
    // Setter for TradePair ID
    @Setter
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

    // Getter for base currency
    @Setter
    private Currency baseCurrency;

    // Getter for counter currency
    @Setter
    private Currency counterCurrency;

    // Getter for bid price
    private double bid;

    // Getter for ask price
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

    // Get the symbol of the trade pair, e.g., USD/EUR
    public String getSymbol() {
        return String.format("%s/%s", baseCurrency.getSymbol(), counterCurrency.getSymbol());
    }

    // Setter for bid price with validation
    public void setBid(double bid) {
        if (bid < 0) {
            throw new IllegalArgumentException("Bid price must be positive.");
        }
        this.bid = bid;
    }

    // Setter for ask price with validation
    public void setAsk(double ask) {
        if (ask < 0) {
            throw new IllegalArgumentException("Ask price must be positive.");
        }
        this.ask = ask;
    }
}
