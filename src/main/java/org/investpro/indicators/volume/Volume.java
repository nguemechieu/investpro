package org.investpro.indicators.volume;

import org.investpro.indicators.core.IndicatorUtils;
import java.util.List;

public final class Volume {
    private Volume() {}

    public static List<Double> obv(List<Double> close, List<Double> volume) {
        int n = close == null ? 0 : close.size();
        List<Double> out = IndicatorUtils.nanList(n);
        if (volume == null || n == 0) return out;
        double obv = 0.0;
        out.set(0, obv);
        for (int i = 1; i < n; i++) {
            if (close.get(i) > close.get(i - 1)) obv += volume.get(i);
            else if (close.get(i) < close.get(i - 1)) obv -= volume.get(i);
            out.set(i, obv);
        }
        return out;
    }

    public static List<Double> mfi(List<Double> high, List<Double> low, List<Double> close, List<Double> volume, int period) {
        int n = close == null ? 0 : close.size();
        List<Double> out = IndicatorUtils.nanList(n);
        if (high == null || low == null || volume == null) return out;
        double[] positive = new double[n];
        double[] negative = new double[n];
        for (int i = 1; i < n; i++) {
            double tp = (high.get(i) + low.get(i) + close.get(i)) / 3.0;
            double prevTp = (high.get(i - 1) + low.get(i - 1) + close.get(i - 1)) / 3.0;
            double flow = tp * volume.get(i);
            if (tp > prevTp) positive[i] = flow; else negative[i] = flow;
        }
        for (int i = period; i < n; i++) {
            double pos = 0, neg = 0;
            for (int j = i - period + 1; j <= i; j++) { pos += positive[j]; neg += negative[j]; }
            out.set(i, 100.0 - 100.0 / (1.0 + IndicatorUtils.safeDiv(pos, neg)));
        }
        return out;
    }

    public static List<Double> vwap(List<Double> high, List<Double> low, List<Double> close, List<Double> volume) {
        int n = close == null ? 0 : close.size();
        List<Double> out = IndicatorUtils.nanList(n);
        if (high == null || low == null || volume == null) return out;
        double cumPV = 0, cumV = 0;
        for (int i = 0; i < n; i++) {
            double tp = (high.get(i) + low.get(i) + close.get(i)) / 3.0;
            cumPV += tp * volume.get(i);
            cumV += volume.get(i);
            out.set(i, IndicatorUtils.safeDiv(cumPV, cumV));
        }
        return out;
    }
}
