package org.investpro.investpro.ai;

import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.Candle;
import javafx.application.Platform;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.XYChart;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class InvestProAIPaperTradingBot {

    private final InvestProAIPredictorClient aiClient = new InvestProAIPredictorClient();
    private final List<Trade> openTrades = new ArrayList<>();
    private final List<Trade> closedTrades = new ArrayList<>();
    private final double stopLossPercent = 0.005; // 0.5% Stop loss
    private final double takeProfitPercent = 0.01; // 1% Take profit
    private double accountBalance = 10_000.0; // Starting paper trading balance
    private double riskPerTrade = 0.02; // Risk 2% per trade
    private XYChart.Series<Number, Number> equityCurveSeries = new XYChart.Series<>();
    private int tradeCounter = 0;

    public InvestProAIPaperTradingBot(LineChart<Number, Number> equityCurveChart) {
        this.equityCurveSeries.setName("Equity Curve");
        equityCurveChart.getData().add(this.equityCurveSeries);
    }

    public void onNewCandle(List<Candle> recentCandles) {
        if (recentCandles.size() < 26) {
            return; // Need at least 26 candles for features
        }

        List<Double> features = InvestProFeatureExtractor.extractFeatures(recentCandles);
        var predictionResult = aiClient.predict(features);

        if (predictionResult.confidence() < 0.7) {
            return; // 🔥 Only trade high confidence signals
        }

        Candle latestCandle = recentCandles.get(recentCandles.size() - 1);
        double entryPrice = latestCandle.getClose().doubleValue();
        double tradeSize = (accountBalance * riskPerTrade) / (entryPrice * stopLossPercent);

        if (predictionResult.prediction().equalsIgnoreCase("up")) {
            openTrades.add(new Trade("BUY", entryPrice, tradeSize));
            System.out.println("📈 AI Bot BUY @" + entryPrice + " (size: " + tradeSize + ")");
        } else if (predictionResult.prediction().equalsIgnoreCase("down")) {
            openTrades.add(new Trade("SELL", entryPrice, tradeSize));
            System.out.println("📉 AI Bot SELL @" + entryPrice + " (size: " + tradeSize + ")");
        }
    }

    public void onCloseCandle(Candle closingCandle) {
        List<Trade> toClose = new ArrayList<>(openTrades);

        for (Trade trade : toClose) {
            double closePrice = closingCandle.getClose().doubleValue();
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
                System.out.println("💰 Closed " + trade.side + " @ " + closePrice + " | Profit: " + String.format("%.2f", profit));
                updateEquityCurve(tradeCounter, accountBalance);
            }
        }
    }

    private void updateEquityCurve(int tradeNum, double balance) {
        Platform.runLater(() -> {
            equityCurveSeries.getData().add(new XYChart.Data<>(tradeNum, balance));
        });
    }

    public void printSummary() {
        double totalProfit = closedTrades.stream().mapToDouble(t -> t.profit).sum();
        System.out.println("\n--- AI Paper Trading Summary ---");
        System.out.println("Total Trades: " + closedTrades.size());
        System.out.println("Total Profit: " + String.format("%.2f", totalProfit));
        System.out.println("Final Account Balance: " + String.format("%.2f", accountBalance));
        System.out.println("-------------------------------\n");
    }

    private static class Trade {
        String side;
        double entryPrice;
        double tradeSize;
        double exitPrice;
        double profit;

        public Trade(String side, double entryPrice, double tradeSize) {
            this.side = side;
            this.entryPrice = entryPrice;
            this.tradeSize = tradeSize;
        }

        public void close(double exitPrice, double profit) {
            this.exitPrice = exitPrice;
            this.profit = profit;
        }
    }
}
