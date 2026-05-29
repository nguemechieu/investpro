package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.List;

/**
 * Momentum study: close[t] - close[t-period].
 */
public class Momentum extends BaseIndicator {

    public Momentum(int period) {
        super("MOMENTUM" + period, period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() <= period) {
            return;
        }

        double[] momentum = new double[candles.size()];
        for (int i = period; i < candles.size(); i++) {
            momentum[i] = candles.get(i).closePrice() - candles.get(i - period).closePrice();
        }

        values.put("MOMENTUM", momentum);
        calculated = true;
    }
}
