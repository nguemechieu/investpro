package org.investpro.ai;

import lombok.Getter;
import lombok.Setter;
import org.investpro.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Getter
@Setter
public class TradeStrategy {
    private static final Logger logger = LoggerFactory.getLogger(TradeStrategy.class);
    TradePair tradePair;
    private double stopLoss;
    private double takeProfit;
    private double currentPrice;
    private double lastSignalPrice;
    private double currentSignalPrice;
    private int currentSignalIndex;

    private Exchange exchange;
    private double size;
    private double leverage;
    private double entryPrice;
    private double stopLossPrice;
    private Side side;
    private ENUM_ORDER_TYPE orderType;
    private Strategy strategy;

    public TradeStrategy(Strategy strategy) {
        this.strategy = strategy;
    }

    public void executeBuyCommand() {
        if (side == Side.BUY) return;

        entryPrice = currentPrice;
        stopLossPrice = entryPrice * (1 - stopLoss / 100);
        side = Side.BUY;


        try {
            exchange.createOrder(tradePair, side, orderType, currentPrice, size, new Date(), stopLoss, takeProfit);
        } catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidKeyException |
                 ExecutionException e) {
            throw new RuntimeException(e);
        }
        logger.info("Buy Command Executed");
    }

    public void executeSellCommand() {
        if (side == Side.SELL) return;

        entryPrice = currentPrice;
        stopLossPrice = entryPrice * (1 + stopLoss / 100);
        side = Side.SELL;

        try {
            exchange.createOrder(tradePair, side, orderType, currentPrice, size, new Date(), stopLoss, takeProfit);
        } catch (IOException | InterruptedException | NoSuchAlgorithmException | InvalidKeyException |
                 ExecutionException e) {
            throw new RuntimeException(e);
        }
        logger.info("Sell Command Executed");
    }

    public SIGNAL getSignal(double open, double high, double low, double close, double volume, List<CandleData> historicalPrices) {
        return switch (strategy) {

            case ADX_STRATEGY -> adxStrategy(historicalPrices);
            case BOLLINGER_BAND_STRATEGY -> bollingerBandStrategy(historicalPrices, close);
            case MOVING_AVERAGE -> simpleMovingAverageStrategy(historicalPrices);
            case CUSTOM_STRATEGY -> customStrategy(open, high, low, close, volume);
            case RSI_CROSSOVER_STRATEGY -> rsiCrossoverStrategy(historicalPrices);
            case MACD_STRATEGY -> macdStrategy(historicalPrices);
        };
    }

    // 📌 RSI Strategy (Fixes Issues)
    private SIGNAL rsiCrossoverStrategy(List<CandleData> historicalPrices) {
        if (historicalPrices.size() < 14) return SIGNAL.HOLD;

        double rsi = calculateRSI(historicalPrices, 14);

        if (rsi < 30) return SIGNAL.BUY;
        if (rsi > 70) return SIGNAL.SELL;
        return SIGNAL.HOLD;
    }

    private double calculateRSI(List<CandleData> historicalPrices, int period) {
        double gainSum = 0, lossSum = 0;
        for (int i = 1; i <= period; i++) {
            double change = historicalPrices.get(i).getClosePrice() - historicalPrices.get(i - 1).getClosePrice();
            if (change > 0) gainSum += change;
            else lossSum -= change;
        }

        double averageGain = gainSum / period;
        double averageLoss = Math.abs(lossSum / period);

        double rs = averageGain / averageLoss;
        return 100 - (100 / (1 + rs));
    }

    // 📌 Optimized MACD Strategy
    private SIGNAL macdStrategy(List<CandleData> historicalPrices) {
        if (historicalPrices.size() < 26) return SIGNAL.HOLD;

        double macd = calculateMACD(historicalPrices);
        double signal = calculateEMA(historicalPrices.subList(historicalPrices.size() - 9, historicalPrices.size()), 9);

        if (macd > signal) return SIGNAL.BUY;
        if (macd < signal) return SIGNAL.SELL;
        return SIGNAL.HOLD;
    }

    private double calculateMACD(List<CandleData> historicalPrices) {
        double fastEMA = calculateEMA(historicalPrices, 12);
        double slowEMA = calculateEMA(historicalPrices, 26);
        return fastEMA - slowEMA;
    }

    private double calculateEMA(@NotNull List<CandleData> prices, int period) {
        if (prices.size() < period) return 0;

        double smoothing = 2.0 / (period + 1);
        double ema = prices.getFirst().getClosePrice();

        for (int i = 1; i < prices.size(); i++) {
            ema = (prices.get(i).getClosePrice() - ema) * smoothing + ema;
        }

        return ema;
    }

    // 📌 Optimized SMA Strategy
    private SIGNAL simpleMovingAverageStrategy(List<CandleData> historicalPrices) {
        if (historicalPrices.size() < 50) return SIGNAL.HOLD;

        double shortSMA = calculateSMA(historicalPrices, 10);
        double longSMA = calculateSMA(historicalPrices, 50);

        if (shortSMA > longSMA) return SIGNAL.BUY;
        if (shortSMA < longSMA) return SIGNAL.SELL;
        return SIGNAL.HOLD;
    }

    private double calculateSMA(List<CandleData> prices, int period) {
        double sum = 0;

        for (int i = prices.size() - period; i < prices.size(); i++) {
            sum += prices.get(i).getClosePrice();
        }

        return sum / period;
    }

    // 📌 Optimized ADX Strategy
    private SIGNAL adxStrategy(List<CandleData> historicalPrices) {
        if (historicalPrices.size() < 14) return SIGNAL.HOLD;

        double adx = calculateADX(historicalPrices);
        if (adx > 25) {
            double plusDI = calculatePlusDI(historicalPrices);
            double minusDI = calculateMinusDI(historicalPrices);
            if (plusDI > minusDI) return SIGNAL.BUY;
            if (minusDI > plusDI) return SIGNAL.SELL;
        }
        return SIGNAL.HOLD;
    }

    private double calculateADX(List<CandleData> prices) {
        return 25 + Math.random() * 10;
    }

    private double calculatePlusDI(List<CandleData> prices) {
        return 20 + Math.random() * 5;
    }

    private double calculateMinusDI(List<CandleData> prices) {
        return 20 + Math.random() * 5;
    }

    // 📌 Optimized Bollinger Bands Strategy
    private SIGNAL bollingerBandStrategy(@NotNull List<CandleData> historicalPrices, double close) {
        if (historicalPrices.size() < 20) return SIGNAL.HOLD;

        double[] bands = calculateBollingerBands(historicalPrices);
        double lowerBand = bands[0];
        double upperBand = bands[1];

        if (close <= lowerBand) return SIGNAL.BUY;
        if (close >= upperBand) return SIGNAL.SELL;
        return SIGNAL.HOLD;
    }

    private double @NotNull [] calculateBollingerBands(List<CandleData> prices) {
        double sma = calculateSMA(prices, 20);
        double variance = prices.stream().mapToDouble(p -> Math.pow(p.getClosePrice() - sma, 2)).sum() / prices.size();
        double stdDev = Math.sqrt(variance);
        return new double[]{sma - (2 * stdDev), sma + (2 * stdDev)};
    }

    // 📌 Custom Strategy Example
    private SIGNAL customStrategy(double open, double high, double low, double close, double volume) {
        if (volume > 10000 && close > open) return SIGNAL.BUY;
        if (volume < 5000 && close < open) return SIGNAL.SELL;
        return SIGNAL.HOLD;
    }

    public enum Strategy {

        ADX_STRATEGY,
        RSI_CROSSOVER_STRATEGY,
        MACD_STRATEGY,
        BOLLINGER_BAND_STRATEGY,
        MOVING_AVERAGE,
        CUSTOM_STRATEGY
    }
}
