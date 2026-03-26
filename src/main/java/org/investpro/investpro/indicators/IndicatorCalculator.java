package org.investpro.investpro.indicators;

import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
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

    public static @NotNull List<Double> calculateSMASeries(@NotNull List<CandleData> candles, int period) {
        List<Double> series = new ArrayList<>(candles.size());
        double rollingSum = 0;

        for (int i = 0; i < candles.size(); i++) {
            rollingSum += candles.get(i).getClosePrice();
            if (i >= period) {
                rollingSum -= candles.get(i - period).getClosePrice();
            }

            if (i + 1 < period) {
                series.add(null);
            } else {
                series.add(rollingSum / period);
            }
        }

        return series;
    }

    public static @NotNull List<Double> calculateEMASeries(@NotNull List<CandleData> candles, int period) {
        List<Double> series = new ArrayList<>(candles.size());
        if (candles.size() < period) {
            for (int i = 0; i < candles.size(); i++) {
                series.add(null);
            }
            return series;
        }

        double multiplier = 2.0 / (period + 1);
        double seed = 0;
        for (int i = 0; i < period; i++) {
            seed += candles.get(i).getClosePrice();
            series.add(null);
        }

        double ema = seed / period;
        series.set(period - 1, ema);

        for (int i = period; i < candles.size(); i++) {
            ema = ((candles.get(i).getClosePrice() - ema) * multiplier) + ema;
            series.add(ema);
        }

        return series;
    }

    public static @NotNull BollingerBands calculateBollingerBandsSeries(@NotNull List<CandleData> candles, int period) {
        List<Double> upperBand = new ArrayList<>(candles.size());
        List<Double> middleBand = new ArrayList<>(candles.size());
        List<Double> lowerBand = new ArrayList<>(candles.size());

        for (int i = 0; i < candles.size(); i++) {
            if (i + 1 < period) {
                upperBand.add(null);
                middleBand.add(null);
                lowerBand.add(null);
                continue;
            }

            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                sum += candles.get(j).getClosePrice();
            }

            double sma = sum / period;
            double variance = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double deviation = candles.get(j).getClosePrice() - sma;
                variance += deviation * deviation;
            }

            double stdDev = Math.sqrt(variance / period);
            middleBand.add(sma);
            upperBand.add(sma + (2 * stdDev));
            lowerBand.add(sma - (2 * stdDev));
        }

        return new BollingerBands(upperBand, middleBand, lowerBand);
    }

    public record BollingerBands(List<Double> upperBand, List<Double> middleBand, List<Double> lowerBand) {
    }
}
