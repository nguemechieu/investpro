package org.investpro.ai;

import org.investpro.model.Candle;

import java.util.ArrayList;
import java.util.List;

public class InvestProFeatureExtractor {

    private static final int RSI_PERIOD = 14;
    private static final int EMA_PERIOD = 10;

    public static List<Double> extractFeatures(List<Candle> candles) {
        List<Double> features = new ArrayList<>();

        if (candles.size() < RSI_PERIOD + 2) {
            // Not enough candles to compute features
            return features;
        }

        // --- Extract Latest Candle Features ---
        Candle last = candles.get(candles.size() - 1);
        Candle prev = candles.get(candles.size() - 2);

        // 1. Price Movement
        features.add(last.getClose().doubleValue());
        features.add(last.getOpen().doubleValue());
        features.add(last.getHigh().doubleValue());
        features.add(last.getLow().doubleValue());
        features.add(last.getVolume().doubleValue());

        // 2. Candle body size
        features.add(Math.abs(last.getClose().doubleValue() - last.getOpen().doubleValue()));

        // 3. Candle range (high - low)
        features.add(last.getHigh().doubleValue() - last.getLow().doubleValue());

        // 4. Close - Previous Close (momentum)
        features.add(last.getClose().doubleValue() - prev.getClose().doubleValue());

        // 5. Simple Moving Average (SMA)
        features.add(calculateSMA(candles, EMA_PERIOD));

        // 6. Exponential Moving Average (EMA)
        features.add(calculateEMA(candles, EMA_PERIOD));

        // 7. Relative Strength Index (RSI)
        features.add(calculateRSI(candles, RSI_PERIOD));

        // 8. MACD Value (very basic version)
        features.add(calculateMACD(candles, 12, 26));

        return features;
    }

    private static double calculateSMA(List<Candle> candles, int period) {
        if (candles.size() < period) return 0;
        return candles.subList(candles.size() - period, candles.size())
                .stream()
                .mapToDouble(c -> c.getClose().doubleValue())
                .average()
                .orElse(0);
    }

    private static double calculateEMA(List<Candle> candles, int period) {
        if (candles.size() < period) return 0;

        double k = 2.0 / (period + 1);
        double ema = candles.get(candles.size() - period).getClose().doubleValue();

        for (int i = candles.size() - period + 1; i < candles.size(); i++) {
            double close = candles.get(i).getClose().doubleValue();
            ema = close * k + ema * (1 - k);
        }
        return ema;
    }

    private static double calculateRSI(List<Candle> candles, int period) {
        if (candles.size() <= period) return 50; // Neutral

        double gain = 0;
        double loss = 0;

        for (int i = candles.size() - period; i < candles.size(); i++) {
            double change = candles.get(i).getClose().doubleValue() - candles.get(i - 1).getClose().doubleValue();
            if (change > 0) gain += change;
            else loss -= change;
        }

        if (loss == 0) return 100;
        double rs = gain / loss;
        return 100 - (100 / (1 + rs));
    }

    private static double calculateMACD(List<Candle> candles, int shortPeriod, int longPeriod) {
        double shortEMA = calculateEMA(candles, shortPeriod);
        double longEMA = calculateEMA(candles, longPeriod);
        return shortEMA - longEMA;
    }
}
