package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * MACD (Moving Average Convergence Divergence) Indicator
 * Trend-following momentum indicator showing relationship between two moving
 * averages.
 */
@Getter
@Setter
public class MACD extends BaseIndicator {

    private int fastPeriod;
    private int slowPeriod;
    private int signalPeriod;

    public MACD() {
        this(12, 26, 9);
    }

    public MACD(int fastPeriod, int slowPeriod, int signalPeriod) {
        super("MACD", Math.max(slowPeriod, signalPeriod));
        this.fastPeriod = fastPeriod;
        this.slowPeriod = slowPeriod;
        this.signalPeriod = signalPeriod;
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

        // Calculate EMAs
        double[] emaFast = calculateEMA(closePrices, fastPeriod);
        double[] emaSlow = calculateEMA(closePrices, slowPeriod);

        // Calculate MACD line
        double[] macdLine = new double[closePrices.size()];
        for (int i = slowPeriod - 1; i < closePrices.size(); i++) {
            macdLine[i] = emaFast[i] - emaSlow[i];
        }

        // Calculate signal line (EMA of MACD)
        List<Double> macdValues = new ArrayList<>();
        for (int i = slowPeriod - 1; i < closePrices.size(); i++) {
            macdValues.add(macdLine[i]);
        }
        double[] signalLine = new double[closePrices.size()];
        if (macdValues.size() >= signalPeriod) {
            double[] tmpSignal = calculateEMA(macdValues, signalPeriod);
            for (int i = 0; i < tmpSignal.length && (slowPeriod - 1 + i) < closePrices.size(); i++) {
                signalLine[slowPeriod - 1 + i] = tmpSignal[i];
            }
        }

        // Calculate histogram
        double[] histogram = new double[closePrices.size()];
        for (int i = 0; i < closePrices.size(); i++) {
            histogram[i] = macdLine[i] - signalLine[i];
        }

        values.put("MACD", macdLine);
        values.put("Signal", signalLine);
        values.put("Histogram", histogram);
        calculated = true;
    }
}
