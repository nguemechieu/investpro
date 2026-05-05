package org.investpro.investpro.ai;


import org.investpro.investpro.indicators.IndicatorCalculator;
import org.investpro.investpro.models.CandleData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class InvestProAIBacktester {

    private static final Logger logger = LoggerFactory.getLogger(InvestProAIBacktester.class);
    private static final String DEFAULT_AI_HOST = System.getProperty("investpro.ai.host", "localhost");
    private static final int DEFAULT_AI_PORT = Integer.getInteger("investpro.ai.port", 50051);

    private final InvestProAIPredictor aiClient = new InvestProAIPredictor(DEFAULT_AI_HOST, DEFAULT_AI_PORT);

    private int totalTrades = 0;
    private int wins = 0;
    private int losses = 0;
    private double cumulativeProfit = 0.0;

    public void runBacktest(@NotNull List<CandleData> candles) {
        resetStats();

        if (candles.size() < 21) {
            logger.info("Not enough candles to backtest.");
            return;
        }

        if (!PredictorRuntimeManager.ensureAvailable(Duration.ofSeconds(12)) || !aiClient.checkHealth()) {
            logger.warn("Skipping AI backtest because the predictor is unavailable.");
            return;
        }

        try (FileWriter csvWriter = new FileWriter("backtest_results.csv")) {
            csvWriter.append("Trade #,Prediction,Entry,Exit,PnL,Result\n");

            for (int i = 20; i < candles.size() - 1; i++) {
                List<CandleData> recentCandles = candles.subList(i - 20, i);
                CandleData latest = candles.get(i);
                double macd = IndicatorCalculator.calculateMACD(recentCandles);
                double rsi = IndicatorCalculator.calculateRSI(recentCandles, 14);
                double bbUpper = IndicatorCalculator.calculateBollingerUpper(recentCandles, 20);
                double bbLower = IndicatorCalculator.calculateBollingerLower(recentCandles, 20);
                double stoch = IndicatorCalculator.calculateStochastic(recentCandles, 14);
                double atr = calculateATR(recentCandles, 14);

                InvestProAIPredictor.MarketDataRequest request = InvestProAIPredictor.MarketDataRequest.newBuilder()
                        .setOpen(latest.getOpenPrice())
                        .setHigh(latest.getHighPrice())
                        .setLow(latest.getLowPrice())
                        .setClose(latest.getClosePrice())
                        .setVolume(latest.getVolume())
                        .setAtr(atr)
                        .setRsi(rsi)
                        .setBbUpper(bbUpper)
                        .setBbLower(bbLower)
                        .setStoch(stoch)
                        .setMacd(macd)
                        .build();
                List<InvestProAIPredictor.MarketDataRequest> requestList = new ArrayList<>();
                requestList.add(request);

                CompletableFuture<List<InvestProAIPredictor.PredictionResponse>> prediction = aiClient.streamBatchPredict(requestList);
                List<InvestProAIPredictor.PredictionResponse> predictionResponses = prediction.get(5, TimeUnit.SECONDS);
                if (predictionResponses.isEmpty()) {
                    logger.debug("Skipping backtest trade {} because no AI prediction was returned.", i - 19);
                    continue;
                }

                CandleData exitCandle = candles.get(i + 1);
                simulateTrade(predictionResponses.getFirst().getPrediction(), latest, exitCandle, csvWriter);
            }

        } catch (IOException e) {
            logger.error("Failed to write CSV: {}", e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("AI backtest interrupted.", e);
        } catch (ExecutionException | TimeoutException e) {
            logger.error("AI backtest failed while waiting for predictions.", e);
        }

        printReport();
    }

    private void simulateTrade(@NotNull String prediction, CandleData entry, CandleData exit, FileWriter csvWriter) throws IOException {
        if (prediction.equalsIgnoreCase("unknown") || prediction.equalsIgnoreCase("error")) return;

        totalTrades++;
        boolean bullish = prediction.equalsIgnoreCase("BUY") || prediction.equalsIgnoreCase("up");
        boolean bearish = prediction.equalsIgnoreCase("SELL") || prediction.equalsIgnoreCase("down");
        boolean correct = bullish && exit.getClosePrice() > entry.getClosePrice()
                || bearish && exit.getClosePrice() < entry.getClosePrice();

        double pnl = exit.getClosePrice() - entry.getClosePrice();
        if (bearish) pnl = -pnl;

        if (correct) {
            wins++;
            cumulativeProfit += Math.abs(pnl);
        } else {
            losses++;
            cumulativeProfit -= Math.abs(pnl);
        }

        String result = correct ? "WIN" : "LOSS";

        logger.info("Trade #{}: Prediction={} | Entry={} | Exit={} | PnL={} | Result={}",
                totalTrades, prediction, entry.getClosePrice(), exit.getClosePrice(), pnl, result);

        csvWriter.append(String.format("%d,%s,%.5f,%.5f,%.5f,%s%n",
                totalTrades, prediction, entry.getClosePrice(), exit.getClosePrice(), pnl, result));
    }

    private void printReport() {
        System.out.println("\n--- AI Backtest Report ---");
        System.out.println("Total Trades: " + totalTrades);
        System.out.println("Wins: " + wins);
        System.out.println("Losses: " + losses);

        double winRate = (totalTrades == 0) ? 0 : (wins * 100.0) / totalTrades;
        logger.info("Win Rate: {}%", String.format("%.2f", winRate));
        logger.info("Cumulative Profit: {}", String.format("%.2f", cumulativeProfit));
        logger.info("--------------------------");
    }

    private double calculateATR(@NotNull List<CandleData> candles, int period) {
        if (candles.size() < period + 1) return 0;
        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double high = candles.get(i).getHighPrice();
            double low = candles.get(i).getLowPrice();
            double prevClose = candles.get(i - 1).getClosePrice();
            sum += Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }
        return sum / period;
    }

    private void resetStats() {
        totalTrades = 0;
        wins = 0;
        losses = 0;
        cumulativeProfit = 0.0;
    }
}
