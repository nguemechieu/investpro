package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.List;

/**
 * Volume Weighted Average Price (VWAP) Indicator
 * Average price weighted by volume, useful for intraday trading.
 */
public class VWAPIndicator extends BaseIndicator {
    
    public VWAPIndicator() {
        super("VWAP", 1);
    }
    
    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }
        
        double[] vwapValues = new double[candles.size()];
        double cumulativeVolume = 0;
        double cumulativeVolumePrice = 0;
        
        for (int i = 0; i < candles.size(); i++) {
            CandleData candle = candles.get(i);
            double typicalPrice = (candle.highPrice() + candle.lowPrice() + candle.closePrice()) / 3.0;
            double volume = candle.volume();
            
            cumulativeVolume += volume;
            cumulativeVolumePrice += typicalPrice * volume;
            
            vwapValues[i] = cumulativeVolume > 0 ? cumulativeVolumePrice / cumulativeVolume : 0;
        }
        
        values.put("VWAP", vwapValues);
        calculated = true;
    }
}
