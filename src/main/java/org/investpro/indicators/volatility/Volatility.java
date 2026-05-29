package org.investpro.indicators.volatility;

import org.investpro.indicators.core.IndicatorUtils;
import org.investpro.indicators.trend.MovingAverages;
import java.util.List;

public final class Volatility {
    private Volatility() {}

    public static List<Double> trueRange(List<Double> high, List<Double> low, List<Double> close) {
        int n = close == null ? 0 : close.size();
        List<Double> out = IndicatorUtils.nanList(n);
        if (high == null || low == null || close == null || n == 0) return out;
        out.set(0, high.get(0) - low.get(0));
        for (int i = 1; i < n; i++) {
            double hl = high.get(i) - low.get(i);
            double hc = Math.abs(high.get(i) - close.get(i - 1));
            double lc = Math.abs(low.get(i) - close.get(i - 1));
            out.set(i, Math.max(hl, Math.max(hc, lc)));
        }
        return out;
    }

    public static List<Double> atr(List<Double> high, List<Double> low, List<Double> close, int period) {
        return MovingAverages.ema(trueRange(high, low, close), period);
    }

    public record BollingerBands(List<Double> middle, List<Double> upper, List<Double> lower, List<Double> width) {}
    public static BollingerBands bollingerBands(List<Double> close, int period, double stdMultiplier) {
        List<Double> mid = MovingAverages.sma(close, period);
        List<Double> upper = IndicatorUtils.nanList(close == null ? 0 : close.size());
        List<Double> lower = IndicatorUtils.nanList(upper.size());
        List<Double> width = IndicatorUtils.nanList(upper.size());
        if (close == null) return new BollingerBands(mid, upper, lower, width);
        for (int i = period - 1; i < close.size(); i++) {
            double mean = mid.get(i), sumSq = 0.0;
            for (int j = i - period + 1; j <= i; j++) sumSq += Math.pow(close.get(j) - mean, 2);
            double sd = Math.sqrt(sumSq / period);
            upper.set(i, mean + stdMultiplier * sd);
            lower.set(i, mean - stdMultiplier * sd);
            width.set(i, IndicatorUtils.safeDiv(upper.get(i) - lower.get(i), mean));
        }
        return new BollingerBands(mid, upper, lower, width);
    }

    public record KeltnerChannel(List<Double> middle, List<Double> upper, List<Double> lower) {}
    public static KeltnerChannel keltner(List<Double> high, List<Double> low, List<Double> close, int emaPeriod, int atrPeriod, double multiplier) {
        List<Double> middle = MovingAverages.ema(close, emaPeriod);
        List<Double> atr = atr(high, low, close, atrPeriod);
        List<Double> upper = IndicatorUtils.nanList(close == null ? 0 : close.size());
        List<Double> lower = IndicatorUtils.nanList(upper.size());
        for (int i = 0; i < upper.size(); i++) {
            upper.set(i, middle.get(i) + multiplier * atr.get(i));
            lower.set(i, middle.get(i) - multiplier * atr.get(i));
        }
        return new KeltnerChannel(middle, upper, lower);
    }
}
