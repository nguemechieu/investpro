package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.List;

/**
 * Volatility Indicator
 * Measures the standard deviation of price movements over a period.
 * High volatility indicates larger price swings, low volatility indicates
 * smaller swings.
 * Useful for assessing market stability and potential breakout opportunities.
 */
@Getter
@Setter
public class Volatility extends BaseIndicator {
    private static final int DEFAULT_PERIOD = 20;

    public Volatility() {
        super("Volatility", DEFAULT_PERIOD);
    }

    public Volatility(int period) {
        super("Volatility", period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        double[] volatilityValues = calculateVolatility(candles, period);
        values.put("Volatility", volatilityValues);
        calculated = true;
    }

    /**
     * Calculate volatility using standard deviation of returns
     */
    private double[] calculateVolatility(List<CandleData> candles, int period) {
        double[] volatility = new double[candles.size()];

        // Calculate log returns
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < candles.size(); i++) {
            double prevClose = candles.get(i - 1).closePrice();
            double currentClose = candles.get(i).closePrice();
            if (prevClose > 0) {
                double logReturn = Math.log(currentClose / prevClose);
                returns.add(logReturn);
            }
        }

        // Calculate rolling standard deviation (volatility)
        for (int i = 0; i < candles.size(); i++) {
            if (i < period) {
                // Not enough data, use NaN
                volatility[i] = Double.NaN;
            } else {
                // Calculate standard deviation of last 'period' returns
                double mean = 0;

                // Calculate mean
                for (int j = i - period; j < i; j++) {
                    if (j > 0 && j <= returns.size()) {
                        mean += returns.get(j - 1);
                    }
                }
                mean /= period;

                // Calculate variance
                double variance = 0;
                for (int j = i - period; j < i; j++) {
                    if (j > 0 && j <= returns.size()) {
                        double diff = returns.get(j - 1) - mean;
                        variance += diff * diff;
                    }
                }
                variance /= period;

                // Standard deviation (annualized for daily data)
                volatility[i] = Math.sqrt(variance) * Math.sqrt(252) * 100; // Convert to percentage and annualize
            }
        }

        return volatility;
    }
}
