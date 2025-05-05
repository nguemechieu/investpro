package org.investpro.investpro.ai;

import javafx.application.Platform;
import javafx.scene.control.Alert;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.model.Account;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.Position;
import org.investpro.investpro.model.TradePair;
import org.investpro.investpro.ui.chart.CandleStickChart;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import static org.investpro.investpro.Side.BUY;
import static org.investpro.investpro.Side.SELL;

public class InvestProAIAutotrader {

    private final CandleStickChart chart;
    private final InvestProAIPredictor predictor;
    double confidenceThreshold = 0.7;
    double maxRiskRatio = 2.0;
    double riskPercentage = 0.01;
    double profit;
    double profitOrLoss;
    double availableBalance;
    List<CandleData> candles;
    int period;
    TradePair tradePair;
    private double accountBalance = 10000.0;
    private double equity;
    private double balance;
    private List<Position> positions;

    public InvestProAIAutotrader(CandleStickChart chart) {
        this.chart = chart;
        this.predictor = new InvestProAIPredictor();
    }

    public void onNewCandle(CandleData latestCandle) {
        try {
            List<CandleData> candles = chart.getCandlesData();
            if (candles == null || candles.isEmpty()) {
                System.err.println("❗ No candles loaded yet, skipping autotrade.");
                return;
            }

            updateAccountBalance();

            List<Double> features = InvestProFeatureExtractor.extractFeatures(candles);
            InvestProAIPredictor.PredictionResult result = predictor.predict(features);

            if (result.confidence() >= confidenceThreshold) {
                if ("up".equalsIgnoreCase(result.prediction())) {
                    placeBuyOrder(latestCandle, candles);
                } else if ("down".equalsIgnoreCase(result.prediction())) {
                    placeSellOrder(latestCandle, candles);
                }
            } else {
                String message = "ℹ️ Confidence (" + result.confidence() + ") too low. No trade.";
                Platform.runLater(() -> new Alert(Alert.AlertType.INFORMATION, message).show());
                chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), message);
            }

            monitorOpenPositions();

        } catch (Exception e) {
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), "❗ Error during AI autotrading: " + e.getMessage());
            throw new RuntimeException("❗ Error during AI auto trading: " + e.getMessage());
        }
    }

    private void updateAccountBalance() {
        try {
            List<Account> accounts = chart.getExchange().getAccounts();
            if (!accounts.isEmpty()) {
                Account account = accounts.getFirst();
                this.accountBalance = account.getBalance();
                this.equity = account.getEquity();
                this.balance = account.getBalance();
                this.profit = account.getProfitability();
                this.profitOrLoss = account.getPl();
                this.availableBalance = account.getAvailableBalance();

                chart.getTelegram().sendMessage(chart.getTelegram().getChatId(),
                        "💰 Account Balance Updated:\nBalance: $" + balance +
                                "\nEquity: $" + equity +
                                "\nAvailable: $" + availableBalance +
                                "\nP/L: $" + profitOrLoss +
                                "\nProfitability: " + profit + "%");
            }
        } catch (Exception e) {
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), "⚠️ Failed to fetch account balance: " + e.getMessage());
        }
    }

    private double calculateATR(@NotNull List<CandleData> candles, int period) {
        this.candles = candles;
        this.period = period;
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

    private double calculateLotSize(double stopLossPips, double pipValue) {
        double riskAmount = accountBalance * riskPercentage;
        return riskAmount / (stopLossPips * pipValue);
    }

    private void placeBuyOrder(CandleData candle, List<CandleData> candles) throws IOException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException, InterruptedException {
        tradePair = chart.getTradePair();
        long chatId = chart.getTelegram().getChatId();

        double entryPrice = chart.getExchange().fetchLivesBidAsk(tradePair);
        double atr = calculateATR(candles, 14);
        double takeProfit = atr * 2;
        double pipValue = 10.0;
        double lotSize = calculateLotSize(atr, pipValue);

        if (validateRiskReward(takeProfit, atr)) return;

        String message = "✅ [AI TRADE] BUY @ " + entryPrice + " | Lot: " + lotSize +
                "\nBalance: $" + balance + " | Equity: $" + equity;
        chart.getTelegram().sendMessage(chatId, message);

        chart.getExchange().createOrder(tradePair, BUY, ENUM_ORDER_TYPE.STOP,
                entryPrice, lotSize, new Date(), atr, takeProfit);
    }

    private void placeSellOrder(@NotNull CandleData candle, List<CandleData> candles) throws IOException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException, InterruptedException {
        TradePair tradePair = chart.getTradePair();
        long chatId = chart.getTelegram().getChatId();

        double entryPrice = chart.getExchange().fetchLivesBidAsk(tradePair);
        double atr = calculateATR(candles, 14);
        double takeProfit = atr * 2;
        double pipValue = 10.0;
        double lotSize = calculateLotSize(atr, pipValue);

        if (validateRiskReward(takeProfit, atr)) return;

        String message = "✅ [AI TRADE] SELL @ " + entryPrice + " | Lot: " + lotSize +
                "\nBalance: $" + balance + " | Equity: $" + equity;
        chart.getTelegram().sendMessage(chatId, message);

        chart.getExchange().createOrder(tradePair, SELL, ENUM_ORDER_TYPE.STOP,
                entryPrice, lotSize, new Date(), atr, takeProfit);
    }

    private boolean validateRiskReward(double tp, double sl) {
        double rrRatio = tp / sl;
        if (rrRatio < 1 || rrRatio > maxRiskRatio) {
            String msg = "❗ Invalid Risk/Reward Ratio: " + rrRatio + ". Trade skipped.";
            Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, msg).show());
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), msg);
            return true;
        }
        return false;
    }

    private void monitorOpenPositions() throws IOException, NoSuchAlgorithmException, ExecutionException, InvalidKeyException, InterruptedException {
        chart.getExchange().getOpenOrder(chart.getTradePair()).forEach(order -> {

            try {
                positions = chart.getExchange().getPositions();
            } catch (IOException | InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }

            for (Position position : positions) {
                if (Objects.equals(position.getInstrument(), chart.getTradePair().toString('_'))) {


                    double unrealizedPL = 0;
                    unrealizedPL += position.getUnrealizedPL();
                    if (unrealizedPL <= -accountBalance * riskPercentage || unrealizedPL >= accountBalance * riskPercentage * 2) {
                        closeOrder(position.getLastTransactionID());
                    }
                }
            }
        });
    }

    private void closeOrder(String orderId) {
        try {
            chart.getExchange().cancelOrder(orderId);
            String msg = "🔒 Position closed: Order ID " + orderId;
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), msg);
        } catch (Exception e) {
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), "⚠️ Failed to close order: " + e.getMessage());
        }
    }
}
