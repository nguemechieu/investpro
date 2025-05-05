package org.investpro.investpro.ai;

import org.investpro.grpc.Predict;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.Side;
import org.investpro.investpro.indicators.IndicatorCalculator;
import org.investpro.investpro.model.Account;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.investpro.investpro.ui.chart.CandleStickChart;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.investpro.investpro.Side.BUY;
import static org.investpro.investpro.Side.SELL;

public class InvestProAIAutotrader {

    private final CandleStickChart chart;
    private final double maxRiskRatio = 2.0;
    double confidenceThreshold = 0.7;
    InvestProAIPredictor predictor;
    double equity;
    double riskPercentage = 0.01;
    double balance;
    double sl;
    double profit;
    double profitOrLoss;
    double availableBalance;
    private double accountBalance = 10000.0;

    public InvestProAIAutotrader(CandleStickChart chart) {
        this.chart = chart;
        this.predictor = new InvestProAIPredictor("localhost", 50051);
    }
    public void onNewCandle(CandleData latestCandle) {
        try {
            List<CandleData> candles = chart.getCandleData();
            if (candles == null || candles.isEmpty()) return;

            updateAccountBalance();

            InvestProAIPredictor investProAIPredictor = new InvestProAIPredictor("localhost", 50051);
            investProAIPredictor.checkHealth();

            // Extract indicators
            double atr = calculateATR(candles, 14);
            double bbLower = IndicatorCalculator.calculateBollingerLower(candles, 20);
            double bbUpper = IndicatorCalculator.calculateBollingerUpper(candles, 20);
            double macd = IndicatorCalculator.calculateMACD(candles);
            double stochastic = IndicatorCalculator.calculateStochastic(candles, 14);
            double rsi = IndicatorCalculator.calculateRSI(candles, 14);

            Predict.MarketDataRequest marketDataRequest = Predict.MarketDataRequest.newBuilder()
                    .setAtr(atr)
                    .setBbLower(bbLower)
                    .setBbUpper(bbUpper)
                    .setMacd(macd)
                    .setStoch(stochastic)
                    .setRsi(rsi)
                    .setClose(latestCandle.getClosePrice())
                    .setHigh(latestCandle.getHighPrice())
                    .setLow(latestCandle.getLowPrice())
                    .setOpen(latestCandle.getOpenPrice())
                    .setVolume(latestCandle.getVolume())
                    .build();

            List<Predict.MarketDataRequest> requestList = new ArrayList<>();
            requestList.add(marketDataRequest);

            CompletableFuture<List<Predict.PredictionResponse>> future = investProAIPredictor.streamBatchPredict(requestList);

            future.thenAccept(responses -> {
                if (!responses.isEmpty()) {
                    Predict.PredictionResponse prediction = responses.get(0);
                    double confidence = prediction.getConfidence();
                    String direction = prediction.getPrediction();

                    if (confidence >= confidenceThreshold) {
                        try {
                            if ("up".equalsIgnoreCase(direction)) {
                                executeOrder(BUY, latestCandle, candles);
                            } else if ("down".equalsIgnoreCase(direction)) {
                                executeOrder(SELL, latestCandle, candles);
                            }
                        } catch (Exception e) {
                            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(),
                                    "❌ Order execution failed: " + e.getMessage());
                        }
                    } else {
                        String message = "ℹ️ Confidence (" + confidence + ") too low. No trade.";
                        chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), message);
                    }
                } else {
                    chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), "⚠️ No prediction received.");
                }

                closeOpenOrders();

            }).exceptionally(e -> {
                chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), "❗ Prediction error: " + e.getMessage());
                return null;
            });


        } catch (Exception e) {
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), "❗ Error: " + e.getMessage());
            throw new RuntimeException(e);
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
                        "💰 Account Updated:\nBalance: $" + balance +
                                "\nEquity: $" + equity +
                                "\nAvailable: $" + availableBalance +
                                "\nP/L: $" + profitOrLoss +
                                "\nProfitability: " + profit + "%");
            }
        } catch (Exception e) {
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), "⚠️ Balance fetch failed: " + e.getMessage());
        }
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

    private double calculateLotSize(double stopLossPips, double pipValue) {
        double riskAmount = accountBalance * riskPercentage;
        return riskAmount / (stopLossPips * pipValue);
    }

    private boolean validateRiskReward(double tp, double sl) {
        double rrRatio = tp / sl;
        if (rrRatio < 1 || rrRatio > maxRiskRatio) {
            String msg = "❗ Invalid Risk/Reward Ratio: " + rrRatio + ". Trade skipped.";
            //   Platform.runLater(() -> new Alert(Alert.AlertType.WARNING, msg).show());
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), msg);
            return false;
        }
        return true;
    }

    private void executeOrder(Side side, CandleData candle, List<CandleData> candles) throws Exception {
        TradePair pair = chart.getTradePair();
        Exchange exchange = chart.getExchange();
        double entryPrice = exchange.fetchLivesBidAsk(pair);
        double atr = calculateATR(candles, 14);
        sl = atr;
        double tp = atr * 2;
        double pipValue = 10.0;
        double lotSize = calculateLotSize(sl, pipValue);

        if (!validateRiskReward(tp, sl)) return;

        String direction = side == BUY ? "BUY" : "SELL";
        String message = "✅ [AI TRADE] " + direction + " @ " + entryPrice + " | Lot: " + lotSize;
        chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), message);

        exchange.createOrder(pair, side, ENUM_ORDER_TYPE.STOP, entryPrice, lotSize, new Date(), sl, tp);
    }

    private void closeOpenOrders() {
        try {
            chart.getExchange().getOpenOrder(chart.getTradePair()).forEach(order -> {
                double rr = order.getTakeProfit() / order.getStopLoss();
                if (rr > maxRiskRatio || rr < 1) {
                    try {
                        chart.getExchange().cancelOrder(order.getLastTransactionID());
                        chart.getTelegram().sendMessage(chart.getTelegram().getChatId(),
                                "❌ Order Closed - R/R Ratio out of bounds: " + rr);
                    } catch (Exception e) {
                        chart.getTelegram().sendMessage(chart.getTelegram().getChatId(),
                                "⚠️ Failed to close order: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            chart.getTelegram().sendMessage(chart.getTelegram().getChatId(), "⚠️ Order check failed: " + e.getMessage());
        }
    }
}
