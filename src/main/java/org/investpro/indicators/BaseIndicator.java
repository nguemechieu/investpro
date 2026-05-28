package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for all technical indicators with common functionality.
 */
public abstract class BaseIndicator implements ChartIndicator {
    
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
    
    /**
     * Calculate simple moving average
     */
    protected double[] calculateSMA(List<Double> data, int period) {
        if (data == null) {
            return new double[0];
        }
        double[] sma = new double[data.size()];
        if (period <= 0 || data.size() < period) {
            return sma;
        }
        
        for (int i = period - 1; i < data.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += data.get(j);
            }
            sma[i] = sum / period;
        }
        
        return sma;
    }
    
    /**
     * Calculate exponential moving average
     */
    protected double[] calculateEMA(List<Double> data, int period) {
        if (data == null) {
            return new double[0];
        }
        double[] ema = new double[data.size()];
        if (period <= 0 || data.size() < period) {
            return ema;
        }
        double multiplier = 2.0 / (period + 1);
        
        // Initial SMA
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += data.get(i);
        }
        ema[period - 1] = sum / period;
        
        // EMA calculation
        for (int i = period; i < data.size(); i++) {
            ema[i] = data.get(i) * multiplier + ema[i - 1] * (1 - multiplier);
        }
        
        return ema;
    }
    
    /**
     * Calculate RSI (Relative Strength Index)
     */
    protected double[] calculateRSI(List<Double> closeData, int period) {
        if (closeData == null) {
            return new double[0];
        }
        double[] rsi = new double[closeData.size()];
        if (period <= 0 || closeData.size() < period + 1) {
            return rsi;
        }
        double[] gains = new double[closeData.size()];
        double[] losses = new double[closeData.size()];
        
        // Calculate gains and losses
        for (int i = 1; i < closeData.size(); i++) {
            double change = closeData.get(i) - closeData.get(i - 1);
            gains[i] = change > 0 ? change : 0;
            losses[i] = change < 0 ? -change : 0;
        }
        
        // Calculate initial average gain/loss
        double avgGain = 0, avgLoss = 0;
        for (int i = 1; i <= period; i++) {
            avgGain += gains[i];
            avgLoss += losses[i];
        }
        avgGain /= period;
        avgLoss /= period;
        
        // Calculate RSI
        rsi[period] = avgLoss == 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
        
        for (int i = period + 1; i < closeData.size(); i++) {
            avgGain = (avgGain * (period - 1) + gains[i]) / period;
            avgLoss = (avgLoss * (period - 1) + losses[i]) / period;
            rsi[i] = avgLoss == 0 ? 100 : 100 - (100 / (1 + avgGain / avgLoss));
        }
        
        return rsi;
    }
}
