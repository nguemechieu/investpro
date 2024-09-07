
package org.investpro;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.MathContext;
import java.sql.SQLException;

/**
 * @author NOEL NGUEMECHIEU
 */
public interface Money {
    @Contract("_ -> new")
    @NotNull
    static Money of(BigDecimal bd) throws SQLException, ClassNotFoundException {

        return new DefaultMoney(bd, Currency.ofFiat("USD"));


    }

    Number amount();

    Currency currency();

    Money plus(Money summand);

    Money plus(long summand);

    Money plus(double summand) throws SQLException, ClassNotFoundException;

    Money multiply(long multiplicand);

    Money multiply(double multiplicand) throws SQLException, ClassNotFoundException;

    Money multiply(BigDecimal multiplicand);

    Money divide(long divisor);

    Money divide(double divisor) throws SQLException, ClassNotFoundException;

    Money divide(BigDecimal divisor);

    Money negate();

    Money abs();

    Money minus(Money subtrahend);

    Money minus(long subtrahend);

    Money minus(double subtrahend);

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
