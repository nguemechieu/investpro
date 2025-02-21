package org.investpro;

import javafx.util.Pair;


public class Extrema extends Pair<Double, Double> {
    public Extrema(Double min, Double max) {
        super(min, max);
    }

    public static Double getMin(Pair<Extrema, Extrema> value) {
        return value.getKey().getValue();
    }

    public static Double getMax(Pair<Extrema, Extrema> value) {

        return value.getValue().getValue();
    }
}
