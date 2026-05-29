package org.investpro.indicators;

import org.investpro.data.CandleData;

import java.util.ArrayList;
import java.util.List;

/**
 * ADX (Average Directional Index) indicator.
 */
public class ADX extends BaseIndicator {

    public ADX(int period) {
        super("ADX", period);
    }

    @Override
    public void calculate(List<CandleData> candles) {
        if (candles == null || candles.size() < period + 1) {
            return;
        }

        int n = candles.size();
        List<Double> trueRanges = new ArrayList<>();
        List<Double> plusDMs = new ArrayList<>();
        List<Double> minusDMs = new ArrayList<>();

        for (int i = 0; i < n; i++) {
            CandleData current = candles.get(i);
            double tr;
            double plusDM = 0.0;
            double minusDM = 0.0;

            if (i == 0) {
                tr = current.highPrice() - current.lowPrice();
            } else {
                CandleData prev = candles.get(i - 1);
                tr = Math.max(
                        current.highPrice() - current.lowPrice(),
                        Math.max(
                                Math.abs(current.highPrice() - prev.closePrice()),
                                Math.abs(current.lowPrice() - prev.closePrice())));

                double upMove = current.highPrice() - prev.highPrice();
                double downMove = prev.lowPrice() - current.lowPrice();

                if (upMove > 0.0 && upMove > downMove) {
                    plusDM = upMove;
                }
                if (downMove > 0.0 && downMove > upMove) {
                    minusDM = downMove;
                }
            }

            trueRanges.add(tr);
            plusDMs.add(plusDM);
            minusDMs.add(minusDM);
        }

        double[] trSmoothed = smoothWilder(trueRanges, period);
        double[] plusDMSmoothed = smoothWilder(plusDMs, period);
        double[] minusDMSmoothed = smoothWilder(minusDMs, period);

        double[] plusDI = new double[n];
        double[] minusDI = new double[n];
        double[] dx = new double[n];

        for (int i = 0; i < n; i++) {
            plusDI[i] = trSmoothed[i] != 0.0 ? (100.0 * plusDMSmoothed[i] / trSmoothed[i]) : 0.0;
            minusDI[i] = trSmoothed[i] != 0.0 ? (100.0 * minusDMSmoothed[i] / trSmoothed[i]) : 0.0;

            double diSum = plusDI[i] + minusDI[i];
            dx[i] = diSum != 0.0 ? 100.0 * Math.abs(plusDI[i] - minusDI[i]) / diSum : 0.0;
        }

        double[] adx = smoothWilder(toList(dx), period);

        values.put("+DI", plusDI);
        values.put("-DI", minusDI);
        values.put("ADX", adx);
        calculated = true;
    }

    private double[] smoothWilder(List<Double> data, int window) {
        double[] result = new double[data.size()];
        if (data.size() < window || window <= 0) {
            for (int i = 0; i < data.size(); i++) {
                result[i] = data.get(i);
            }
            return result;
        }

        double sum = 0.0;
        for (int i = 0; i < window; i++) {
            sum += data.get(i);
        }
        result[window - 1] = sum / window;

        for (int i = window; i < data.size(); i++) {
            result[i] = (result[i - 1] * (window - 1) + data.get(i)) / window;
        }

        for (int i = 0; i < window - 1; i++) {
            result[i] = result[window - 1];
        }

        return result;
    }

    private List<Double> toList(double[] values) {
        List<Double> out = new ArrayList<>(values.length);
        for (double value : values) {
            out.add(value);
        }
        return out;
    }
}
