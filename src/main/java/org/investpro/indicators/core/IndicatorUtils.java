package org.investpro.indicators.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class IndicatorUtils {
    private IndicatorUtils() {}

    public static void requirePeriod(int period) {
        if (period <= 0) throw new IllegalArgumentException("period must be > 0");
    }

    public static void requireData(List<Double> values, int min) {
        if (values == null || values.size() < min) throw new IllegalArgumentException("Not enough data");
    }

    public static List<Double> nanList(int size) {
        return new ArrayList<>(Collections.nCopies(Math.max(size, 0), Double.NaN));
    }

    public static double safeDiv(double a, double b) {
        return Math.abs(b) < 1e-12 ? 0.0 : a / b;
    }

    public static double highest(List<Double> values, int fromInclusive, int toExclusive) {
        double high = -Double.MAX_VALUE;
        for (int i = Math.max(0, fromInclusive); i < Math.min(values.size(), toExclusive); i++) high = Math.max(high, values.get(i));
        return high;
    }

    public static double lowest(List<Double> values, int fromInclusive, int toExclusive) {
        double low = Double.MAX_VALUE;
        for (int i = Math.max(0, fromInclusive); i < Math.min(values.size(), toExclusive); i++) low = Math.min(low, values.get(i));
        return low;
    }
}
