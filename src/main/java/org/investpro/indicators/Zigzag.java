package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Zigzag Indicator
 * Shows trend direction by connecting significant highs and lows.
 * Filters out minor price movements based on threshold percentage.
 */
@Getter
@Setter
public class Zigzag extends BaseIndicator {

    private double thresholdPercent;

    public Zigzag(double thresholdPercent) {
        super("Zigzag", 1);
        this.thresholdPercent = thresholdPercent;
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < 2) {
            return;
        }

        int n = candles.size();
        double[] zigzag = new double[n];

        // Initialize with NaN values
        Arrays.fill(zigzag, Double.NaN);

        // Find zigzag turning points
        List<Integer> turnPoints = new ArrayList<>();
        List<Double> turnValues = new ArrayList<>();

        boolean isUptrend = true;
        double lastValue = candles.get(0).lowPrice();
        int lastIndex = 0;
        turnPoints.add(0);
        turnValues.add(lastValue);
        zigzag[0] = lastValue;

        for (int i = 1; i < n; i++) {
            CandleData current = candles.get(i);
            double high = current.highPrice();
            double low = current.lowPrice();

            if (isUptrend) {
                // In uptrend, look for reversal from high
                if (high > lastValue) {
                    lastValue = high;
                    lastIndex = i;
                }

                // Check if reversal threshold is met (reversal to downtrend)
                double retracement = (lastValue - low) / lastValue * 100.0;
                if (retracement >= thresholdPercent) {
                    // Found a reversal point
                    turnPoints.add(lastIndex);
                    turnValues.add(lastValue);
                    zigzag[lastIndex] = lastValue;
                    isUptrend = false;
                    lastValue = low;
                    lastIndex = i;
                }
            } else {
                // In downtrend, look for reversal from low
                if (low < lastValue) {
                    lastValue = low;
                    lastIndex = i;
                }

                // Check if reversal threshold is met (reversal to uptrend)
                double retracement = (high - lastValue) / lastValue * 100.0;
                if (retracement >= thresholdPercent) {
                    // Found a reversal point
                    turnPoints.add(lastIndex);
                    turnValues.add(lastValue);
                    zigzag[lastIndex] = lastValue;
                    isUptrend = true;
                    lastValue = high;
                    lastIndex = i;
                }
            }
        }

        // Add final point if not already added
        if (lastIndex != turnPoints.get(turnPoints.size() - 1)) {
            turnPoints.add(lastIndex);
            turnValues.add(lastValue);
            zigzag[lastIndex] = lastValue;
        }

        // Interpolate zigzag line between turning points
        for (int i = 0; i < turnPoints.size() - 1; i++) {
            int startIdx = turnPoints.get(i);
            int endIdx = turnPoints.get(i + 1);
            double startVal = turnValues.get(i);
            double endVal = turnValues.get(i + 1);

            // Linear interpolation between turning points
            for (int j = startIdx; j <= endIdx; j++) {
                double ratio = (j - startIdx) / (double) (endIdx - startIdx);
                zigzag[j] = startVal + (endVal - startVal) * ratio;
            }
        }

        values.put("Zigzag", zigzag);
        calculated = true;
    }
}
