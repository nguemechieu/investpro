package org.investpro.indicators.marketstructure;

import org.investpro.indicators.core.IndicatorUtils;
import java.util.List;

public final class MarketStructure {
    private MarketStructure() {}

    public static List<Double> highestHigh(List<Double> high, int period) {
        List<Double> out = IndicatorUtils.nanList(high == null ? 0 : high.size());
        if (high == null) return out;
        for (int i = period - 1; i < high.size(); i++) out.set(i, IndicatorUtils.highest(high, i - period + 1, i + 1));
        return out;
    }

    public static List<Double> lowestLow(List<Double> low, int period) {
        List<Double> out = IndicatorUtils.nanList(low == null ? 0 : low.size());
        if (low == null) return out;
        for (int i = period - 1; i < low.size(); i++) out.set(i, IndicatorUtils.lowest(low, i - period + 1, i + 1));
        return out;
    }

    public static boolean crossesAbove(List<Double> a, List<Double> b) {
        int n = Math.min(a == null ? 0 : a.size(), b == null ? 0 : b.size());
        if (n < 2) return false;
        return a.get(n - 2) <= b.get(n - 2) && a.get(n - 1) > b.get(n - 1);
    }

    public static boolean crossesBelow(List<Double> a, List<Double> b) {
        int n = Math.min(a == null ? 0 : a.size(), b == null ? 0 : b.size());
        if (n < 2) return false;
        return a.get(n - 2) >= b.get(n - 2) && a.get(n - 1) < b.get(n - 1);
    }
}
