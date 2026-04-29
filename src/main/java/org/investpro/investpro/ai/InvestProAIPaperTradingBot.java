package org.investpro.investpro.ai;

import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.TelegramClient;
import org.investpro.investpro.indicators.IndicatorCalculator;
import org.investpro.investpro.models.CandleData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Getter
@Setter
public class InvestProAIPaperTradingBot {

    private static final Logger logger = LoggerFactory.getLogger(InvestProAIPaperTradingBot.class);
    private static final String DEFAULT_AI_HOST = System.getProperty("investpro.ai.host", "localhost");
    private static final int DEFAULT_AI_PORT = Integer.getInteger("investpro.ai.port", 50051);

    private final InvestProAIPredictor aiClient = new InvestProAIPredictor(DEFAULT_AI_HOST, DEFAULT_AI_PORT);
    private final List<Trade> openTrades = new ArrayList<>();
    private final List<Trade> closedTrades = new ArrayList<>();
    private final double stopLossPercent = 0.005;
    private final double takeProfitPercent = 0.01;
    private final XYChart.Series<Number, Number> equityCurveSeries = new XYChart.Series<>();
    private double accountBalance = 10_000.0;
    private double riskPerTrade = 0.02;
    private int tradeCounter = 0;

    public InvestProAIPaperTradingBot(TelegramClient telegram, LineChart<Number, Number> equityCurveChart) {
        equityCurveSeries.setName("Equity Curve");
        if (equityCurveChart != null) {
            equityCurveChart.getData().add(equityCurveSeries);
        }
        if (telegram != null && telegram.canSendMessages()) {
            telegram.sendMessage(telegram.getChatId(), "AI paper trading bot started.");
        }
    }

    public void onNewCandle(List<CandleData> recentCandles) {
        if (recentCandles.size() < 26) {
            return;
        }

        if (!PredictorRuntimeManager.ensureAvailable(Duration.ofSeconds(12)) || !aiClient.checkHealth()) {
            logger.debug("Skipping paper-trading prediction because the predictor is unavailable.");
            return;
        }

        InvestProAIPredictor.MarketDataRequest request = buildRequest(recentCandles);
        List<InvestProAIPredictor.PredictionResponse> responses;
        try {
            responses = aiClient.streamBatchPredict(List.of(request)).get(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.warn("Paper-trading prediction interrupted.", e);
            return;
        } catch (ExecutionException | TimeoutException e) {
            logger.warn("Paper-trading prediction failed.", e);
            return;
        }

        if (responses.isEmpty()) {
            logger.debug("Skipping paper-trading decision because no AI prediction was returned.");
            return;
        }

        InvestProAIPredictor.PredictionResponse prediction = responses.getFirst();
        if (prediction.getConfidence() < 0.7) {
            logger.info("Only trade high confidence signals");
            return;
        }

        CandleData latestCandle = recentCandles.getLast();
        double entryPrice = latestCandle.getClosePrice();
        double tradeSize = (accountBalance * riskPerTrade) / (entryPrice * stopLossPercent);

        if (prediction.isBuy() || prediction.getPrediction().equalsIgnoreCase("up")) {
            openTrades.add(new Trade("BUY", entryPrice, tradeSize));
            logger.info("AI Bot BUY @{} (size: {})", entryPrice, tradeSize);
        } else if (prediction.isSell() || prediction.getPrediction().equalsIgnoreCase("down")) {
            openTrades.add(new Trade("SELL", entryPrice, tradeSize));
            logger.info("AI Bot SELL @{} (size: {})", entryPrice, tradeSize);
        }
    }

    public void onCloseCandle(CandleData closingCandle) {
        List<Trade> toClose = new ArrayList<>(openTrades);

        for (Trade trade : toClose) {
            double closePrice = closingCandle.getClosePrice();
            double profit = 0;
            boolean shouldClose = false;

            if (trade.side.equals("BUY")) {
                double change = (closePrice - trade.entryPrice) / trade.entryPrice;
                profit = (closePrice - trade.entryPrice) * trade.tradeSize;

                if (change <= -stopLossPercent || change >= takeProfitPercent) {
                    shouldClose = true;
                }
            } else if (trade.side.equals("SELL")) {
                double change = (trade.entryPrice - closePrice) / trade.entryPrice;
                profit = (trade.entryPrice - closePrice) * trade.tradeSize;

                if (change <= -stopLossPercent || change >= takeProfitPercent) {
                    shouldClose = true;
                }
            }

            if (shouldClose) {
                trade.close(closePrice, profit);
                accountBalance += profit;
                openTrades.remove(trade);
                closedTrades.add(trade);
                tradeCounter++;
                logger.info("Closed {} @ {} | Profit: {}", trade.side, closePrice, String.format("%.2f", profit));
                updateEquityCurve(tradeCounter, accountBalance);
            }
        }
    }

    private void updateEquityCurve(int tradeNum, double balance) {
        Platform.runLater(() -> equityCurveSeries.getData().add(new XYChart.Data<>(tradeNum, balance)));
    }

    public void printSummary() {
        double totalProfit = closedTrades.stream().mapToDouble(t -> t.profit).sum();
        System.out.println("\n--- AI Paper Trading Summary ---");
        System.out.println("Total Trades: " + closedTrades.size());
        System.out.println("Total Profit: " + String.format("%.2f", totalProfit));
        System.out.println("Final Account Balance: " + String.format("%.2f", accountBalance));
        System.out.println("-------------------------------\n");
    }

    private InvestProAIPredictor.MarketDataRequest buildRequest(List<CandleData> recentCandles) {
        List<CandleData> featureCandles = recentCandles.subList(recentCandles.size() - 20, recentCandles.size());
        CandleData latestCandle = recentCandles.getLast();

        return InvestProAIPredictor.MarketDataRequest.newBuilder()
                .setOpen(latestCandle.getOpenPrice())
                .setHigh(latestCandle.getHighPrice())
                .setLow(latestCandle.getLowPrice())
                .setClose(latestCandle.getClosePrice())
                .setVolume(latestCandle.getVolume())
                .setAtr(calculateAtr(recentCandles, 14))
                .setRsi(IndicatorCalculator.calculateRSI(recentCandles, 14))
                .setMacd(IndicatorCalculator.calculateMACD(recentCandles))
                .setStoch(IndicatorCalculator.calculateStochastic(recentCandles, 14))
                .setBbUpper(IndicatorCalculator.calculateBollingerUpper(featureCandles, 20))
                .setBbLower(IndicatorCalculator.calculateBollingerLower(featureCandles, 20))
                .build();
    }

    private double calculateAtr(List<CandleData> candles, int period) {
        if (candles.size() < period + 1) {
            return 0;
        }

        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            double high = candles.get(i).getHighPrice();
            double low = candles.get(i).getLowPrice();
            double prevClose = candles.get(i - 1).getClosePrice();
            sum += Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }
        return sum / period;
    }

    private static class Trade {
        String side;
        double entryPrice;
        double tradeSize;
        double exitPrice;
        double profit;

        Trade(String side, double entryPrice, double tradeSize) {
            this.side = side;
            this.entryPrice = entryPrice;
            this.tradeSize = tradeSize;
        }

        void close(double exitPrice, double profit) {
            this.exitPrice = exitPrice;
            this.profit = profit;
        }
    }
}
