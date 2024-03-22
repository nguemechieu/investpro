package org.investpro;

import javafx.scene.control.Alert;
import javafx.util.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static java.lang.System.out;


public class TradePair extends Pair<Currency, Currency> {

    static final Logger logger = LoggerFactory.getLogger(TradePair.class);


    Currency baseCurrency;
    Currency counterCurrency;

    long id;
    private double bid;
    private double ask;

    public TradePair(Currency baseCurrency, Currency counterCurrency) throws SQLException, ClassNotFoundException {
        super(baseCurrency, counterCurrency);
        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;

        logger.debug("TradePair created: {}", this);


    }

    static {
        Currency.registerCurrencies(new ArrayList<>(Currency.CURRENCIES.values()));
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
    public static @NotNull TradePair of(@NotNull Currency baseCurrency, Currency counterCurrency) throws SQLException, ClassNotFoundException {

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
    public static @NotNull TradePair of(@NotNull Pair<Currency, Currency> currencyPair) throws SQLException, ClassNotFoundException {
        return new TradePair(currencyPair.getKey(), currencyPair.getValue());
    }

    public static <T extends Currency, V extends Currency> @NotNull TradePair parse(
            String tradePair, @NotNull String separator, Pair<Class<T>, Class<V>> pairType)
            throws CurrencyNotFoundException, SQLException, ClassNotFoundException {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(pairType, "pairType must not be null");
        Objects.requireNonNull(pairType.getKey(), "first member of pairType must not be null");

        String[] split;

        if (separator.isEmpty()) {
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
            } else

            if (pairType.getValue() == null) {
                // The counter currency is not specified, so try both (fiat first)
                if (Currency.ofFiat(split[1]) != Currency.NULL_FIAT_CURRENCY) {
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
                logger.error(STR."bad value for second member of pairType - must be one of CryptoCurrency.class, FiatCurrency.class, or null but was: \{pairType.getValue()}");
                throw new IllegalArgumentException(STR."bad value for second member of pairType - must be one of CryptoCurrency.class, FiatCurrency.class, or null but was: \{pairType.getValue()}");
            }
        } else {
            // tradePair must be (crypto, something)
            if (Currency.of(split[0]) == Currency.NULL_CRYPTO_CURRENCY) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText(
                        STR."The base currency of the trade pair must be a crypto currency, but was: \{split[0]}");
                alert.showAndWait();
                //throw new CurrencyNotFoundException(CurrencyType.CRYPTO, split[0]);
                return new TradePair(Currency.NULL_CRYPTO_CURRENCY, Currency.of(split[1]));
            } else if (pairType.getValue() == null) {

                logger.error("bad value for second member of pairType - must be one of CryptoCurrency.class,");

                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Error");
                alert.setHeaderText(null);
                alert.setContentText(
                        STR."The counter currency of the trade pair must be a fiat or crypto currency, but was: \{split[1]}");
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
                logger.error(STR."bad value for second member of pairType - must be one of CryptoCurrency.class, FiatCurrency.class, or null but was: \{pairType.getValue()}");
                throw new IllegalArgumentException(STR."bad value for second member of pairType - must be one of CryptoCurrency.class, FiatCurrency.class, or null but was: \{pairType.getValue()}");
            }

        }
    }

    public @NotNull List<String> getTradePairs() {
        List<String> tradePairs = new ArrayList<>();

        tradePairs.add(toString());

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

            return STR."\{baseCurrency.code.toUpperCase()}_\{counterCurrency.code.toUpperCase()}";
        } else if (separator.equals('-')) {

            return STR."\{baseCurrency.code.toUpperCase()}-\{counterCurrency.code.toUpperCase()}";
        } else if (separator.equals('/')) {

            return baseCurrency.code.toUpperCase() + counterCurrency.code.toUpperCase();
        } else {
            out.println(baseCurrency.code);

            out.println(counterCurrency.code);

            if (baseCurrency.code.equals(counterCurrency.code)) {
                throw new IllegalArgumentException("baseCurrency and counterCurrency must be different");
            } else if (baseCurrency.code.isEmpty() || counterCurrency.code.isEmpty()) {
                throw new IllegalArgumentException(
                        STR."Currency code must be non-empty, but was: \{baseCurrency.code} and\{counterCurrency.code}"
                );
            }

            return counterCurrency.code.toUpperCase() + separator + baseCurrency.code.toUpperCase();
        }
    }





    public long getId() {
        return id;
    }

    public String getSymbol() {

        return STR."\{baseCurrency.getSymbol()}/\{counterCurrency.getSymbol()}";
    }

    public double getBid() {

        return bid;
    }

    public void setBid(double bid) {
        this.bid = bid;
    }

    public double getAsk() {
        return ask;
    }

    public void setAsk(double ask) {
        this.ask = ask;
    }
}