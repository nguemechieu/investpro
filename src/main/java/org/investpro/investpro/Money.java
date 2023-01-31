package org.investpro.investpro;

import java.math.BigDecimal;
import java.math.MathContext;


public interface Money {
    Number amount();

    Currency currency();

    Money plus(Money summand);

    Money plus(long summand);

    Money plus(double summand);

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
}
