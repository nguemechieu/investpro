package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.List;

/**
 * Donchian Channel: highest high and lowest low over lookback period.
 */
public class DonchianChannel extends BaseIndicator {

    public DonchianChannel(int period) {
        super("DONCHIAN_CHANNEL" + period, period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < period) {
            return;
        }

        double[] highBand = new double[candles.size()];
        double[] lowBand = new double[candles.size()];
        double[] midBand = new double[candles.size()];

        for (int i = period - 1; i < candles.size(); i++) {
            double highestHigh = Double.NEGATIVE_INFINITY;
            double lowestLow = Double.POSITIVE_INFINITY;

            for (int j = i - period + 1; j <= i; j++) {
                highestHigh = Math.max(highestHigh, candles.get(j).highPrice());
                lowestLow = Math.min(lowestLow, candles.get(j).lowPrice());
            }

            highBand[i] = highestHigh;
            lowBand[i] = lowestLow;
            midBand[i] = (highestHigh + lowestLow) / 2.0;
        }

        values.put("DONCHIAN_HIGH", highBand);
        values.put("DONCHIAN_LOW", lowBand);
        values.put("DONCHIAN_MID", midBand);
        calculated = true;
    }
}
