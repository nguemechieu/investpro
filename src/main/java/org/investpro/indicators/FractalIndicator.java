package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.List;

/**
 * Fractal Indicator
 * Identifies market turning points where price reverses direction.
 * Upside Fractal: high in middle with lower highs on both sides (5-bar pattern)
 * Downside Fractal: low in middle with higher lows on both sides (5-bar pattern)
 */
public class FractalIndicator extends BaseIndicator {
    
    public FractalIndicator() {
        this(2);
    }
    
    public FractalIndicator(int lookback) {
        super("Fractal", lookback);
    }
    
    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < 5) {
            return;
        }
        
        int n = candles.size();
        double[] upFractal = new double[n];
        double[] dnFractal = new double[n];
        
        // Initialize arrays
        for (int i = 0; i < n; i++) {
            upFractal[i] = Double.NaN;
            dnFractal[i] = Double.NaN;
        }
        
        // Find fractals (requires 2 bars on each side, so start at index 2)
        for (int i = 2; i < n - 2; i++) {
            CandleData prev2 = candles.get(i - 2);
            CandleData prev1 = candles.get(i - 1);
            CandleData current = candles.get(i);
            CandleData next1 = candles.get(i + 1);
            CandleData next2 = candles.get(i + 2);
            
            // Check for upside fractal (bullish reversal)
            if (current.highPrice() > prev2.highPrice() &&
                current.highPrice() > prev1.highPrice() &&
                current.highPrice() > next1.highPrice() &&
                current.highPrice() > next2.highPrice()) {
                upFractal[i] = current.highPrice();
            }
            
            // Check for downside fractal (bearish reversal)
            if (current.lowPrice() < prev2.lowPrice() &&
                current.lowPrice() < prev1.lowPrice() &&
                current.lowPrice() < next1.lowPrice() &&
                current.lowPrice() < next2.lowPrice()) {
                dnFractal[i] = current.lowPrice();
            }
        }
        
        values.put("UpFractal", upFractal);
        values.put("DnFractal", dnFractal);
        calculated = true;
    }
}
