package org.investpro.investpro;

import javafx.util.Pair;

/**
 * A utility class that holds a pair of minimum and maximum values.
 */
public class Extrema extends Pair<Double, Double> {

    public Extrema(Double min, Double max) {
        super(min, max);
    }

    public Double getMin() {
        return getKey();
    }

    public Double getMax() {
        return getValue();
    }

    @Override
    public String toString() {
        return "Extrema{min=" + getMin() + ", max=" + getMax() + '}';
    }
}
