package org.investpro;

import org.jetbrains.annotations.NotNull;

public class T {
    private static final double PRECISION = 0.0000000001;
    private double value;

    public T(double value) {
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = value;
    }

    public T add(@NotNull T other) {
        return new T(value + other.value);
    }

    public T subtract(@NotNull T other) {
        return new T(value - other.value);
    }

    public T multiply(@NotNull T other) {
        return new T(value * other.value);
    }

    public T divide(@NotNull T other) {
        if (other.value == 0) {
            throw new IllegalArgumentException("Division by zero");
        }
        return new T(value / other.value);

    }

    public boolean equals(@NotNull T other) {
        return Math.abs(value - other.value) < PRECISION;
    }

    public T sqrt() {
        return new T(Math.sqrt(value));
    }

    public T pow(double exponent) {
        return new T(Math.pow(value, exponent));
    }


}
