package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.SQLException;
import java.util.Objects;

/**
 * A monetary amount - models some fixed amount in a given
 * currency. The amount is internally represented using a BigDecimal,
 * thus this implementation should be favored when accuracy and
 * precision are more important than speed. If speed is more
 * important, {@link FastMoney} should be used instead.
 *
 * @author NOEL NGUEMECHIEU
 */
public record DefaultMoney(BigDecimal amount, Currency currency) implements Money, Comparable<DefaultMoney> {

    public static Money of(int amount, Currency currency) {
        return of(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(long amount, Currency currency) {
        return of(BigDecimal.valueOf(amount), currency);
    }

    public static Money of(float amount, Currency currency) {
        return of(new BigDecimal(Float.valueOf(amount).toString()), currency);
    }

    public static Money of(double amount, Currency currency) {
        return of(new BigDecimal(Double.valueOf(amount).toString()), currency);
    }

    public static Money of(int amount, CurrencyType currencyType, String currencyCode) throws SQLException, ClassNotFoundException {
        return of(BigDecimal.valueOf(amount), currencyType, currencyCode);
    }

    public static Money of(long amount, CurrencyType currencyType, String currencyCode) throws SQLException, ClassNotFoundException {
        return of(BigDecimal.valueOf(amount), currencyType, currencyCode);
    }

    public static Money of(float amount, CurrencyType currencyType, String currencyCode) throws SQLException, ClassNotFoundException {
        return of(new BigDecimal(Float.valueOf(amount).toString()), currencyType, currencyCode);
    }

    public static Money of(double amount, CurrencyType currencyType, String currencyCode) throws SQLException, ClassNotFoundException {
        return of(new BigDecimal(Double.valueOf(amount).toString()), currencyType, currencyCode);
    }

    public static Money of(String amount, CurrencyType currencyType, String currencyCode) throws SQLException, ClassNotFoundException {
        return of(new BigDecimal(amount), currencyType, currencyCode);
    }

    public static Money of(BigDecimal amount, CurrencyType currencyType, String currencyCode) throws SQLException, ClassNotFoundException {
        return switch (currencyType) {
            case FIAT -> new DefaultMoney(amount, Currency.ofFiat(currencyCode));
            case CRYPTO -> new DefaultMoney(amount, Currency.ofCrypto(currencyCode));
            default -> throw new IllegalArgumentException("unknown currency type: %s".formatted(currencyType));
        };
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new DefaultMoney(amount, currency);
    }


    public static Money ofFiat(BigDecimal amount, String currencyCode) throws SQLException, ClassNotFoundException {
        return of(amount, CurrencyType.FIAT, currencyCode);
    }

    @Contract("_, _ -> new")
    public static @NotNull Money ofFiat(BigDecimal amount, Currency currency) {
        return of(amount, currency);
    }

    public static Money ofFiat(String amount, Currency currency) {
        return of(new BigDecimal(amount), currency);
    }


    public static Money ofCrypto(String amount, Currency currency) {
        return of(new BigDecimal(amount), currency);
    }

    public Money plus(DefaultMoney defaultMoney) {
        checkCurrenciesEqual(defaultMoney);
        return new DefaultMoney(this.amount.add(defaultMoney.amount), currency);
    }

    public Money plus(int amount) {
        return new DefaultMoney(this.amount.add(BigDecimal.valueOf(amount)), currency);
    }

    @Override
    public Money plus(long amount) {
        return new DefaultMoney(this.amount.add(BigDecimal.valueOf(amount)), currency);
    }

    public Money plus(float amount) {
        return new DefaultMoney(this.amount.add(BigDecimal.valueOf(amount)), currency);
    }

    @Override
    public Money plus(double amount) {
        return new DefaultMoney(this.amount.add(BigDecimal.valueOf(amount)), currency);
    }

    @Override
    public Money multiply(long multiplicand) {
        return null;
    }

    @Override
    public Money multiply(double multiplicand) {
        return null;
    }

    @Override
    public Money multiply(BigDecimal multiplicand) {
        return null;
    }

    @Override
    public Money divide(long divisor) {
        return null;
    }

    @Override
    public Money divide(double divisor) {
        return null;
    }

    @Override
    public Money divide(BigDecimal divisor) {
        return null;
    }

    public Money plus(BigDecimal amount) {
        return new DefaultMoney(this.amount.add(amount), currency);
    }

    @Override
    public Money plus(Money summand) {
        return this.plus(summand.toBigDecimal());
    }

    public Money minus(DefaultMoney defaultMoney) {
        checkCurrenciesEqual(defaultMoney);
        return new DefaultMoney(this.amount.subtract(defaultMoney.amount), currency);
    }

    public Money minus(int amount) {
        return new DefaultMoney(this.amount.subtract(BigDecimal.valueOf(amount)), currency);
    }

    @Override
    public Money minus(long amount) {
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

    @Override
    public Money minus(Money subtrahend) {
        return minus(subtrahend.toBigDecimal());
    }

    public Money minus(BigDecimal amount) {
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
            throw new IllegalArgumentException("Unknown money type: %s".formatted(other.getClass()));
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
            throw new IllegalArgumentException("Unknown money type: %s".formatted(other.getClass()));
        }
    }

    @Override
    public boolean isZero() {
        return amount.compareTo(BigDecimal.ZERO) == 0;
    }

    @Override
    public boolean isPositive() {
        return false;
    }

    @Override
    public boolean isPositiveOrZero() {
        return false;
    }

    @Override
    public boolean isNegative() {
        return false;
    }

    @Override
    public boolean isNegativeOrZero() {
        return false;
    }

    @Override
    public @NotNull FastMoney toFastMoney() {

        return new FastMoney((long) amount.doubleValue(), currency);


    }

    @Override
    public boolean canEqual(Object other) {
        return false;
    }

    @Override
    public boolean isLessThan(Money other) {
        if (other instanceof DefaultMoney money) {
            checkCurrenciesEqual(money);
            return this.amount().compareTo(money.amount) < 0;
        } else if (other instanceof FastMoney money) {
            return this.amount().compareTo(money.toBigDecimal()) < 0;
        } else {
            throw new IllegalArgumentException("Unknown money type: %s".formatted(other.getClass()));
        }
    }

    @Override
    public Money negate() {
        return new DefaultMoney(amount.negate(), currency);
    }

    @Override
    public Money abs() {
        return new DefaultMoney(amount.abs(), currency);
    }

    @Override
    public Money multipliedBy(long multiplier) {
        return new DefaultMoney(amount.multiply(BigDecimal.valueOf(multiplier)), currency);
    }

    @Override
    public Money multipliedBy(double multiplier) {
        return new DefaultMoney(amount.multiply(BigDecimal.valueOf(multiplier)), currency);
    }

    @Override
    public Money multipliedBy(BigDecimal multiplier, MathContext mathContext) {
        return new DefaultMoney(amount.multiply(multiplier, mathContext), currency);
    }

    @Override
    public Money dividedBy(long divisor) {
        return new DefaultMoney(amount.divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP), currency);
    }

    @Override
    public Money dividedBy(double divisor) {
        return new DefaultMoney(amount.divide(BigDecimal.valueOf(divisor), RoundingMode.HALF_UP), currency);
    }

    @Override
    public Money dividedBy(BigDecimal divisor, MathContext mathContext) {
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

    private void checkCurrenciesEqual(DefaultMoney defaultMoney) {
        if (!currency.equals(defaultMoney.currency)) {
            throw new IllegalArgumentException("currencies are not equal: first currency: %s second currency: %s".formatted(currency, defaultMoney.currency));
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
    public String toString() {
        if (Objects.requireNonNull(currency.getCurrencyType()) == CurrencyType.FIAT) {
            return DefaultMoneyFormatter.DEFAULT_FIAT_FORMATTER.format(this);
        }
        return DefaultMoneyFormatter.DEFAULT_CRYPTO_FORMATTER.format(this);
    }
}
