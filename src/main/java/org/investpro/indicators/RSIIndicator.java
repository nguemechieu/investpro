package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * Relative Strength Index (RSI) Indicator
 * Momentum oscillator measuring speed and magnitude of price changes (0-100).
 */
public class RSIIndicator extends BaseIndicator {
    
    public RSIIndicator() {
        this(14);
    }
    
    public RSIIndicator(int period) {
        super("RSI" + period, period);
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
        
        double[] rsiValues = calculateRSI(closePrices, period);
        values.put("RSI", rsiValues);
        calculated = true;
    }
}
