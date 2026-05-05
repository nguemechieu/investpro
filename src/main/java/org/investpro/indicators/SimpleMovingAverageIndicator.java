package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple Moving Average (SMA) Indicator
 * Calculates the average price over a specified period (default 20).
 */
public class SimpleMovingAverageIndicator extends BaseIndicator {
    
    public SimpleMovingAverageIndicator() {
        this(20);
    }
    
    public SimpleMovingAverageIndicator(int period) {
        super("SMA" + period, period);
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
        
        double[] smaValues = calculateSMA(closePrices, period);
        values.put("SMA", smaValues);
        calculated = true;
    }
}
