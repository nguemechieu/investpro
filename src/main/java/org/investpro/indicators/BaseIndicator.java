package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared base implementation for technical indicators.
 */
public abstract class BaseIndicator implements Indicator {

    protected String name;
    protected int period;
    protected boolean calculated;
    protected Map<String, double[]> values;

    public BaseIndicator(String name, int period) {
        this.name = name;
        this.period = period;
        this.calculated = false;
        this.values = new HashMap<>();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getPeriod() {
        return period;
    }

    @Override
    public boolean isCalculated() {
        return calculated;
    }

    @Override
    public Map<String, double[]> getValues() {
        return values;
    }

    @Override
    public void reset() {
        values.clear();
        calculated = false;
    }

    @Override
    public abstract void calculate(List<CandleData> candles);

    protected double[] calculateSMA(List<Double> data, int period) {
        if (data == null || data.size() < period || period <= 0) {
            return new double[0];
        }

        double[] sma = new double[data.size()];
        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += data.get(j);
            }
            sma[i] = sum / period;
        }
        return sma;
    }

    protected double[] calculateEMA(List<Double> data, int period) {
        if (data == null || data.size() < period || period <= 0) {
            return new double[0];
        }

        double[] ema = new double[data.size()];
        double multiplier = 2.0 / (period + 1);

        double seed = 0.0;
        for (int i = 0; i < period; i++) {
            seed += data.get(i);
        }
        ema[period - 1] = seed / period;

        for (int i = period; i < data.size(); i++) {
            ema[i] = data.get(i) * multiplier + ema[i - 1] * (1.0 - multiplier);
        }

        return ema;
    }

    protected double[] calculateRSI(List<Double> closeData, int period) {
        if (closeData == null || closeData.size() < period + 1 || period <= 0) {
            return new double[0];
        }

        double[] rsi = new double[closeData.size()];
        double[] gains = new double[closeData.size()];
        double[] losses = new double[closeData.size()];

        for (int i = 1; i < closeData.size(); i++) {
            double change = closeData.get(i) - closeData.get(i - 1);
            gains[i] = Math.max(0.0, change);
            losses[i] = Math.max(0.0, -change);
        }

        double avgGain = 0.0;
        double avgLoss = 0.0;
        for (int i = 1; i <= period; i++) {
            avgGain += gains[i];
            avgLoss += losses[i];
        }
        avgGain /= period;
        avgLoss /= period;

        rsi[period] = avgLoss == 0.0 ? 100.0 : 100.0 - (100.0 / (1.0 + avgGain / avgLoss));

        for (int i = period + 1; i < closeData.size(); i++) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period;
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period;
            rsi[i] = avgLoss == 0.0 ? 100.0 : 100.0 - (100.0 / (1.0 + avgGain / avgLoss));
        }

        return rsi;
    }
}
