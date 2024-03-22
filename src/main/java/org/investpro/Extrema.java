package org.investpro;

import javafx.util.Pair;


public class Extrema<T extends Number> extends Pair<T, T> {
    public Extrema(T min, T max) {
        super(min, max);
    }

    public T getMin() {
        return getKey();
    }

    public T getMax() {
        return getValue();
    }
}
