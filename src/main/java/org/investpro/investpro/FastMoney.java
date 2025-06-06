package org.investpro.investpro;

import lombok.Getter;

import org.investpro.investpro.model.Currency;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.MathContext;
import java.util.Objects;

/**
 * A monetary amount - models some fixed amount in a given currency.
 * The amount is internally represented using a long, thus this
 * implementation should be favored when speed is more important
 * than accuracy or precision.
 * <p>
 * Based heavily on: <a href="https://github.com/mikvor/money-conversion">mikvor/money-conversion</a>
 * <p>
 * It is important to note that when obtaining a FastMoney instance using
 * one of the static {@code of(...)} methods, a DefaultMoney instance
 * <em>can</em> be returned, because the construction of the FastMoney
 * can fail.
 */
public final class FastMoney implements Money, Comparable<FastMoney> {

    private double amount;
    @Getter
    private int precision;
    private Currency currency;

    public FastMoney(long amount, Currency currency) {
        this(amount, currency, currency.getFractionalDigits());
        this.amount = amount;
        this.currency = currency;
    }

    private FastMoney(double amount, Currency currency, int precision) {
        Objects.requireNonNull(currency, "currency must not be null");
        this.amount = amount;
        this.precision = precision;
        this.currency = currency;
    }

    public static @NotNull Money of(long amount, final Currency currency) {
        Objects.requireNonNull(currency, "currency must not be null");
        return of(amount, currency, currency.getFractionalDigits());
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Money of(long amount, final Currency currency, int precision) {
        amount *= (long) Math.pow(10, precision);
        return new FastMoney(amount, currency, precision);
    }

    public static @NotNull Money of(final double amount, final @NotNull Currency currency) throws Exception {
        return fromDouble(amount, Utils.MAX_ALLOWED_PRECISION, currency.getCode(), CurrencyType.valueOf(currency.getCurrencyType()));
    }

    public static @NotNull Money of(final double amount, final @NotNull Currency currency, int precision) throws Exception {
        return fromDouble(amount, precision, currency.getCode(), CurrencyType.valueOf(currency.getCurrencyType()));
    }

    @Contract("_, _, _ -> new")
    public static @NotNull Money ofFiat(long amount, final String currencyCode, int precision) {
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Currency currency = Currency.of(currencyCode);
        amount *= (long) Math.pow(10, precision);
        return new FastMoney(amount, currency, precision);
    }

    public static @NotNull Money ofFiat(final double amount, final String currencyCode, int precision) {
        Currency currency = Currency.of(currencyCode); // Ensure this returns a valid Currency object
        if (currency == null) {

            throw new IllegalArgumentException("Invalid currency code: " + currencyCode);
        }

        return new FastMoney(amount, currency, precision);
    }


    @Contract("_, _, _ -> new")
    public static @NotNull Money ofCrypto(double amount, final String currencyCode, int precision) {
        Currency currency = Currency.of(currencyCode);
        amount *= Math.pow(10, precision);
        return new FastMoney(amount, currency, precision);
    }


    private static @NotNull Money fromDouble(final double value, final int precision, final String currencyCode,
                                             final CurrencyType currencyType) {
        Objects.requireNonNull(currencyCode, "currencyCode must not be null");
        Objects.requireNonNull(currencyType, "currencyType must not be null");
        Utils.checkPrecision(precision);
        final FastMoney direct;
        // attempt direct
        direct = fromDoubleNoFallback(value, precision, Currency.of(currencyCode));

        if (direct != null) {
            return direct;
        }

        return DefaultMoney.of(value, Currency.of(currencyCode));
    }

    private static @Nullable FastMoney fromDoubleNoFallback(final double value, final int precision, final Currency currency) {
        // attempt direct
        final FastMoney direct = fromDouble0(value, precision, currency);
        if (direct != null) {
            return direct;
        }
        // ulp down
        final FastMoney down = fromDouble0(Math.nextAfter(value, -Double.MAX_VALUE), precision, currency);
        if (down != null) {
            return down;
        }
        // ulp up
        return fromDouble0(Math.nextAfter(value, Double.MAX_VALUE), precision, currency);
    }

    private static @Nullable FastMoney fromDouble0(final double value, final int precision, final Currency currency) {
        final double multiplied = value * Utils.MULTIPLIERS[precision];
        final long converted = (long) multiplied;
        if (multiplied == converted) { // here is an implicit conversion from double to long
            return new FastMoney(converted, currency, precision).normalize();
        }

        return null;
    }

    private static @NotNull Money fromBigDecimal(final @NotNull BigDecimal value, Currency currency) {
        final BigDecimal cleaned = value.stripTrailingZeros();
        // try to convert to double using fixed precision = 3, which will cover most currencies
        // it is required to get rid of rounding issues

        final Money res = fromDoubleNoFallback(value.doubleValue(), currency.getFractionalDigits(), currency);
        if (res != null) {
            return res;
        }

        final int scale = cleaned.scale();
        if (scale > Utils.MAX_ALLOWED_PRECISION || scale < -Utils.MAX_ALLOWED_PRECISION) {
            return new DefaultMoney(cleaned, currency);
        }
        // we may not fit into the Long, but we should try
        // this value may be truncated!
        final BigInteger unscaledBigInt = cleaned.unscaledValue();
        final long unscaledAmount = unscaledBigInt.longValue();
        // check that it was not
        if (!BigInteger.valueOf(unscaledAmount).equals(unscaledBigInt)) {
            return new DefaultMoney(cleaned, currency);
        }
        // The Scale could be negative here - we must multiply in that case
        if (scale >= 0) {
            return new FastMoney(unscaledAmount, currency, scale);
        }
        // multiply by 10, and each time check that sign did not change
        // scale is negative
        long amount = unscaledAmount;
        for (int i = 0; i < -scale; ++i) {
            amount *= 10;
            if (amount >= Utils.MAX_LONG_DIVIDED_BY_10) {
                return new DefaultMoney(value, currency);
            }
        }

        return new FastMoney(amount, currency, 8);
    }

    @Override
    public Long amount() {
        return (long) amount;
    }

    @Override
    public Currency currency() {
        return currency;
    }

    private Money plus(@NotNull FastMoney other) {
        double result;
        int precision = currency.getFractionalDigits();
        int precisionOther = other.currency.getFractionalDigits();
        if (precision == precisionOther) {
            result = amount + other.amount;
        } else if (precision > precisionOther) {
            long multiplier = Utils.MULTIPLIERS[precision - precisionOther];
            double multi = other.amount * multiplier;
            // overflow check, alternative is double multiplication and compare with Long.MAX_VALUE.
            if (multi / multiplier != other.amount) {
                return other.plus(new DefaultMoney(toBigDecimal(), currency));
            }
            result = amount + multi;
        } else {
            long multiplier = Utils.MULTIPLIERS[precisionOther - precision];
            double multi = amount * multiplier;
            if (multi / multiplier != amount) {
                return other.plus(new DefaultMoney(toBigDecimal(), currency));
            }
            result = multi + other.amount;
            precision = precisionOther;
        }
        return new FastMoney(result, currency, precision);
    }

    @Override
    public Money plus(Money summand) {
        if (summand instanceof DefaultMoney) {
            return new DefaultMoney(toBigDecimal().add(summand.toBigDecimal()), currency);
        } else if (summand instanceof FastMoney) {
            return plus((FastMoney) summand);
        } else {
            throw new IllegalArgumentException("unknown money type: " + summand.getClass());
        }
    }

    @Override
    public Money plus(long summand) {
        return plus(FastMoney.of(summand, currency));
    }

    @Override
    public Money plus(double summand) throws Exception {
        return plus(FastMoney.of(summand, currency));
    }

    @Override
    public Money minus(Money subtrahend) {
        if (subtrahend instanceof DefaultMoney) {
            return new DefaultMoney(toBigDecimal().subtract(subtrahend.toBigDecimal()), currency);
        } else if (subtrahend instanceof FastMoney) {
            return plus(subtrahend.negate());
        } else {
            throw new IllegalArgumentException("Unknown money type: " + subtrahend.getClass());
        }
    }

    @Override
    public Money minus(long subtrahend) {

        return minus(FastMoney.of(subtrahend, currency));
    }

    @Override
    public Money minus(double subtrahend) throws Exception {

        return minus(FastMoney.of(subtrahend, currency));
    }

    @Override
    public Money multipliedBy(long multiplier) {

        return new FastMoney(amount * multiplier, currency, precision).normalize();
    }

    @Override
    public Money multipliedBy(double multiplier) {

        return new FastMoney(amount * multiplier, currency, precision).normalize();
    }

    @Override
    public Money multipliedBy(@NotNull BigDecimal multiplier, MathContext mathContext) {

        return new FastMoney(amount * multiplier.doubleValue(), currency, precision).normalize();
    }

    @Override
    public Money dividedBy(long divisor) {

        return new FastMoney(amount / divisor, currency, precision).normalize();
    }

    @Override
    public Money dividedBy(double divisor) {

        return new FastMoney(amount / divisor, currency, precision).normalize();
    }

    @Contract(pure = true)
    @Override
    public @Nullable Money dividedBy(BigDecimal divisor, MathContext mathContext) {
        return null;
    }

    @Override
    public Money multiply(long multiplicand) {
        return new FastMoney(amount * multiplicand, currency, precision).normalize();
    }

    @Override
    public @NotNull Money multiply(double multiplicand) {
        return fromDouble(amount * multiplicand / Utils.MULTIPLIERS[precision], precision, currency.getCode(), CurrencyType.valueOf(currency.getCurrencyType()));
    }

    @Override
    public @NotNull Money multiply(BigDecimal multiplicand) {
        return fromBigDecimal(toBigDecimal().multiply(multiplicand, MathContext.DECIMAL128), currency);
    }

    @Override
    public Money divide(long divisor) {
        return new FastMoney(amount / divisor, currency, precision).normalize();
    }

    @Override
    public @NotNull Money divide(double divisor) {
        return fromDouble(amount / divisor / Utils.MULTIPLIERS[precision], precision, currency.getCode(), CurrencyType.valueOf(currency.getCurrencyType()));
    }

    @Override
    public @NotNull Money divide(BigDecimal divisor) {
        return fromBigDecimal(toBigDecimal().divide(divisor, MathContext.DECIMAL128), currency);
    }

    @Override
    public Money negate() {
        return new FastMoney(-amount, currency, precision).normalize();
    }

    @Contract(pure = true)
    @Override
    public @Nullable Money abs() {

        return new FastMoney(Math.abs(amount), currency, precision).normalize();
    }

    @Contract(" -> new")
    @Override
    public @NotNull BigDecimal toBigDecimal() {
        return new BigDecimal(BigInteger.valueOf((long) amount), precision);
    }

    @Override
    public double toDouble() {
        return 0;
    }

    @Override
    public boolean isLessThan(Money other) {
        return false;
    }

    @Override
    public boolean isGreaterThan(Money other) {
        return false;
    }

    @Override
    public boolean isGreaterThanOrEqualTo(Money other) {
        return false;
    }

    @Override
    public boolean isZero() {
        return amount == 0L;
    }

    @Override
    public boolean isPositive() {
        return amount > 0L;
    }

    @Override
    public boolean isPositiveOrZero() {
        return amount >= 0L;
    }

    @Override
    public boolean isNegative() {
        return amount < 0L;
    }

    @Override
    public boolean isNegativeOrZero() {
        return amount <= 0L;
    }

    @Override
    public @NotNull FastMoney toFastMoney() {
        return this;
    }

    @Override
    public boolean canEqual(Object other) {
        return other instanceof FastMoney;
    }

    @Override
    public int compareTo(@NotNull FastMoney other) {
        if (precision == other.precision) {
            return Long.compare((long) amount, (long) other.amount);
        } else if (precision > other.precision) {
            return Long.compare((long) amount, (long) (other.amount * Utils.MULTIPLIERS[precision - other.precision]));
        } else {
            return Long.compare((long) (amount * Utils.MULTIPLIERS[other.precision - precision]), (long) other.amount);
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FastMoney fastMoney = (FastMoney) o;
        return amount == fastMoney.amount && precision == fastMoney.precision && currency.equals(fastMoney.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount, precision, currency);
    }

    @Override
    public @NotNull String toString() {
        return "%s %s".formatted(toBigDecimal().toPlainString(), currency.getCode());
    }

    private FastMoney normalize() {
        double truncated = truncate();
        return truncated != amount ? new FastMoney(truncated, currency, precision) : this;
    }

    private double truncate() {
        double truncatedAmount = amount;
        while (precision > currency.getFractionalDigits()) {
            truncatedAmount /= 10;
            precision--;
        }
        return truncatedAmount;
    }
}
