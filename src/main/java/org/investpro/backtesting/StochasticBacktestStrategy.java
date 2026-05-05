package org.investpro.backtesting;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import org.investpro.indicators.StochasticIndicator;
import java.util.*;

/**
 * Example trading strategy implementation using Stochastic indicator
 */
@Setter(AccessLevel.PRIVATE)
@Getter
public class StochasticBacktestStrategy extends BacktestStrategy {
    private final StochasticIndicator stochasticIndicator;
    private double kUpperBand;
    private double kLowerBand;
    private double dUpperBand;
    private double dLowerBand;

    public StochasticBacktestStrategy(BacktestConfig config) {
        super("Stochastic Strategy", config);
        this.stochasticIndicator = new StochasticIndicator(14, 3, 3);
        this.kUpperBand = 80.0;
        this.kLowerBand = 20.0;
        this.dUpperBand = 80.0;
        this.dLowerBand = 20.0;

        // Store parameters
        setParameter("kPeriod", 14);
        setParameter("kSlowPeriod", 3);
        setParameter("dPeriod", 3);
        setParameter("kUpperBand", kUpperBand);
        setParameter("kLowerBand", kLowerBand);
    }

    @Override
    public List<SignalEvent> processData() {
        signals.clear();

        if (candleHistory.size() < 20) {
            return signals;
        }

        // Calculate stochastic for all candles
        for (int i = 14; i < candleHistory.size(); i++) {
            List<CandleData> window = candleHistory.subList(Math.max(0, i - 13), i + 1);

            stochasticIndicator.calculate(window);
            Map<String, double[]> stochasticValues = stochasticIndicator.getValues();
            if (stochasticValues == null)
                continue;

            double[] kValues = stochasticValues.get("K");
            double[] dValues = stochasticValues.get("D");

            if (kValues == null || dValues == null || kValues.length == 0)
                continue;

            double k = kValues[kValues.length - 1];
            double d = dValues[dValues.length - 1];

            // Generate buy signal when K crosses above D below 20
            if (i > 15) {
                double prevK = kValues.length > 1 ? kValues[kValues.length - 2] : k;
                double prevD = dValues.length > 1 ? dValues[dValues.length - 2] : d;

                // Oversold condition: both K and D below 20
                if (k > d && prevK <= prevD && k < 30 && d < 30) {
                    signals.add(new SignalEvent(i, SignalEvent.Type.BUY,
                            String.format("K(%.2f) > D(%.2f) oversold", k, d), 0.8));
                }

                // Overbought condition: both K and D above 80
                if (k < d && prevK >= prevD && k > 70 && d > 70) {
                    signals.add(new SignalEvent(i, SignalEvent.Type.SELL,
                            String.format("K(%.2f) < D(%.2f) overbought", k, d), 0.8));
                }
            }
        }

        return signals;
    }

    @Override
    public void onCandleUpdate(CandleData candle, int candleIndex) {
        if (candleIndex < 14)
            return;

        List<CandleData> window = candleHistory.subList(
                Math.max(0, candleIndex - 13), candleIndex + 1);

        stochasticIndicator.calculate(window);
        Map<String, double[]> stochasticValues = stochasticIndicator.getValues();
        if (stochasticValues == null)
            return;

        double[] kValues = stochasticValues.get("K");
        double[] dValues = stochasticValues.get("D");

        if (kValues == null || dValues == null || kValues.length == 0)
            return;

        double k = kValues[kValues.length - 1];
        double d = dValues[dValues.length - 1];

        // Generate signal on live candle
        if (candleIndex > 1) {
            double prevK = kValues.length > 1 ? kValues[kValues.length - 2] : k;
            double prevD = dValues.length > 1 ? dValues[dValues.length - 2] : d;

            if (k > d && prevK <= prevD && k < 30) {
                addSignal(new SignalEvent(candleIndex, SignalEvent.Type.BUY,
                        String.format("K(%.2f) > D(%.2f) oversold", k, d)));
            } else if (k < d && prevK >= prevD && k > 70) {
                addSignal(new SignalEvent(candleIndex, SignalEvent.Type.SELL,
                        String.format("K(%.2f) < D(%.2f) overbought", k, d)));
            }
        }
    }

    public void setKBands(double upper, double lower) {
        this.kUpperBand = upper;
        this.kLowerBand = lower;
        setParameter("kUpperBand", upper);
        setParameter("kLowerBand", lower);
    }

    public void setDBands(double upper, double lower) {
        this.dUpperBand = upper;
        this.dLowerBand = lower;
    }
}
