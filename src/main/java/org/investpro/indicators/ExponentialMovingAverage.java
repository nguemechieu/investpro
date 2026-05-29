package org.investpro.indicators;

import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * Exponential Moving Average (EMA) Indicator
 * Weighted moving average that gives more weight to recent prices.
 */
public class ExponentialMovingAverage extends BaseIndicator {

    public ExponentialMovingAverage(int period) {
        super("EMA" + period, period);
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

        double[] emaValues = calculateEMA(closePrices, period);
        values.put("EMA", emaValues);
        calculated = true;
    }
}
