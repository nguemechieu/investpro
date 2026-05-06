package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * ADX (Average Directional Index) Indicator
 * Measures trend strength using +DI, -DI, and ADX.
 * ADX above 25 indicates strong trend, below 20 indicates weak trend.
 */
public class ADXIndicator extends BaseIndicator {

    public ADXIndicator(int period) {
        super("ADX", period);
    }
    
    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < period + 1) {
            return;
        }
        
        int n = candles.size();
        
        // Calculate True Range
        List<Double> trueRanges = new ArrayList<>();
        List<Double> plusDMs = new ArrayList<>();
        List<Double> minusDMs = new ArrayList<>();
        
        for (int i = 0; i < n; i++) {
            CandleData current = candles.get(i);
            double tr;
            double plusDM = 0;
            double minusDM = 0;
            
            if (i == 0) {
                tr = current.highPrice() - current.lowPrice();
            } else {
                CandleData prev = candles.get(i - 1);
                tr = Math.max(
                    current.highPrice() - current.lowPrice(),
                    Math.max(
                        Math.abs(current.highPrice() - prev.closePrice()),
                        Math.abs(current.lowPrice() - prev.closePrice())
                    )
                );
                
                // Calculate directional movements
                double upMove = current.highPrice() - prev.highPrice();
                double downMove = prev.lowPrice() - current.lowPrice();
                
                if (upMove > 0 && upMove > downMove) {
                    plusDM = upMove;
                }
                if (downMove > 0 && downMove > upMove) {
                    minusDM = downMove;
                }
            }
            
            trueRanges.add(tr);
            plusDMs.add(plusDM);
            minusDMs.add(minusDM);
        }
        
        // Smooth with RSI-style smoothing (EMA-like)
        double[] trSmoothed = smoothRSI(trueRanges, period);
        double[] plusDMSmoothed = smoothRSI(plusDMs, period);
        double[] minusDMSmoothed = smoothRSI(minusDMs, period);
        
        // Calculate +DI and -DI
        double[] plusDI = new double[n];
        double[] minusDI = new double[n];
        double[] dx = new double[n];
        
        for (int i = 0; i < n; i++) {
            plusDI[i] = trSmoothed[i] != 0 ? (100.0 * plusDMSmoothed[i] / trSmoothed[i]) : 0;
            minusDI[i] = trSmoothed[i] != 0 ? (100.0 * minusDMSmoothed[i] / trSmoothed[i]) : 0;
            
            double diSum = plusDI[i] + minusDI[i];
            if (diSum != 0) {
                dx[i] = 100.0 * Math.abs(plusDI[i] - minusDI[i]) / diSum;
            } else {
                dx[i] = 0;
            }
        }
        
        // Calculate ADX as smoothed DX
        double[] adx = smoothRSI(toList(dx), period);
        
        values.put("+DI", plusDI);
        values.put("-DI", minusDI);
        values.put("ADX", adx);
        
        calculated = true;
    }
    
    /**
     * RSI-style smoothing (Wilder's smoothing)
     */
    private double[] smoothRSI(List<Double> data, int period) {
        double[] result = new double[data.size()];
        if (data.size() < period) {
            for (int i = 0; i < data.size(); i++) {
                result[i] = data.get(i);
            }
            return result;
        }
        
        // First value: simple average
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += data.get(i);
        }
        result[period - 1] = sum / period;
        
        // Subsequent values: Wilder's smoothing
        for (int i = period; i < data.size(); i++) {
            result[i] = (result[i - 1] * (period - 1) + data.get(i)) / period;
        }
        
        // Fill initial period with first smoothed value
        for (int i = 0; i < period - 1; i++) {
            result[i] = result[period - 1];
        }
        
        return result;
    }
    
    private List<Double> toList(double[] arr) {
        List<Double> list = new ArrayList<>();
        for (double v : arr) {
            list.add(v);
        }
        return list;
    }
}
