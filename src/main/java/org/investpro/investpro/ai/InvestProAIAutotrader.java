package org.investpro.investpro.ai;

import org.investpro.grpc.Predict;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.Exchange;
import org.investpro.investpro.Side;
import org.investpro.investpro.TelegramClient;
import org.investpro.investpro.indicators.IndicatorCalculator;
import org.investpro.investpro.model.Account;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.model.TradePair;
import org.investpro.investpro.ui.chart.CandleStickChart;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.investpro.investpro.Side.BUY;
import static org.investpro.investpro.Side.SELL;

public class InvestProAIAutotrader {
    private static final Logger logger = LoggerFactory.getLogger(InvestProAIAutotrader.class);
    private static final String DEFAULT_PREDICTOR_HOST = System.getProperty("investpro.ai.host", "localhost");
    private static final int DEFAULT_PREDICTOR_PORT = Integer.getInteger("investpro.ai.port", 50051);
    private static final int MIN_CANDLES_FOR_SIGNALS = 20;

    private final CandleStickChart chart;
    private final double maxRiskRatio = 2.0;
    private final InvestProAIPredictor predictor;
    private final TelegramClient telegram;
    double confidenceThreshold = 0.7;
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
        this.telegram = chart.getTelegram();
        this.predictor = new InvestProAIPredictor(DEFAULT_PREDICTOR_HOST, DEFAULT_PREDICTOR_PORT);
    }

    public void onNewCandle(CandleData latestCandle) {
        if (latestCandle == null) {
            return;
        }

        try {
            List<CandleData> candles = chart.getCandleData();
            if (candles == null || candles.size() < MIN_CANDLES_FOR_SIGNALS) {
                return;
            }

            updateAccountBalance();
            if (!predictor.checkHealth()) {
                logger.debug("Skipping AI prediction because the predictor is unavailable.");
                return;
            }

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

            CompletableFuture<List<Predict.PredictionResponse>> future = predictor.streamBatchPredict(requestList);
            future.thenAccept(responses -> {
                if (responses.isEmpty()) {
                    logger.debug("AI predictor returned no responses.");
                    return;
                }

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
                    } catch (Exception ex) {
                        notifyTelegram("Order execution failed: " + ex.getMessage());
                        logger.error("Order execution failed: {}", ex.getMessage(), ex);
                    }
                } else {
                    logger.debug("Skipping trade because prediction confidence {} is below threshold {}.",
                            confidence,
                            confidenceThreshold);
                }

                closeOpenOrders();
            }).exceptionally(ex -> {
                logger.warn("AI prediction failed: {}", ex.getMessage());
                return null;
            });
        } catch (Exception ex) {
            logger.error("AI autotrader failed while processing a new candle: {}", ex.getMessage(), ex);
        }
    }

    private void updateAccountBalance() {
        try {
            List<Account> accounts = chart.getExchange().getAccounts();
            if (!accounts.isEmpty()) {
                Account account = accounts.getFirst();
                accountBalance = account.getBalance();
                equity = account.getEquity();
                balance = account.getBalance();
                profit = account.getProfitability();
                profitOrLoss = account.getPl();
                availableBalance = account.getAvailableBalance();
            }
        } catch (Exception ex) {
            logger.warn("Balance fetch failed: {}", ex.getMessage());
        }
    }

    private double calculateATR(@NotNull List<CandleData> candles, int period) {
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

    private double calculateLotSize(double stopLossPips, double pipValue) {
        double riskAmount = accountBalance * riskPercentage;
        return riskAmount / (stopLossPips * pipValue);
    }

    private boolean validateRiskReward(double tp, double sl) {
        double rrRatio = tp / sl;
        if (rrRatio < 1 || rrRatio > maxRiskRatio) {
            String msg = "Invalid risk/reward ratio: " + rrRatio + ". Trade skipped.";
            notifyTelegram(msg);
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

        if (!validateRiskReward(tp, sl)) {
            return;
        }

        String direction = side == BUY ? "BUY" : "SELL";
        String message = "[AI TRADE] " + direction + " @ " + entryPrice + " | Lot: " + lotSize;
        notifyTelegram(message);

        exchange.createOrder(pair, side, ENUM_ORDER_TYPE.STOP, entryPrice, lotSize, new Date(), sl, tp);
    }

    private void closeOpenOrders() {
        try {
            chart.getExchange().getOpenOrder(chart.getTradePair()).forEach(order -> {
                double rr = order.getTakeProfit() / order.getStopLoss();
                if (rr > maxRiskRatio || rr < 1) {
                    try {
                        chart.getExchange().cancelOrder(order.getLastTransactionID());
                        notifyTelegram("Order closed because the risk/reward ratio moved out of bounds: " + rr);
                    } catch (Exception ex) {
                        notifyTelegram("Failed to close order: " + ex.getMessage());
                    }
                }
            });
        } catch (Exception ex) {
            notifyTelegram("Order check failed: " + ex.getMessage());
        }
    }

    private void notifyTelegram(String message) {
        if (telegram != null) {
            telegram.sendMessage(telegram.getChatId(), message);
        }
    }
}
