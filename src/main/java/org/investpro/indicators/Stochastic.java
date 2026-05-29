package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * Stochastic Oscillator Indicator
 * Momentum indicator comparing closing price to price range (0-100).
 */
@Getter
@Setter
public class Stochastic extends BaseIndicator {

    private int kPeriod;
    private int kSlowPeriod;
    private int dPeriod;

    public Stochastic() {
        this(14, 3, 3);
    }

    public Stochastic(int kPeriod, int kSlowPeriod, int dPeriod) {
        super("Stochastic", Math.max(kPeriod, Math.max(kSlowPeriod, dPeriod)));
        this.kPeriod = kPeriod;
        this.kSlowPeriod = kSlowPeriod;
        this.dPeriod = dPeriod;
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < kPeriod) {
            return;
        }

        // Calculate raw stochastic %K
        double[] rawK = new double[candles.size()];
        for (int i = kPeriod - 1; i < candles.size(); i++) {
            double highest = Double.NEGATIVE_INFINITY;
            double lowest = Double.POSITIVE_INFINITY;

            for (int j = i - kPeriod + 1; j <= i; j++) {
                CandleData candle = candles.get(j);
                highest = Math.max(highest, candle.highPrice());
                lowest = Math.min(lowest, candle.lowPrice());
            }

            double close = candles.get(i).closePrice();
            double range = highest - lowest;
            rawK[i] = range == 0 ? 50 : 100 * (close - lowest) / range;
        }

        // Smooth %K with SMA
        List<Double> kValues = new ArrayList<>();
        for (int i = kPeriod - 1; i < candles.size(); i++) {
            kValues.add(rawK[i]);
        }

        double[] smoothedK = new double[candles.size()];
        if (kValues.size() >= kSlowPeriod) {
            double[] tmpK = calculateSMA(kValues, kSlowPeriod);
            for (int i = 0; i < tmpK.length && (kPeriod - 1 + i) < candles.size(); i++) {
                smoothedK[kPeriod - 1 + i] = tmpK[i];
            }
        }

        // Calculate %D (SMA of %K)
        List<Double> dInputs = new ArrayList<>();
        for (int i = kPeriod - 1; i < candles.size(); i++) {
            dInputs.add(smoothedK[i]);
        }
        double[] dValues = new double[candles.size()];
        if (dInputs.size() >= dPeriod) {
            double[] tmpD = calculateSMA(dInputs, dPeriod);
            for (int i = 0; i < tmpD.length && (kPeriod - 1 + i) < candles.size(); i++) {
                dValues[kPeriod - 1 + i] = tmpD[i];
            }
        }

        values.put("K", smoothedK);
        values.put("D", dValues);
        calculated = true;
    }
}
