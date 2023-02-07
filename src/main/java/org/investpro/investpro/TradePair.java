package org.investpro.investpro;

import javafx.util.Pair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;


public class TradePair extends Pair<Currency, Currency> {
    private final Currency baseCurrency;
    private final Currency counterCurrency;

    public TradePair(Currency baseCurrency, Currency counterCurrency) {
        super(baseCurrency, counterCurrency);
        this.baseCurrency = baseCurrency;
        this.counterCurrency = counterCurrency;
    }

//    public TradePair(String baseCurrency, String counterCurrency) {
//        super(Currency.of(baseCurrency), Currency.of(counterCurrency));
//
//        this.baseCurrency = Currency.of(baseCurrency);
//        this.counterCurrency = Currency.of(counterCurrency);
//    }

//    @Contract("_, _ -> new")
//    public static @NotNull TradePair of(String baseCurrencyCode, String counterCurrencyCode) {
//        return new TradePair(baseCurrencyCode, counterCurrencyCode);
//    }

    @Contract("_, _ -> new")
    public static @NotNull TradePair of(Currency baseCurrency, Currency counterCurrency) {
        return new TradePair(baseCurrency, counterCurrency);
    }

//    @Contract("_ -> new")
//    public static @NotNull TradePair of(@NotNull Pair<Currency, Currency> currencyPair) {
//        return new TradePair(currencyPair.getKey(), currencyPair.getValue());
//    }

    public static <T extends Currency, V extends Currency> @NotNull TradePair parse(
            TradePair tradePair, @NotNull String separator, Pair<Class<T>, Class<V>> pairType)
            throws CurrencyNotFoundException {
        Objects.requireNonNull(tradePair, "tradePair must not be null");
        Objects.requireNonNull(pairType, "pairType must not be null");
        Objects.requireNonNull(pairType.getKey(), "first member of pairType must not be null");

        String[] split;


        // We don't know where to split so try the most logical thing (after 3 characters). We could
        // extend this by checking that the substring is indeed a real currency and if not try 4
        // characters.
        split = new String[]{tradePair.baseCurrency.code, tradePair.counterCurrency.code};


        //split = tradePair.baseCurrency+separator+tradePair.counterCurrency;


        if (pairType.getKey().equals(FiatCurrency.class)) {
            // tradePair must be (fiat, something)
            if (Currency.ofFiat(split[0]) == Currency.NULL_FIAT_CURRENCY) {
                throw new CurrencyNotFoundException(CurrencyType.FIAT, split[0]);
            }

            if (pairType.getValue() == null) {
                // The counter currency is not specified, so try both (fiat first)
                if (Currency.ofFiat(split[1]) != Currency.NULL_FIAT_CURRENCY) {
                    return new TradePair(Currency.ofFiat(split[0]), Currency.ofFiat(split[1]));
                } else if (Currency.ofCrypto(split[1]) != Currency.NULL_CRYPTO_CURRENCY) {
                    return new TradePair(Currency.ofFiat(split[0]), Currency.ofCrypto(split[1]));
                } else {
                    // TODO throw exception instead?
                    return TradePair.of(Currency.NULL_FIAT_CURRENCY, Currency.NULL_CRYPTO_CURRENCY);
                }
            } else if (pairType.getValue().equals(FiatCurrency.class)) {
                if (Currency.ofFiat(split[1]) == Currency.NULL_FIAT_CURRENCY) {
                    throw new CurrencyNotFoundException(CurrencyType.FIAT, split[1]);
                } else {
                    return new TradePair(Currency.ofFiat(split[0]), Currency.ofFiat(split[1]));
                }
            } else if (pairType.getValue().equals(CryptoCurrency.class)) {
                if (Currency.ofCrypto(split[1]) == Currency.NULL_CRYPTO_CURRENCY) {
                    throw new CurrencyNotFoundException(CurrencyType.CRYPTO, split[1]);
                } else {
                    return new TradePair(Currency.ofFiat(split[0]), Currency.ofCrypto(split[1]));
                }
            } else {
                Log.error("bad value for second member of pairType - must be one of CryptoCurrency.class, " +
                        "FiatCurrency.class, or null but was: " + pairType.getValue());
                throw new IllegalArgumentException("bad value for second member of pairType - must be one of " +
                        "CryptoCurrency.class, FiatCurrency.class, or null but was: " + pairType.getValue());
            }
        } else {
            // tradePair must be (crypto, something)
            if (Currency.ofCrypto(split[0]) == Currency.NULL_CRYPTO_CURRENCY) {
                throw new CurrencyNotFoundException(CurrencyType.CRYPTO, split[0]);
            }

            if (pairType.getValue() == null) {
                // The counter currency is not specified, so try both (fiat first)
                if (Currency.ofFiat(split[1]) != Currency.NULL_FIAT_CURRENCY) {
                    return new TradePair(Currency.ofCrypto(split[0]), Currency.ofFiat(split[1]));
                } else if (Currency.ofCrypto(split[1]) != Currency.NULL_CRYPTO_CURRENCY) {
                    return new TradePair(Currency.ofCrypto(split[0]), Currency.ofCrypto(split[1]));
                } else {
                    // TODO throw exception instead?
                    return TradePair.of(Currency.NULL_CRYPTO_CURRENCY, Currency.NULL_CRYPTO_CURRENCY);
                }
            } else if (pairType.getValue().equals(FiatCurrency.class)) {
                if (Currency.ofFiat(split[1]) == Currency.NULL_FIAT_CURRENCY) {
                    throw new CurrencyNotFoundException(CurrencyType.FIAT, split[1]);
                } else {
                    return new TradePair(Currency.ofCrypto(split[0]), Currency.ofFiat(split[1]));
                }
            } else if (pairType.getValue().equals(CryptoCurrency.class)) {
                if (Currency.ofCrypto(split[1]) == Currency.NULL_CRYPTO_CURRENCY) {
                    throw new CurrencyNotFoundException(CurrencyType.CRYPTO, split[1]);
                } else {
                    return new TradePair(Currency.ofCrypto(split[0]), Currency.ofCrypto(split[1]));
                }
            } else {
                Log.error("bad value for second member of pairType - must be one of CryptoCurrency.class, " +
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

    @Override
    public String toString() {
        return toString('/');
    }

    /**
     * Returns the String obtained by concatenating the code of the
     * base currency with the code of the counter currency, separated
     * by the given separator. If {@code separator} is {@code null},
     * then the two codes are concatenated together without any separation.
     *
     * @param separator
     * @return
     */
    public String toString(Character separator) {
        return baseCurrency.getCode() + (separator == null ? "" : separator) + counterCurrency.getCode();
    }

    public String getFullDisplayString() {
        return baseCurrency.getFullDisplayName() + " - " + counterCurrency.getFullDisplayName();
    }

    public String[] split(String s) {

        return new String[]{s};
    }
}
