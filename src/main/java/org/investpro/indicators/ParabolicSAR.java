package org.investpro.indicators;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.List;

/**
 * Parabolic SAR (Stop and Reverse) Indicator
 * Provides entry and exit points, showing trend reversals.
 */
@Getter
@Setter
public class ParabolicSAR extends BaseIndicator {

    private double initialAF;
    private double maxAF;

    public ParabolicSAR(double initialAF, double maxAF) {
        super("Parabolic SAR", 1);
        this.initialAF = initialAF;
        this.maxAF = maxAF;
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < 2) {
            return;
        }

        double[] sar = new double[candles.size()];

        // Determine initial trend
        boolean isUptrend = candles.get(1).closePrice() > candles.get(0).closePrice();
        double af = initialAF;
        double extremePoint = isUptrend ? candles.get(1).highPrice() : candles.get(1).lowPrice();

        // Initialize SAR
        sar[0] = isUptrend ? candles.get(0).lowPrice() : candles.get(0).highPrice();

        for (int i = 1; i < candles.size(); i++) {
            CandleData current = candles.get(i);

            // Update SAR
            sar[i] = sar[i - 1] + af * (extremePoint - sar[i - 1]);

            if (isUptrend) {
                // Check for reversal to downtrend
                if (current.lowPrice() <= sar[i]) {
                    isUptrend = false;
                    sar[i] = extremePoint;
                    extremePoint = current.lowPrice();
                    af = initialAF;
                } else {
                    // Update extreme point and AF in uptrend
                    if (current.highPrice() > extremePoint) {
                        extremePoint = current.highPrice();
                        af = Math.min(af + initialAF, maxAF);
                    }
                    // SAR cannot be above the last 2 lows
                    sar[i] = Math.min(sar[i], current.lowPrice());
                    if (i > 0) {
                        sar[i] = Math.min(sar[i], candles.get(i - 1).lowPrice());
                    }
                }
            } else {
                // Check for reversal to uptrend
                if (current.highPrice() >= sar[i]) {
                    isUptrend = true;
                    sar[i] = extremePoint;
                    extremePoint = current.highPrice();
                    af = initialAF;
                } else {
                    // Update extreme point and AF in downtrend
                    if (current.lowPrice() < extremePoint) {
                        extremePoint = current.lowPrice();
                        af = Math.min(af + initialAF, maxAF);
                    }
                    // SAR cannot be below the last 2 highs
                    sar[i] = Math.max(sar[i], current.highPrice());
                    if (i > 0) {
                        sar[i] = Math.max(sar[i], candles.get(i - 1).highPrice());
                    }
                }
            }
        }

        values.put("SAR", sar);
        calculated = true;
    }
}
