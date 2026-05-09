package org.investpro.risk;

import org.investpro.data.CandleData;

import java.util.ArrayList;
import java.util.List;

/**
 * Calculates market volatility using ATR (Average True Range)
 * ATR is an institutional standard for volatility measurement
 */
public class VolatilityCalculator {

    private static final int DEFAULT_PERIOD = 14;

    /**
     * Calculate ATR (Average True Range) as percentage of price
     */
    public static double calculateATRPercent(List<CandleData> candles) {
        if (candles == null || candles.size() < 2) {
            return 2.0; // Default volatility
        }

        double atr = calculateATR(candles, DEFAULT_PERIOD);

        if (candles.isEmpty()) {
            return 2.0;
        }

        double lastClose = candles.getLast().closePrice();
        if (lastClose <= 0) {
            return 2.0;
        }

        double atrPercent = (atr / lastClose) * 100.0;
        return Math.max(0.5, Math.min(atrPercent, 20.0)); // Cap at 0.5% - 20%
    }

    /**
     * Calculate ATR value
     */
    public static double calculateATR(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period) {
            return 0;
        }

        List<Double> trueRanges = new java.util.ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            double tr = getTr(candles, i);

            trueRanges.add(tr);
        }

        // Calculate SMA of True Range
        if (trueRanges.size() < period) {
            return 0;
        }

        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += trueRanges.get(i);
        }

        return sum / period;
    }

    private static double getTr(List<CandleData> candles, int i) {
        CandleData current = candles.get(i);
        CandleData previous = candles.get(i - 1);

        double high = current.highPrice();
        double low = current.lowPrice();
        double prevClose = previous.closePrice();

        // True Range = max(high - low, |high - prevClose|, |low - prevClose|)
        return Math.max(
                high - low,
                Math.max(
                        Math.abs(high - prevClose),
                        Math.abs(low - prevClose)));
    }

    /**
     * Calculate volatility using standard deviation (percentage)
     */
    public static double calculateVolatilityStdDev(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period) {
            return 2.0;
        }

        // Get closing prices
        List<Double> closes = new ArrayList<>();
        for (CandleData candle : candles) {
            closes.add(candle.closePrice());
        }

        // Calculate returns
        List<Double> returns = new ArrayList<>();
        for (int i = 1; i < closes.size(); i++) {
            double ret = (closes.get(i) - closes.get(i - 1)) / closes.get(i - 1);
            returns.add(ret);
        }

        if (returns.isEmpty()) {
            return 2.0;
        }

        // Calculate standard deviation
        double mean = returns.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = returns.stream()
                .mapToDouble(r -> Math.pow(r - mean, 2))
                .average()
                .orElse(0);

        double stdDev = Math.sqrt(variance) * 100; // Convert to percentage
        return Math.max(0.5, Math.min(stdDev, 20.0)); // Cap at 0.5% - 20%
    }

    /**
     * Calculate Bollinger Band width as percentage
     */
    public static double calculateBollingerBandWidth(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period) {
            return 2.0;
        }

        // Calculate SMA
        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).closePrice();
        }
        double sma = sum / period;

        // Calculate standard deviation
        double sumSqDiff = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double diff = candles.get(i).closePrice() - sma;
            sumSqDiff += diff * diff;
        }
        double stdDev = Math.sqrt(sumSqDiff / period);

        // Upper and lower bands
        double upper = sma + (2 * stdDev);
        double lower = sma - (2 * stdDev);

        // Band width as percentage
        double bandwidth = ((upper - lower) / sma) * 100;
        return Math.max(0.5, Math.min(bandwidth, 20.0));
    }

    /**
     * Composite volatility measure (average of ATR and StdDev)
     */
    public static double calculateCompositeVolatility(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return 2.0;
        }

        double atrVol = calculateATRPercent(candles);
        double stdDevVol = calculateVolatilityStdDev(candles, DEFAULT_PERIOD);
        double bbVol = calculateBollingerBandWidth(candles, DEFAULT_PERIOD);

        // Weighted average (ATR 50%, StdDev 30%, BB 20%)
        return (atrVol * 0.5) + (stdDevVol * 0.3) + (bbVol * 0.2);
    }

    /**
     * Get volatility classification
     */
    public static String classifyVolatility(double volatility) {
        if (volatility < 1.0) {
            return "VERY_LOW";
        } else if (volatility < 2.0) {
            return "LOW";
        } else if (volatility < 4.0) {
            return "NORMAL";
        } else if (volatility < 7.0) {
            return "HIGH";
        } else {
            return "VERY_HIGH";
        }
    }
}
