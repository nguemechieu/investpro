package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * CCI (Commodity Channel Index) Indicator
 * Measures deviation from average price using typical price and mean absolute deviation.
 * Values typically range from -100 to +100.
 */
public class CCIIndicator extends BaseIndicator {
    
    public CCIIndicator() {
        this(20);
    }
    
    public CCIIndicator(int period) {
        super("CCI", period);
    }
    
    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < period) {
            return;
        }
        
        int n = candles.size();
        
        // Calculate typical prices
        List<Double> typicalPrices = new ArrayList<>();
        for (CandleData candle : candles) {
            double tp = (candle.highPrice() + candle.lowPrice() + candle.closePrice()) / 3.0;
            typicalPrices.add(tp);
        }
        
        // Calculate CCI
        double[] cci = new double[n];
        
        for (int i = period - 1; i < n; i++) {
            // Calculate SMA of typical price over period
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += typicalPrices.get(j);
            }
            double sma = sum / period;
            
            // Calculate Mean Absolute Deviation (MAD)
            double madSum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                madSum += Math.abs(typicalPrices.get(j) - sma);
            }
            double mad = madSum / period;
            
            // Calculate CCI
            if (mad != 0) {
                cci[i] = (typicalPrices.get(i) - sma) / (0.015 * mad);
            } else {
                cci[i] = 0;
            }
        }
        
        // Fill earlier values with first calculated CCI
        if (period - 1 < n) {
            for (int i = 0; i < period - 1; i++) {
                cci[i] = cci[period - 1];
            }
        }
        
        values.put("CCI", cci);
        calculated = true;
    }
}
