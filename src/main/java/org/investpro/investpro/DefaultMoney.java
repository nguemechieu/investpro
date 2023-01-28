package org.investpro.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;


public record DefaultMoney(BigDecimal amount, Currency currency) implements Money, Comparable<DefaultMoney> {
    public static final Money NULL_MONEY = DefaultMoney.ofFiat(BigDecimal.ZERO, Currency.NULL_FIAT_CURRENCY);

    @Contract("_, _ -> new")
    public static @NotNull Money of(int amount, Currency currency) {
        return of(BigDecimal.valueOf(amount), currency);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money of(long amount, Currency currency) {
        return of(BigDecimal.valueOf(amount), currency);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money of(float amount, Currency currency) {
        return of(new BigDecimal(Float.valueOf(amount).toString()), currency);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money of(double amount, Currency currency) {
        return of(new BigDecimal(Double.valueOf(amount).toString()), currency);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Money of(int amount, CurrencyType currencyType, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyType, currencyCode);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Money of(long amount, CurrencyType currencyType, String currencyCode) {
        return of(BigDecimal.valueOf(amount), currencyType, currencyCode);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Money of(float amount, CurrencyType currencyType, String currencyCode) {
        return of(new BigDecimal(Float.valueOf(amount).toString()), currencyType, currencyCode);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Money of(double amount, CurrencyType currencyType, String currencyCode) {
        return of(new BigDecimal(Double.valueOf(amount).toString()), currencyType, currencyCode);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Money of(String amount, CurrencyType currencyType, String currencyCode) {
        return of(new BigDecimal(amount), currencyType, currencyCode);
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Money of(BigDecimal amount, @NotNull CurrencyType currencyType, String currencyCode) {
        return switch (currencyType) {
            case FIAT -> new DefaultMoney(amount, Currency.ofFiat(currencyCode));
            case CRYPTO -> new DefaultMoney(amount, Currency.ofCrypto(currencyCode));
            default -> throw new IllegalArgumentException("unknown currency type: " + currencyType);
        };
    }

    @Contract("_, _ -> new")
    public static @NotNull Money of(BigDecimal amount, Currency currency) {
        return new DefaultMoney(amount, currency);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(int amount, String currencyCode) {
        return of(amount, CurrencyType.FIAT, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(long amount, String currencyCode) {
        return of(amount, CurrencyType.FIAT, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(float amount, String currencyCode) {
        return of(amount, CurrencyType.FIAT, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(double amount, String currencyCode) {
        return of(amount, CurrencyType.FIAT, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(String amount, String currencyCode) {
        return of(new BigDecimal(amount), CurrencyType.FIAT, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(BigDecimal amount, String currencyCode) {
        return of(amount, CurrencyType.FIAT, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(BigDecimal amount, Currency currency) {
        return of(amount, currency);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(String amount, Currency currency) {
        return of(new BigDecimal(amount), currency);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofCrypto(int amount, String currencyCode) {
        return of(amount, CurrencyType.CRYPTO, currencyCode);
    }

    public static Money ofCrypto(long amount, String currencyCode) {
        return of(amount, CurrencyType.CRYPTO, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofCrypto(float amount, String currencyCode) {
        return of(amount, CurrencyType.CRYPTO, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofCrypto(double amount, String currencyCode) {
        return of(amount, CurrencyType.CRYPTO, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofCrypto(String amount, String currencyCode) {
        return of(new BigDecimal(amount), CurrencyType.CRYPTO, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofCrypto(BigDecimal amount, String currencyCode) {
        return of(amount, CurrencyType.CRYPTO, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofCrypto(BigDecimal amount, Currency currency) {
        return of(amount, currency);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofCrypto(String amount, Currency currency) {
        return of(new BigDecimal(amount), currency);
    }

    @Contract("_ -> new")
    public @NotNull Money plus(DefaultMoney defaultMoney) {
        checkCurrenciesEqual(defaultMoney);
        return new DefaultMoney(this.amount.add(defaultMoney.amount), currency);
    }

    @Contract("_ -> new")
    public @NotNull Money plus(int amount) {
        return new DefaultMoney(this.amount.add(BigDecimal.valueOf(amount)), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money plus(long amount) {
        return new DefaultMoney(this.amount.add(BigDecimal.valueOf(amount)), currency);
    }

    @Contract("_ -> new")
    public @NotNull Money plus(float amount) {
        return new DefaultMoney(this.amount.add(BigDecimal.valueOf(amount)), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money plus(double amount) {
        return new DefaultMoney(this.amount.add(BigDecimal.valueOf(amount)), currency);
    }

    @Contract("_ -> new")
    public @NotNull Money plus(BigDecimal amount) {
        return new DefaultMoney(this.amount.add(amount), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money plus(@NotNull Money summand) {
        return this.plus(summand.toBigDecimal());
    }

    @Contract("_ -> new")
    public @NotNull Money minus(DefaultMoney defaultMoney) {
        checkCurrenciesEqual(defaultMoney);
        return new DefaultMoney(this.amount.subtract(defaultMoney.amount), currency);
    }

    @Contract("_ -> new")
    public @NotNull Money minus(int amount) {
        return new DefaultMoney(this.amount.subtract(BigDecimal.valueOf(amount)), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money minus(long amount) {
        return new DefaultMoney(this.amount.subtract(BigDecimal.valueOf(amount)), currency);
    }

    @Contract("_ -> new")
    public @NotNull Money minus(float amount) {
        return new DefaultMoney(this.amount.subtract(BigDecimal.valueOf(amount)), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money minus(double amount) {
        return new DefaultMoney(this.amount.subtract(BigDecimal.valueOf(amount)), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money minus(@NotNull Money subtrahend) {
        return minus(subtrahend.toBigDecimal());
    }

    @Contract("_ -> new")
    public @NotNull Money minus(BigDecimal amount) {
        return new DefaultMoney(this.amount.subtract(amount), currency);
    }

    @Override
    public boolean isGreaterThan(Money other) {
        if (other instanceof DefaultMoney money) {
            checkCurrenciesEqual(money);
            return amount.compareTo(money.amount) > 0;
        } else if (other instanceof FastMoney money) {
            return amount.compareTo(money.toBigDecimal()) > 0;
        } else {
            throw new IllegalArgumentException("Unknown money type: " + other.getClass());
        }
    }

    @Override
    public boolean isGreaterThanOrEqualTo(Money other) {
        if (other instanceof DefaultMoney money) {
            checkCurrenciesEqual(money);
            return amount.compareTo(money.amount) >= 0;
        } else if (other instanceof FastMoney money) {
            return amount.compareTo(money.toBigDecimal()) >= 0;
        } else {
            throw new IllegalArgumentException("Unknown money type: " + other.getClass());
        }
    }

    @Override
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public boolean isLessThan(Money other) {
        if (other instanceof DefaultMoney) {
            DefaultMoney money = (DefaultMoney) other;
            checkCurrenciesEqual(money);
            return this.amount().compareTo(money.amount) < 0;
        } else if (other instanceof FastMoney) {
            FastMoney money = (FastMoney) other;
            return this.amount().compareTo(money.toBigDecimal()) < 0;
        } else {
            throw new IllegalArgumentException("Unknown money type: " + other.getClass());
        }
    }

    @Contract(" -> new")
    @Override
    public @NotNull Money negate() {
        return new DefaultMoney(amount.negate(), currency);
    }

    @Contract(" -> new")
    @Override
    public @NotNull Money abs() {
        return new DefaultMoney(amount.abs(), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money multipliedBy(long multiplier) {
        return new DefaultMoney(amount.multiply(BigDecimal.valueOf(multiplier)), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money multipliedBy(double multiplier) {
        return new DefaultMoney(amount.multiply(BigDecimal.valueOf(multiplier)), currency);
    }

    @Contract("_, _ -> new")
    @Override
    public @NotNull Money multipliedBy(BigDecimal multiplier, MathContext mathContext) {
        return new DefaultMoney(amount.multiply(multiplier, mathContext), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money dividedBy(long divisor) {
        return new DefaultMoney(amount.divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP), currency);
    }

    @Contract("_ -> new")
    @Override
    public @NotNull Money dividedBy(double divisor) {
        return new DefaultMoney(amount.divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP), currency);
    }

    @Contract("_, _ -> new")
    @Override
    public @NotNull Money dividedBy(BigDecimal divisor, MathContext mathContext) {
        return new DefaultMoney(amount.divide(divisor, mathContext), currency);
    }

    @Override
    public double toDouble() {
        return amount.doubleValue();
    }

    @Override
    public BigDecimal toBigDecimal() {
        return amount;
    }

    private void checkCurrenciesEqual(@NotNull DefaultMoney defaultMoney) {
        if (!currency.equals(defaultMoney.currency)) {
            throw new IllegalArgumentException("currencies are not equal: first currency: "
                    + currency + " second currency: " + defaultMoney.currency);
        }
    }

    @Override
    public int compareTo(@NotNull DefaultMoney other) {
        // TODO is this really the behavior we want?
        checkCurrenciesEqual(other);

        return amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || object.getClass() != getClass()) {
            return false;
        }

        if (object == this) {
            return true;
        }

        DefaultMoney other = (DefaultMoney) object;

        return amount.compareTo(other.amount) == 0 && currency == other.currency;
    }

    @Override
    public int hashCode() {
        return amount.hashCode() ^ currency.hashCode();
    }

    @Override
    public @NotNull String toString() {
        return switch (currency.getCurrencyType()) {
            case FIAT -> DefaultMoneyFormatter.DEFAULT_FIAT_FORMATTER.format(this);
            case CRYPTO, NULL, default -> DefaultMoneyFormatter.DEFAULT_CRYPTO_FORMATTER.format(this);
        };
    }
}
