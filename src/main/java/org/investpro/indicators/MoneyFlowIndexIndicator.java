package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.List;

/**
 * Money Flow Index (MFI), a volume-weighted momentum oscillator.
 */
public class MoneyFlowIndexIndicator extends BaseIndicator {

    public MoneyFlowIndexIndicator(int period) {
        super("MFI" + period, period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        double[] mfi = new double[candles.size()];
        if (period <= 0 || candles.size() < period + 1) {
            values.put("MFI", mfi);
            calculated = true;
            return;
        }

        double[] typical = new double[candles.size()];
        double[] positive = new double[candles.size()];
        double[] negative = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            CandleData candle = candles.get(i);
            typical[i] = (candle.highPrice() + candle.lowPrice() + candle.closePrice()) / 3.0;
            if (i == 0) {
                continue;
            }
            double moneyFlow = typical[i] * Math.max(0.0, candle.volume());
            if (typical[i] > typical[i - 1]) {
                positive[i] = moneyFlow;
            } else if (typical[i] < typical[i - 1]) {
                negative[i] = moneyFlow;
            }
        }

        for (int i = period; i < candles.size(); i++) {
            double positiveSum = 0.0;
            double negativeSum = 0.0;
            for (int j = i - period + 1; j <= i; j++) {
                positiveSum += positive[j];
                negativeSum += negative[j];
            }
            if (negativeSum == 0.0) {
                mfi[i] = positiveSum == 0.0 ? 50.0 : 100.0;
            } else {
                double ratio = positiveSum / negativeSum;
                mfi[i] = 100.0 - (100.0 / (1.0 + ratio));
            }
        }

        values.put("MFI", mfi);
        calculated = true;
    }
}
