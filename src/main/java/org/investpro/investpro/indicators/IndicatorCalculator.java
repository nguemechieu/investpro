package org.investpro.investpro.indicators;

import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class IndicatorCalculator {

    public static double calculateMACD(@NotNull List<CandleData> candles) {
        if (candles.size() < 26) return 0;
        double ema12 = calculateEMA(candles, 12);
        double ema26 = calculateEMA(candles, 26);
        return ema12 - ema26;
    }

    public static double calculateEMA(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period) return 0;

        double multiplier = 2.0 / (period + 1);
        double ema = candles.get(candles.size() - period).getClosePrice();

        for (int i = candles.size() - period + 1; i < candles.size(); i++) {
            ema = ((candles.get(i).getClosePrice() - ema) * multiplier) + ema;
        }
        return ema;
    }

    public static double calculateBollingerUpper(List<CandleData> candles, int period) {
        if (candles.size() < period) return 0;

        double sma = calculateSMA(candles, period);
        double stdDev = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double deviation = candles.get(i).getClosePrice() - sma;
            stdDev += deviation * deviation;
        }
        stdDev = Math.sqrt(stdDev / period);
        return sma + 2 * stdDev;
    }

    public static double calculateBollingerLower(List<CandleData> candles, int period) {
        if (candles.size() < period) return 0;

        double sma = calculateSMA(candles, period);
        double stdDev = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double deviation = candles.get(i).getClosePrice() - sma;
            stdDev += deviation * deviation;
        }
        stdDev = Math.sqrt(stdDev / period);
        return sma - 2 * stdDev;
    }

    public static double calculateStochastic(List<CandleData> candles, int period) {
        if (candles.size() < period) return 0;

        double highestHigh = Double.MIN_VALUE;
        double lowestLow = Double.MAX_VALUE;
        double close = candles.get(candles.size() - 1).getClosePrice();

        for (int i = candles.size() - period; i < candles.size(); i++) {
            highestHigh = Math.max(highestHigh, candles.get(i).getHighPrice());
            lowestLow = Math.min(lowestLow, candles.get(i).getLowPrice());
        }

        return (close - lowestLow) / (highestHigh - lowestLow) * 100.0;
    }

    public static double calculateRSI(List<CandleData> candles, int period) {
        if (candles.size() < period + 1) return 0;

        double gain = 0;
        double loss = 0;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            double change = candles.get(i).getClosePrice() - candles.get(i - 1).getClosePrice();
            if (change > 0) gain += change;
            else loss -= change;
        }

        if (loss == 0) return 100;

        double rs = gain / loss;
        return 100 - (100 / (1 + rs));
    }

    private static double calculateSMA(List<CandleData> candles, int period) {
        if (candles.size() < period) return 0;

        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).getClosePrice();
        }
        return sum / period;
    }
}
