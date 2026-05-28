package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.List;

/**
 * Rate of Change (ROC) momentum indicator.
 */
public class RateOfChangeIndicator extends BaseIndicator {

    public RateOfChangeIndicator(int period) {
        super("ROC" + period, period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        double[] roc = new double[candles.size()];
        if (period <= 0 || candles.size() <= period) {
            values.put("ROC", roc);
            calculated = true;
            return;
        }

        for (int i = period; i < candles.size(); i++) {
            double previous = candles.get(i - period).closePrice();
            roc[i] = previous == 0.0 ? 0.0 : ((candles.get(i).closePrice() - previous) / previous) * 100.0;
        }

        values.put("ROC", roc);
        calculated = true;
    }
}
