package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.List;

/**
 * Williams %R oscillator in range [-100, 0].
 */
public class WilliamsR extends BaseIndicator {

    public WilliamsR(int period) {
        super("WILLIAMS_R" + period, period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < period) {
            return;
        }

        double[] valuesR = new double[candles.size()];
        for (int i = period - 1; i < candles.size(); i++) {
            double highestHigh = Double.NEGATIVE_INFINITY;
            double lowestLow = Double.POSITIVE_INFINITY;

            for (int j = i - period + 1; j <= i; j++) {
                highestHigh = Math.max(highestHigh, candles.get(j).highPrice());
                lowestLow = Math.min(lowestLow, candles.get(j).lowPrice());
            }

            double range = highestHigh - lowestLow;
            if (range == 0.0) {
                valuesR[i] = 0.0;
            } else {
                valuesR[i] = ((highestHigh - candles.get(i).closePrice()) / range) * -100.0;
            }
        }

        values.put("WILLIAMS_R", valuesR);
        calculated = true;
    }
}
