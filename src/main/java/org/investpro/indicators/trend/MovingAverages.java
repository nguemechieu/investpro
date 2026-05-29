package org.investpro.indicators.trend;

import org.investpro.indicators.core.IndicatorUtils;
import java.util.List;

public final class MovingAverages {
    private MovingAverages() {}

    public static List<Double> sma(List<Double> close, int period) {
        IndicatorUtils.requirePeriod(period);
        List<Double> out = IndicatorUtils.nanList(close == null ? 0 : close.size());
        if (close == null) return out;
        double sum = 0.0;
        for (int i = 0; i < close.size(); i++) {
            sum += close.get(i);
            if (i >= period) sum -= close.get(i - period);
            if (i >= period - 1) out.set(i, sum / period);
        }
        return out;
    }

    public static List<Double> ema(List<Double> close, int period) {
        IndicatorUtils.requirePeriod(period);
        List<Double> out = IndicatorUtils.nanList(close == null ? 0 : close.size());
        if (close == null || close.isEmpty()) return out;
        double k = 2.0 / (period + 1.0);
        double ema = close.get(0);
        out.set(0, ema);
        for (int i = 1; i < close.size(); i++) {
            ema = close.get(i) * k + ema * (1.0 - k);
            out.set(i, ema);
        }
        return out;
    }

    public static List<Double> wma(List<Double> close, int period) {
        IndicatorUtils.requirePeriod(period);
        List<Double> out = IndicatorUtils.nanList(close == null ? 0 : close.size());
        if (close == null) return out;
        double denom = period * (period + 1) / 2.0;
        for (int i = period - 1; i < close.size(); i++) {
            double sum = 0.0;
            for (int j = 0; j < period; j++) sum += close.get(i - j) * (period - j);
            out.set(i, sum / denom);
        }
        return out;
    }

    public static List<Double> hma(List<Double> close, int period) {
        IndicatorUtils.requirePeriod(period);
        List<Double> half = wma(close, Math.max(1, period / 2));
        List<Double> full = wma(close, period);
        List<Double> diff = IndicatorUtils.nanList(close == null ? 0 : close.size());
        for (int i = 0; i < diff.size(); i++) diff.set(i, 2 * half.get(i) - full.get(i));
        return wma(diff, Math.max(1, (int) Math.sqrt(period)));
    }
}
