package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * Bollinger Bands Indicator
 * Shows volatility through upper and lower bands around a moving average.
 */
public class BollingerBandsIndicator extends BaseIndicator {
    
    private final double stdDevMultiplier;
    
    public BollingerBandsIndicator() {
        this(20, 2.0);
    }
    
    public BollingerBandsIndicator(int period, double stdDevMultiplier) {
        super("BB" + period, period);
        this.stdDevMultiplier = stdDevMultiplier;
    }
    
    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }
        
        List<Double> closePrices = new ArrayList<>();
        for (CandleData candle : candles) {
            closePrices.add(candle.closePrice());
        }
        
        // Calculate middle band (SMA)
        double[] sma = calculateSMA(closePrices, period);
        double[] upperBand = new double[closePrices.size()];
        double[] lowerBand = new double[closePrices.size()];
        
        // Calculate standard deviation and bands
        for (int i = period - 1; i < closePrices.size(); i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += closePrices.get(j);
            }
            double mean = sum / period;
            
            double sumSquaredDiff = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = closePrices.get(j) - mean;
                sumSquaredDiff += diff * diff;
            }
            double stdDev = Math.sqrt(sumSquaredDiff / period);
            
            upperBand[i] = sma[i] + (stdDev * stdDevMultiplier);
            lowerBand[i] = sma[i] - (stdDev * stdDevMultiplier);
        }
        
        values.put("MiddleBand", sma);
        values.put("UpperBand", upperBand);
        values.put("LowerBand", lowerBand);
        calculated = true;
    }
}
