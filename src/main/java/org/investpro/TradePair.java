package org.investpro;

import javafx.util.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


public class TradePair extends Pair<Currency, Currency> {

    private static final Logger logger = LoggerFactory.getLogger(TradePair.class);

    private long id;


    private Currency baseCurrency;


    private Currency counterCurrency;




    private double bid;

    private double ask;

    public TradePair(@NotNull Currency baseCurrency, @NotNull Currency counterCurrency) throws SQLException, ClassNotFoundException {
        super(baseCurrency, counterCurrency);

        if (baseCurrency.equals(counterCurrency)) {
            throw new IllegalArgumentException("Base currency and counter currency cannot be the same.");
        }

        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;

        logger.debug("TradePair created: {} / {}", baseCurrency.getCode(), counterCurrency.getCode());
    }



    public TradePair(
            @NotNull String baseCurrencyCode,
            @NotNull String counterCurrencyCode
    ) throws Exception {
        super(
                Currency.of(baseCurrencyCode),
                Currency.of(counterCurrencyCode)
        );
    }

    public TradePair() throws Exception {
        super(Currency.of("EUR"), Currency.of("USD"));
    }

    @Contract("_, _ -> new")
    public static @NotNull TradePair of(@NotNull String baseCurrencyCode, @NotNull String counterCurrencyCode) throws Exception {
        return new TradePair(baseCurrencyCode, counterCurrencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull TradePair of(@NotNull Currency baseCurrency, @NotNull Currency counterCurrency) throws SQLException, ClassNotFoundException {
        return new TradePair(baseCurrency, counterCurrency);
    }

    @Contract("_ -> new")
    public static @NotNull TradePair of(@NotNull Pair<Currency, Currency> currencyPair) throws SQLException, ClassNotFoundException {
        return new TradePair(currencyPair.getKey(), currencyPair.getValue());
    }

    public static <T extends Currency, V extends Currency> @NotNull TradePair parse(@NotNull String tradePair, @NotNull String separator, @NotNull Pair<Class<T>, Class<V>> pairType) throws CurrencyNotFoundException, Exception {
        Objects.requireNonNull(tradePair, "TradePair must not be null");
        Objects.requireNonNull(pairType, "PairType must not be null");

        String[] split = separator.isEmpty() ? new String[]{tradePair.substring(0, 3), tradePair.substring(3)} : tradePair.split(separator);

        Currency base = Currency.of(split[0]);
        Currency counter = Currency.of(split[1]);

        assert base != null;
        if (base.equals(counter)) {
            throw new CurrencyNotFoundException(CurrencyType.valueOf(base.getCode()), counter.getCode());
        }

        assert counter != null;
        return new TradePair(base, counter);
    }

    public @NotNull List<String> getTradePairs() {
        List<String> tradePairs = new ArrayList<>();
        tradePairs.add(toString('-')); // Default separator is '-'
        return tradePairs;
    }

    public Currency getBaseCurrency() {
        return baseCurrency;
    }

    public Currency getCounterCurrency() {
        return counterCurrency;
    }

    public String toString(@NotNull Character separator) {
        if (separator.equals('_')) {
            return String.format("%s_%s", getBaseCurrency().getCode().toUpperCase(), getCounterCurrency().getCode().toUpperCase());
        } else if (separator.equals('-')) {
            return String.format("%s-%s", getBaseCurrency().getCode().toUpperCase(), getCounterCurrency().getCode().toUpperCase());
        } else if (separator.equals('/')) {
            return String.format("%s%s", getBaseCurrency().getCode().toUpperCase(), getCounterCurrency().getCode().toUpperCase());
        } else {
            throw new IllegalArgumentException("Invalid separator: %s".formatted(separator));
        }
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getSymbol() {
        return String.format("%s/%s", baseCurrency.getSymbol(), counterCurrency.getSymbol());
    }

    public double getBid() {
        return bid;
    }

    public void setBid(double bid) {
        if (bid < 0) {
            throw new IllegalArgumentException("Bid price must be positive.");
        }
        this.bid = bid;
    }

    public double getAsk() {
        return ask;
    }

    public void setAsk(double ask) {
        if (ask < 0) {
            throw new IllegalArgumentException("Ask price must be positive.");
        }
        this.ask = ask;
    }
}
