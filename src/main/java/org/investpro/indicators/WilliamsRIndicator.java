package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.List;

/**
 * Williams %R oscillator. Values range from -100 to 0.
 */
public class WilliamsRIndicator extends BaseIndicator {

    public WilliamsRIndicator(int period) {
        super("Williams %R" + period, period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        double[] williamsR = new double[candles.size()];
        if (period <= 0 || candles.size() < period) {
            values.put("WilliamsR", williamsR);
            calculated = true;
            return;
        }

        for (int i = period - 1; i < candles.size(); i++) {
            double highest = Double.NEGATIVE_INFINITY;
            double lowest = Double.POSITIVE_INFINITY;
            for (int j = i - period + 1; j <= i; j++) {
                CandleData candle = candles.get(j);
                highest = Math.max(highest, candle.highPrice());
                lowest = Math.min(lowest, candle.lowPrice());
            }
            double range = highest - lowest;
            williamsR[i] = range == 0.0 ? -50.0 : ((highest - candles.get(i).closePrice()) / range) * -100.0;
        }

        values.put("WilliamsR", williamsR);
        calculated = true;
    }
}
