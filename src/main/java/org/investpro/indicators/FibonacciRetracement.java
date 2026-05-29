package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.List;

/**
 * Fibonacci Retracement Indicator
 * Shows key support/resistance levels based on Fibonacci ratios.
 * Levels: 0%, 23.6%, 38.2%, 50%, 61.8%, 78.6%, 100%
 */
public class FibonacciRetracement extends BaseIndicator {

    private final int lookbackPeriod;

    public FibonacciRetracement(int lookbackPeriod) {
        super("Fibonacci Retracement", lookbackPeriod);
        this.lookbackPeriod = lookbackPeriod;
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < lookbackPeriod) {
            return;
        }

        // Find highest high and lowest low in lookback period
        double highestHigh = Double.NEGATIVE_INFINITY;
        double lowestLow = Double.POSITIVE_INFINITY;

        int startIndex = Math.max(0, candles.size() - lookbackPeriod);
        for (int i = startIndex; i < candles.size(); i++) {
            highestHigh = Math.max(highestHigh, candles.get(i).highPrice());
            lowestLow = Math.min(lowestLow, candles.get(i).lowPrice());
        }

        double range = highestHigh - lowestLow;

        // Calculate Fibonacci levels
        double level0 = highestHigh; // 0% (top)
        double level236 = highestHigh - range * 0.236;
        double level382 = highestHigh - range * 0.382;
        double level50 = highestHigh - range * 0.50;
        double level618 = highestHigh - range * 0.618;
        double level786 = highestHigh - range * 0.786;
        double level100 = lowestLow; // 100% (bottom)

        // Create arrays for each level (constant for all candles)
        double[] fib0 = new double[candles.size()];
        double[] fib236 = new double[candles.size()];
        double[] fib382 = new double[candles.size()];
        double[] fib50 = new double[candles.size()];
        double[] fib618 = new double[candles.size()];
        double[] fib786 = new double[candles.size()];
        double[] fib100 = new double[candles.size()];

        for (int i = 0; i < candles.size(); i++) {
            fib0[i] = level0;
            fib236[i] = level236;
            fib382[i] = level382;
            fib50[i] = level50;
            fib618[i] = level618;
            fib786[i] = level786;
            fib100[i] = level100;
        }

        // Store all levels
        values.put("0%", fib0);
        values.put("23.6%", fib236);
        values.put("38.2%", fib382);
        values.put("50%", fib50);
        values.put("61.8%", fib618);
        values.put("78.6%", fib786);
        values.put("100%", fib100);

        calculated = true;
    }
}
