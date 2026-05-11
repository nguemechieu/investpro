package org.investpro.indicators;

import org.investpro.data.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Calculates technical indicators from candlestick data
 */
public class IndicatorCalculator {

    /**
     * Calculate Simple Moving Average
     */
    public static List<Double> calculateSMA(List<CandleData> candles, int period) {
        List<Double> sma = new ArrayList<>();

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                sma.add(null);
            } else {
                double sum = 0;
                for (int j = i - period + 1; j <= i; j++) {
                    sum += candles.get(j).closePrice();
                }
                sma.add(sum / period);
            }
        }
        return sma;
    }

    /**
     * Calculate Exponential Moving Average from CandleData
     */
    public static @NotNull List<Double> calculateEMA(@NotNull List<CandleData> candles, int period) {
        List<Double> ema = new ArrayList<>();

        if (candles.isEmpty())
            return ema;

        double multiplier = 2.0 / (period + 1);

        // Calculate initial SMA
        double sum = 0;
        for (int i = 0; i < period && i < candles.size(); i++) {
            sum += candles.get(i).closePrice();
        }
        double firstEMA = sum / period;

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                ema.add(null);
            } else if (i == period - 1) {
                ema.add(firstEMA);
            } else {
                double newEMA = candles.get(i).closePrice() * multiplier + ema.get(i - 1) * (1 - multiplier);
                ema.add(newEMA);
            }
        }
        return ema;
    }

    /**
     * Calculate Exponential Moving Average from Double values
     */
    public static List<Double> calculateEMAFromValues(List<Double> values, int period) {
        List<Double> ema = new ArrayList<>();

        if (values.isEmpty())
            return ema;

        double multiplier = 2.0 / (period + 1);

        // Calculate initial SMA
        double sum = 0;
        int count = 0;
        for (int i = 0; i < period && i < values.size(); i++) {
            Double value = values.get(i);
            if (value != null) {
                sum += value;
                count++;
            }
        }
        double firstEMA = count > 0 ? sum / count : 0.0;

        for (int i = 0; i < values.size(); i++) {
            Double value = values.get(i);
            if (value == null) {
                ema.add(null);
            } else if (i < period - 1) {
                ema.add(null);
            } else if (i == period - 1) {
                ema.add(firstEMA);
            } else {
                Double prevEMA = ema.get(i - 1);
                if (prevEMA != null) {
                    double newEMA = value * multiplier + prevEMA * (1 - multiplier);
                    ema.add(newEMA);
                } else {
                    ema.add(null);
                }
            }
        }
        return ema;
    }

    /**
     * Calculate Relative Strength Index
     */
    public static List<Double> calculateRSI(List<CandleData> candles, int period) {
        List<Double> rsi = new ArrayList<>();

        if (candles.size() < period + 1) {
            for (int i = 0; i < candles.size(); i++) {
                rsi.add(null);
            }
            return rsi;
        }

        double avgGain = 0;
        double avgLoss = 0;

        // Calculate first average gain and loss
        for (int i = 1; i <= period; i++) {
            double change = candles.get(i).closePrice() - candles.get(i - 1).closePrice();
            if (change > 0) {
                avgGain += change;
            } else {
                avgLoss += Math.abs(change);
            }
        }
        avgGain /= period;
        avgLoss /= period;

        // Fill with nulls until we have enough data
        for (int i = 0; i < period; i++) {
            rsi.add(null);
        }

        // Calculate RSI for remaining values
        for (int i = period; i < candles.size(); i++) {
            double change = candles.get(i).closePrice() - candles.get(i - 1).closePrice();

            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period;
                avgLoss = (avgLoss * (period - 1)) / period;
            } else {
                avgGain = (avgGain * (period - 1)) / period;
                avgLoss = (avgLoss * (period - 1) + Math.abs(change)) / period;
            }

            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsiValue = 100 - (100 / (1 + rs));
            rsi.add(rsiValue);
        }

        return rsi;
    }

    /**
     * Calculate MACD (Moving Average Convergence Divergence)
     */
    public static @NotNull Map<String, List<Double>> calculateMACD(List<CandleData> candles) {
        int fastPeriod = 12;
        int slowPeriod = 26;
        int signalPeriod = 9;

        List<Double> fastEMA = calculateEMA(candles, fastPeriod);
        List<Double> slowEMA = calculateEMA(candles, slowPeriod);

        List<Double> macdLine = new ArrayList<>();
        for (int i = 0; i < candles.size(); i++) {
            if (fastEMA.get(i) != null && slowEMA.get(i) != null) {
                macdLine.add(fastEMA.get(i) - slowEMA.get(i));
            } else {
                macdLine.add(null);
            }
        }

        List<Double> signalLine = calculateEMAFromValues(macdLine.stream()
                .filter(Objects::nonNull)
                .toList(), signalPeriod);

        // Pad signalLine
        while (signalLine.size() < candles.size()) {
            signalLine.addFirst(null);
        }

        Map<String, List<Double>> result = new HashMap<>();
        result.put("macd", macdLine);
        result.put("signal", signalLine);
        return result;
    }

    /**
     * Calculate Bollinger Bands
     */
    public static Map<String, List<Double>> calculateBollingerBands(List<CandleData> candles, int period,
            double stdDev) {
        List<Double> sma = calculateSMA(candles, period);
        List<Double> upper = new ArrayList<>();
        List<Double> lower = new ArrayList<>();

        for (int i = 0; i < candles.size(); i++) {
            if (i < period - 1) {
                upper.add(null);
                lower.add(null);
            } else {
                double sumSqDiff = 0;
                for (int j = i - period + 1; j <= i; j++) {
                    double diff = candles.get(j).closePrice() - sma.get(i);
                    sumSqDiff += diff * diff;
                }
                double std = Math.sqrt(sumSqDiff / period);
                upper.add(sma.get(i) + (stdDev * std));
                lower.add(sma.get(i) - (stdDev * std));
            }
        }

        Map<String, List<Double>> result = new HashMap<>();
        result.put("upper", upper);
        result.put("middle", sma);
        result.put("lower", lower);
        return result;
    }
}
