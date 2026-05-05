package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.List;

/**
 * Average True Range (ATR) Indicator
 * Measures market volatility using average of true range values.
 */
public class ATRIndicator extends BaseIndicator {
    
    public ATRIndicator() {
        this(14);
    }
    
    public ATRIndicator(int period) {
        super("ATR" + period, period);
    }
    
    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < 2) {
            return;
        }
        
        double[] trueRange = calculateTrueRange(candles);
        double[] atrValues = new double[candles.size()];
        
        // Calculate initial ATR (simple average of first period)
        double sum = 0;
        for (int i = 0; i < Math.min(period, trueRange.length); i++) {
            sum += trueRange[i];
        }
        atrValues[period - 1] = sum / period;
        
        // Calculate rest using EMA-like smoothing
        for (int i = period; i < candles.size(); i++) {
            atrValues[i] = (atrValues[i - 1] * (period - 1) + trueRange[i]) / period;
        }
        
        values.put("ATR", atrValues);
        calculated = true;
    }
    
    private double[] calculateTrueRange(List<CandleData> candles) {
        double[] tr = new double[candles.size()];
        
        for (int i = 0; i < candles.size(); i++) {
            CandleData current = candles.get(i);
            double high = current.highPrice();
            double low = current.lowPrice();
            double range = high - low;
            
            if (i > 0) {
                double prevClose = candles.get(i - 1).closePrice();
                double high2close = Math.abs(high - prevClose);
                double low2close = Math.abs(low - prevClose);
                tr[i] = Math.max(range, Math.max(high2close, low2close));
            } else {
                tr[i] = range;
            }
        }
        
        return tr;
    }
}
