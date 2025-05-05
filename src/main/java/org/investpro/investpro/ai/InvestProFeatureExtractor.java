package org.investpro.investpro.ai;


import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class InvestProFeatureExtractor {

    private static final int RSI_PERIOD = 14;
    private static final int EMA_PERIOD = 10;

    public static @NotNull List<Double> extractFeatures(List<CandleData> candles) {

        List<Double> features = new ArrayList<>();

        if (candles.size() < RSI_PERIOD + 2) {
            // Not enough candles to compute features
            return features;
        }

        // --- Extract Latest Candle Features ---
        CandleData last = candles.getLast();
        CandleData prev = candles.get(candles.size() - 2);

        // 1. Price Movement
        features.add(last.getClosePrice());//;;.doubleValue());
        features.add(last.getOpenPrice());//.doubleValue());
        features.add(last.getHighPrice());//.doubleValue());
        features.add(last.getLowPrice());//.doubleValue());
        features.add(last.getVolume());//.doubleValue());

        // 2. Candle body size
        features.add(Math.abs(last.getClosePrice() - last.getOpenPrice()));

        // 3. Candle range (high - low)
        features.add(last.getHighPrice() - last.getLowPrice());
        ;//.doubleValue());

        // 4. Close - Previous Close (momentum)
        features.add(last.getClosePrice() - prev.getClosePrice());

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

    private static double calculateSMA(List<CandleData> candles, int period) {
        if (candles.size() < period) return 0;
        return candles.subList(candles.size() - period, candles.size())
                .stream()
                .mapToDouble(CandleData::getClosePrice)//.doubleValue())
                .average()
                .orElse(0);
    }

    private static double calculateEMA(List<CandleData> candles, int period) {
        if (candles.size() < period) return 0;

        double k = 2.0 / (period + 1);
        double ema = candles.get(candles.size() - period).getClosePrice();
        ;//)//.doubleValue();

        for (int i = candles.size() - period + 1; i < candles.size(); i++) {
            double close = candles.get(i).getClosePrice();//.doubleValue();
            ema = close * k + ema * (1 - k);
        }
        return ema;
    }

    private static double calculateRSI(List<CandleData> candles, int period) {
        if (candles.size() <= period) return 50; // Neutral

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

    private static double calculateMACD(List<CandleData> candles, int shortPeriod, int longPeriod) {
        double shortEMA = calculateEMA(candles, shortPeriod);
        double longEMA = calculateEMA(candles, longPeriod);
        return shortEMA - longEMA;
    }
}
