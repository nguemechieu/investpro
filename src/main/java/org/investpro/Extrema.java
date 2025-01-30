package org.investpro;

import javafx.util.Pair;


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
}
