package org.investpro.indicators.momentum;

import org.investpro.indicators.core.IndicatorUtils;
import org.investpro.indicators.trend.MovingAverages;
import java.util.List;

public final class Momentum {
    private Momentum() {}

    public static List<Double> rsi(List<Double> close, int period) {
        IndicatorUtils.requirePeriod(period);
        List<Double> out = IndicatorUtils.nanList(close == null ? 0 : close.size());
        if (close == null || close.size() <= period) return out;
        double gain = 0, loss = 0;
        for (int i = 1; i <= period; i++) {
            double ch = close.get(i) - close.get(i - 1);
            if (ch >= 0) gain += ch; else loss -= ch;
        }
        double avgGain = gain / period, avgLoss = loss / period;
        out.set(period, 100.0 - 100.0 / (1.0 + IndicatorUtils.safeDiv(avgGain, avgLoss)));
        for (int i = period + 1; i < close.size(); i++) {
            double ch = close.get(i) - close.get(i - 1);
            avgGain = (avgGain * (period - 1) + Math.max(ch, 0)) / period;
            avgLoss = (avgLoss * (period - 1) + Math.max(-ch, 0)) / period;
            out.set(i, avgLoss == 0 ? 100.0 : 100.0 - 100.0 / (1.0 + avgGain / avgLoss));
        }
        return out;
    }

    public record MacdResult(List<Double> macd, List<Double> signal, List<Double> histogram) {}
    public static MacdResult macd(List<Double> close, int fast, int slow, int signalPeriod) {
        List<Double> fastEma = MovingAverages.ema(close, fast);
        List<Double> slowEma = MovingAverages.ema(close, slow);
        List<Double> macd = IndicatorUtils.nanList(close == null ? 0 : close.size());
        for (int i = 0; i < macd.size(); i++) macd.set(i, fastEma.get(i) - slowEma.get(i));
        List<Double> signal = MovingAverages.ema(macd, signalPeriod);
        List<Double> hist = IndicatorUtils.nanList(macd.size());
        for (int i = 0; i < hist.size(); i++) hist.set(i, macd.get(i) - signal.get(i));
        return new MacdResult(macd, signal, hist);
    }

    public static List<Double> roc(List<Double> close, int period) {
        List<Double> out = IndicatorUtils.nanList(close == null ? 0 : close.size());
        if (close == null) return out;
        for (int i = period; i < close.size(); i++) out.set(i, 100.0 * IndicatorUtils.safeDiv(close.get(i) - close.get(i - period), close.get(i - period)));
        return out;
    }

    public static List<Double> stochasticK(List<Double> high, List<Double> low, List<Double> close, int period) {
        List<Double> out = IndicatorUtils.nanList(close == null ? 0 : close.size());
        if (high == null || low == null || close == null) return out;
        for (int i = period - 1; i < close.size(); i++) {
            double hh = IndicatorUtils.highest(high, i - period + 1, i + 1);
            double ll = IndicatorUtils.lowest(low, i - period + 1, i + 1);
            out.set(i, 100.0 * IndicatorUtils.safeDiv(close.get(i) - ll, hh - ll));
        }
        return out;
    }
}
