package org.investpro.indicators.trend;

import org.investpro.indicators.core.IndicatorUtils;
import org.investpro.indicators.volatility.Volatility;
import java.util.List;

public final class Trend {
    private Trend() {}

    public static List<Double> adx(List<Double> high, List<Double> low, List<Double> close, int period) {
        int n = close == null ? 0 : close.size();
        List<Double> out = IndicatorUtils.nanList(n);
        if (high == null || low == null || n <= period * 2) return out;
        double[] plusDm = new double[n], minusDm = new double[n];
        for (int i = 1; i < n; i++) {
            double up = high.get(i) - high.get(i - 1);
            double down = low.get(i - 1) - low.get(i);
            plusDm[i] = up > down && up > 0 ? up : 0;
            minusDm[i] = down > up && down > 0 ? down : 0;
        }
        List<Double> tr = Volatility.trueRange(high, low, close);
        double trSum = 0, plusSum = 0, minusSum = 0;
        for (int i = 1; i <= period; i++) { trSum += tr.get(i); plusSum += plusDm[i]; minusSum += minusDm[i]; }
        double adx = 0;
        for (int i = period + 1; i < n; i++) {
            trSum = trSum - trSum / period + tr.get(i);
            plusSum = plusSum - plusSum / period + plusDm[i];
            minusSum = minusSum - minusSum / period + minusDm[i];
            double plusDi = 100 * IndicatorUtils.safeDiv(plusSum, trSum);
            double minusDi = 100 * IndicatorUtils.safeDiv(minusSum, trSum);
            double dx = 100 * IndicatorUtils.safeDiv(Math.abs(plusDi - minusDi), plusDi + minusDi);
            adx = i == period + 1 ? dx : ((adx * (period - 1)) + dx) / period;
            out.set(i, adx);
        }
        return out;
    }

    public static List<Double> parabolicSar(List<Double> high, List<Double> low, double step, double maxStep) {
        int n = high == null ? 0 : high.size();
        List<Double> sar = IndicatorUtils.nanList(n);
        if (low == null || n < 2) return sar;
        boolean uptrend = true;
        double af = step;
        double ep = high.get(0);
        double currentSar = low.get(0);
        sar.set(0, currentSar);
        for (int i = 1; i < n; i++) {
            currentSar = currentSar + af * (ep - currentSar);
            if (uptrend) {
                if (low.get(i) < currentSar) { uptrend = false; currentSar = ep; ep = low.get(i); af = step; }
                else if (high.get(i) > ep) { ep = high.get(i); af = Math.min(maxStep, af + step); }
            } else {
                if (high.get(i) > currentSar) { uptrend = true; currentSar = ep; ep = high.get(i); af = step; }
                else if (low.get(i) < ep) { ep = low.get(i); af = Math.min(maxStep, af + step); }
            }
            sar.set(i, currentSar);
        }
        return sar;
    }
}
