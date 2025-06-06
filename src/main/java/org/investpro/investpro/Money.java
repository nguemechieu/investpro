
package org.investpro.investpro;

import org.investpro.investpro.model.Currency;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * @author NOEL NGUEMECHIEU
 */
public interface Money {
    @Contract("_ -> new")
    @NotNull
    static Money of(BigDecimal bd) throws Exception {

        return new DefaultMoney(bd, Currency.of("USD"));


    }

    Number amount();

    Currency currency();

    Money plus(Money summand);

    Money plus(long summand);

    Money plus(double summand) throws Exception;

    Money multiply(long multiplicand);

    Money multiply(double multiplicand) throws Exception;

    Money multiply(BigDecimal multiplicand);

    Money divide(long divisor);

    Money divide(double divisor) throws Exception;

    Money divide(BigDecimal divisor);

    Money negate();

    Money abs();

    Money minus(Money subtrahend);

    Money minus(long subtrahend);

    Money minus(double subtrahend) throws Exception;

    Money multipliedBy(long multiplier);

    Money multipliedBy(double multiplier);

    Money multipliedBy(BigDecimal multiplier, MathContext mathContext);

    Money dividedBy(long divisor);

    Money dividedBy(double divisor);

    Money dividedBy(BigDecimal divisor, MathContext mathContext);

    BigDecimal toBigDecimal();

    double toDouble();

    boolean isLessThan(Money other);

    boolean isGreaterThan(Money other);

    boolean isGreaterThanOrEqualTo(Money other);

    boolean isZero();

    boolean isPositive();

    boolean isPositiveOrZero();

    boolean isNegative();

    boolean isNegativeOrZero();

    @NotNull FastMoney toFastMoney();

    boolean canEqual(Object other);
}
