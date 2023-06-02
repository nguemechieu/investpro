package org.investpro;

import javafx.scene.control.Alert;
import javafx.util.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.Date;
import java.util.Objects;

import static java.lang.System.out;


public class TradePair extends Pair<Currency, Currency> {

    static final Logger logger = LoggerFactory.getLogger(TradePair.class);



    Currency baseCurrency;
    Currency counterCurrency;
    private String tradePairCode;
    private Iterable<? extends Trade> trades;
    private long orderListId;
    private long id;

    public TradePair(Currency baseCurrency, Currency counterCurrency) throws SQLException {
        super(baseCurrency, counterCurrency);
        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;

        logger.debug("TradePair created: {}", this);
        logger.debug("TradePair created: {}", this);

    }

    public TradePair(String baseCurrency, String counterCurrency) throws SQLException, ClassNotFoundException {
        super(Currency.of(baseCurrency), Currency.of(counterCurrency));

        this.baseCurrency = Currency.of(baseCurrency);
        this.counterCurrency = Currency.of(counterCurrency);
        logger.debug("TradePair created: {}", this);
    }


    @Contract("_, _ -> new")
    public static @NotNull TradePair of(String baseCurrencyCode, String counterCurrencyCode) throws SQLException, ClassNotFoundException {
        return new TradePair(baseCurrencyCode, counterCurrencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull TradePair of(@NotNull Currency baseCurrency, Currency counterCurrency) throws SQLException {

        if (baseCurrency.code.isEmpty() || counterCurrency.code.isEmpty()) {
            throw new IllegalArgumentException(
                    String.format(
                            "TradePair code not found : %s %s",
                            baseCurrency,
                            counterCurrency));
        }
        return new TradePair(baseCurrency, counterCurrency);
    }

    @Contract("_ -> new")
    public static @NotNull TradePair of(@NotNull Pair<Currency, Currency> currencyPair) throws SQLException {
        return new TradePair(currencyPair.getKey(), currencyPair.getValue());
    }

    public static <T extends Currency, V extends Currency> @NotNull TradePair parse(
            String tradePair, @NotNull String separator, Pair<Class<T>, Class<V>> pairType)
            throws CurrencyNotFoundException, SQLException, ClassNotFoundException {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(pairType, "pairType must not be null");
        Objects.requireNonNull(pairType.getKey(), "first member of pairType must not be null");

        String[] split;

        if (separator.equals("")) {
            // We don't know where to split so try the most logical thing (after 3 characters). We could
            // extend this by checking that the substring is indeed a real currency and if not try 4
            // characters.
            split = new String[]{tradePair.substring(0, 3), tradePair.substring(3)};

        } else {
            split = tradePair.split(separator);
        }

        if (pairType.getKey().equals(FiatCurrency.class) && pairType.getValue().equals(FiatCurrency.class)) {
            // tradePair must be (fiat, something)
            if (Currency.of(split[0]) == Currency.of(split[1])) {
                throw
                        new CurrencyNotFoundException(CurrencyType.valueOf(split[0]), split[1]);
            }

            if (pairType.getValue() == null) {
                // The counter currency is not specified, so try both (fiat first)
                if (Currency.of(split[1]) != Currency.NULL_FIAT_CURRENCY) {
                    return new TradePair(Currency.of(split[0]), Currency.of(split[1]));
                } else if (Currency.of(split[1]) != Currency.NULL_FIAT_CURRENCY) {
                    return new TradePair(Currency.of(split[0]), Currency.of(split[1]));
                } else {
                    //
                    throw new CurrencyNotFoundException(CurrencyType.valueOf(split[1]), split[0]);
                    //TradePair.of(Currency.NULL_FIAT_CURRENCY, Currency.NULL_CRYPTO_CURRENCY);
                }
            } else if (pairType.getValue().equals(FiatCurrency.class)) {
                if (Currency.of(split[1]) == Currency.NULL_FIAT_CURRENCY) {
                    throw new CurrencyNotFoundException(CurrencyType.FIAT, split[1]);
                } else {
                    return new TradePair(Currency.of(split[0]), Currency.of(split[1]));
                }
            } else if (pairType.getValue().equals(CryptoCurrency.class)) {
                if (Currency.of(split[1]) == Currency.NULL_CRYPTO_CURRENCY) {
                    throw new CurrencyNotFoundException(CurrencyType.CRYPTO, split[1]);
                } else {
                    return new TradePair(Currency.of(split[0]), Currency.of(split[1]));
                }
            } else {
                logger.error("bad value for second member of pairType - must be one of CryptoCurrency.class, " +
                        "FiatCurrency.class, or null but was: " + pairType.getValue());
                throw new IllegalArgumentException("bad value for second member of pairType - must be one of " +
                        "CryptoCurrency.class, FiatCurrency.class, or null but was: " + pairType.getValue());
            }
        } else {
            // tradePair must be (crypto, something)
            if (Currency.of(split[0]) == Currency.NULL_CRYPTO_CURRENCY) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText(
                        "The base currency of the trade pair must be a crypto currency, but was: " + split[0]);
                alert.showAndWait();
                //throw new CurrencyNotFoundException(CurrencyType.CRYPTO, split[0]);
                return new TradePair(Currency.NULL_CRYPTO_CURRENCY, Currency.of(split[1]));
            } else if (pairType.getValue() == null) {

                Log.error("bad value for second member of pairType - must be one of CryptoCurrency.class,");

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText(
                        "The counter currency of the trade pair must be a fiat or crypto currency, but was: " + split[1]);
                alert.showAndWait();
                return new TradePair(Currency.of(split[0]), Currency.of(split[1]));

            } else if (pairType.getValue().equals(FiatCurrency.class)) {
                if (Currency.of(split[1]) == Currency.NULL_FIAT_CURRENCY) {
                    throw new CurrencyNotFoundException(CurrencyType.FIAT, split[1]);
                } else {
                    return new TradePair(Currency.of(split[0]), Currency.of(split[1]));
                }
            } else if (pairType.getValue().equals(CryptoCurrency.class)) {
                if (Currency.of(split[1]) == Currency.NULL_CRYPTO_CURRENCY) {
                    throw new CurrencyNotFoundException(CurrencyType.CRYPTO, split[1]);
                } else {
                    return new TradePair(Currency.of(split[0]), Currency.of(split[1]));
                }
            } else {
                logger.error("bad value for second member of pairType - must be one of CryptoCurrency.class, " +
                        "FiatCurrency.class, or null but was: " + pairType.getValue());
                throw new IllegalArgumentException("bad value for second member of pairType - must be one of " +
                        "CryptoCurrency.class, FiatCurrency.class, or null but was: " + pairType.getValue());
            }

        }
    }

    public Currency getBaseCurrency() {
        return baseCurrency;
    }

    public Currency getCounterCurrency() {
        return counterCurrency;
    }

    public String toString(@NotNull Character separator) {

        if (separator.equals('_')) {
            out.println(baseCurrency.code);
            out.println("_");
            out.println(counterCurrency.code);
            out.println("baseCurrency image " + baseCurrency.getImage());
            out.println("counterCurrency image " + counterCurrency.getImage());
            return baseCurrency.code + "_" + counterCurrency.code;
        } else if (separator.equals('-')) {
            out.println(baseCurrency.code);
            out.println("-");
            out.println(counterCurrency.code);
            return baseCurrency.code + "-" + counterCurrency.code;
        } else if (separator.equals('/')) {
            out.println(baseCurrency.code);
            out.println("/");
            out.println(counterCurrency.code);
            out.println("baseCurrency image " + baseCurrency.getImage());
            out.println("counterCurrency image " + counterCurrency.getImage());
            return baseCurrency.code + counterCurrency.code;
        } else {
            out.println(baseCurrency.code);

            out.println(counterCurrency.code);

            if (baseCurrency.code.equals(counterCurrency.code)) {
                throw new IllegalArgumentException("baseCurrency and counterCurrency must be different");
            } else if (baseCurrency.code.isEmpty() || counterCurrency.code.isEmpty()) {
                throw new IllegalArgumentException(
                        "Currency code must be non-empty, but was: " + baseCurrency.code + " and" +
                                counterCurrency.code
                );
            }
            out.println("baseCurrency image " + baseCurrency.getImage());
            out.println("counterCurrency image " + counterCurrency.getImage());
            return counterCurrency.code + separator + baseCurrency.code;
        }
    }



    public Date getStartTime() {
        return new Date();
    }

    public static <U, T> U getInstrument(T t) {
        return (U) t;
    }

    public static <K, T> K getPair(T t) {
        return (K) t;
    }

    public String getTradePairCode() {
        return tradePairCode;
    }

    public void setTradePairCode(String tradePairCode) {
        this.tradePairCode = tradePairCode;
    }

    public Iterable<? extends Trade> getTrades() {

        return trades;
    }

    public long getOrderListId() {
        return orderListId;
    }

    public long getId() {
        return id;
    }
}