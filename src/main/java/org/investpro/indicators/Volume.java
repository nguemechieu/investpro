package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.List;

/**
 * Volume Indicator
 * Measures trading volume changes and trends.
 * Can help identify strength of price movements and potential reversals.
 * Rising volume confirms trends; declining volume suggests weakness.
 */
@Getter
@Setter
public class Volume extends BaseIndicator {
    public Volume() {
        super("Volume", 1);
    }

    public Volume(int period) {
        super("Volume", period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        double[] volumeValues = new double[candles.size()];
        for (int i = 0; i < candles.size(); i++) {
            volumeValues[i] = candles.get(i).volume();
        }

        values.put("Volume", volumeValues);
        calculated = true;
    }

    /**
     * Get average volume over a period
     */
    public double getAverageVolume(List<CandleData> candles, int period) {
        if (candles == null || candles.isEmpty() || period <= 0) {
            return 0;
        }

        int startIndex = Math.max(0, candles.size() - period);
        double sum = 0;
        for (int i = startIndex; i < candles.size(); i++) {
            sum += candles.get(i).volume();
        }
        return sum / (candles.size() - startIndex);
    }

    /**
     * Get volume trend: 1 for increasing, -1 for decreasing, 0 for neutral
     */
    public int getVolumeTrend(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period) {
            return 0;
        }

        double oldAvg = getAverageVolume(
                candles.subList(0, Math.max(1, candles.size() - period * 2)),
                period);
        double newAvg = getAverageVolume(candles, period);

        if (newAvg > oldAvg * 1.1) {
            return 1; // Increasing
        } else if (newAvg < oldAvg * 0.9) {
            return -1; // Decreasing
        }
        return 0; // Neutral
    }
}
