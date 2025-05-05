package org.investpro.investpro.ai;

import org.investpro.grpc.Predict;
import org.investpro.investpro.indicators.IndicatorCalculator;
import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.investpro.investpro.Exchange.logger;

public class InvestProAIBacktester {

    private final InvestProAIPredictor aiClient = new InvestProAIPredictor("localhost", 50051);

    private int totalTrades = 0;
    private int wins = 0;
    private int losses = 0;
    private double cumulativeProfit = 0.0;

    public void runBacktest(@NotNull List<CandleData> candles) {
        if (candles.size() < 21) {
            logger.info("Not enough candles to backtest.");
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

                Predict.MarketDataRequest request = Predict.MarketDataRequest.newBuilder()
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
                List<Predict.MarketDataRequest> requestList = new ArrayList<>();
                requestList.add(request);

                CompletableFuture<List<Predict.PredictionResponse>> prediction = aiClient.streamBatchPredict(requestList);

                CandleData exitCandle = candles.get(i + 1);
                simulateTrade(prediction.get().toString(), latest, exitCandle, csvWriter);
            }

        } catch (IOException e) {
            logger.error("Failed to write CSV: {}", e.getMessage(), e);
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        printReport();
    }

    private void simulateTrade(@NotNull String prediction, CandleData entry, CandleData exit, FileWriter csvWriter) throws IOException {
        if (prediction.equalsIgnoreCase("unknown") || prediction.equalsIgnoreCase("error")) return;

        totalTrades++;
        boolean correct = prediction.equalsIgnoreCase("up") && exit.getClosePrice() > entry.getClosePrice()
                || prediction.equalsIgnoreCase("down") && exit.getClosePrice() < entry.getClosePrice();

        double pnl = exit.getClosePrice() - entry.getClosePrice();
        if (prediction.equalsIgnoreCase("down")) pnl = -pnl;

        if (correct) {
            wins++;
            cumulativeProfit += Math.abs(pnl);
        } else {
            losses++;
            cumulativeProfit -= Math.abs(pnl);
        }

        String result = correct ? "WIN" : "LOSS";

        logger.info(String.format("Trade #%d: Prediction=%s | Entry=%.5f | Exit=%.5f | PnL=%.5f | Result=%s",
                totalTrades, prediction, entry.getClosePrice(), exit.getClosePrice(), pnl, result));

        csvWriter.append(String.format("%d,%s,%.5f,%.5f,%.5f,%s\n",
                totalTrades, prediction, entry.getClosePrice(), exit.getClosePrice(), pnl, result));
    }

    private void printReport() {
        System.out.println("\n--- AI Backtest Report ---");
        System.out.println("Total Trades: " + totalTrades);
        System.out.println("Wins: " + wins);
        System.out.println("Losses: " + losses);

        double winRate = (totalTrades == 0) ? 0 : (wins * 100.0) / totalTrades;
        logger.info(String.format("Win Rate: %.2f%%", winRate));
        logger.info(String.format("Cumulative Profit: %.2f", cumulativeProfit));
        logger.info("--------------------------\n");
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
}