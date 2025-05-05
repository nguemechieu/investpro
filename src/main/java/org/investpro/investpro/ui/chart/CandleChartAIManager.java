// Refactored CandleChartAIManager.java
package org.investpro.investpro.ui.chart;

import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import org.investpro.grpc.MarketDataRequest;
import org.investpro.investpro.AIPredictorClient;
import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.ErrorManager;

@Getter
@Setter
public class CandleChartAIManager {

    private static final int INITIAL_DELAY_SEC = 5;
    private static final int INTERVAL_SEC = 10;

    private final AIPredictorClient aiPredictorClient;
    private final List<CandleData> candles;
    private final CandleStickChart chart;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ErrorManager logger = new ErrorManager();

    public CandleChartAIManager(List<CandleData> candles, CandleStickChart chart) {
        Objects.requireNonNull(candles, "Candles list must not be null");
        this.candles = candles;
        this.chart = chart;
        this.aiPredictorClient = new AIPredictorClient("localhost", 50051);
        processInitialCandles();
        startScheduler();
    }

    private void processInitialCandles() {
        sendCandleForPredictions(candles);
    }

    private void startScheduler() {
        scheduler.scheduleAtFixedRate(this::analyzeCandles, INITIAL_DELAY_SEC, INTERVAL_SEC, TimeUnit.SECONDS);
    }

    public void stopScheduler() {
        scheduler.shutdownNow();
    }

    void analyzeCandles() {
        if (candles.size() < 20) return;
        CompletableFuture.runAsync(() -> sendCandleForPredictions(candles));
    }

    private void sendCandleForPredictions(@NotNull List<CandleData> candles) {
        try {
            float rsi = calculateRSI(candles, 14);
            float atr = calculateATR(candles, 14);
            float stoch = calculateStochastic(candles, 14);
            float[] bb = calculateBollingerBands(candles, 20);
            float macd = calculateMacd(candles, 12, 26);

            for (int i = 0; i < candles.size(); i++) {
                CandleData candle = candles.get(i);
                MarketDataRequest request = MarketDataRequest.newBuilder()
                        .setOpen(candle.getOpenPrice())
                        .setClose(candle.getClosePrice())
                        .setHigh(candle.getHighPrice())
                        .setLow(candle.getLowPrice())
                        .setVolume(candle.getVolume())
                        .setRsi(rsi)
                        .setAtr(atr)
                        .setMacd(macd)
                        .setStoch(stoch)
                        .setBbUpper(bb[0])
                        .setBbLower(bb[1])
                        .build();

                String[] result = aiPredictorClient.predict(request);
                if (!result[0].equalsIgnoreCase("unknown")) {
                    int finalI = i;
                    Platform.runLater(() -> drawPrediction(finalI, candle.getClosePrice(), result));
                }
            }
        } catch (Exception e) {
            logger.error(e.getMessage(), e, 500);
        }
    }

    private float calculateRSI(List<CandleData> data, int period) {
        if (data.size() < period + 1) return 0f;
        double gain = 0, loss = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double change = data.get(i).getClosePrice() - data.get(i - 1).getClosePrice();
            if (change >= 0) gain += change;
            else loss -= change;
        }
        double rs = loss == 0 ? 100 : gain / loss;
        return (float) (100 - (100 / (1 + rs)));
    }

    private float calculateATR(List<CandleData> data, int period) {
        if (data.size() < period + 1) return 0f;
        double sum = 0;
        for (int i = data.size() - period; i < data.size(); i++) {
            double high = data.get(i).getHighPrice();
            double low = data.get(i).getLowPrice();
            double prevClose = data.get(i - 1).getClosePrice();
            sum += Math.max(high - low, Math.max(Math.abs(high - prevClose), Math.abs(low - prevClose)));
        }
        return (float) (sum / period);
    }

    private float calculateStochastic(List<CandleData> data, int period) {
        if (data.size() < period) return 0f;
        double high = data.subList(data.size() - period, data.size()).stream().mapToDouble(CandleData::getHighPrice).max().orElse(1);
        double low = data.subList(data.size() - period, data.size()).stream().mapToDouble(CandleData::getLowPrice).min().orElse(0);
        double close = data.get(data.size() - 1).getClosePrice();
        return (float) ((close - low) / (high - low) * 100);
    }

    private float[] calculateBollingerBands(List<CandleData> data, int period) {
        if (data.size() < period) return new float[]{0f, 0f};
        List<CandleData> sub = data.subList(data.size() - period, data.size());
        double sma = sub.stream().mapToDouble(CandleData::getClosePrice).average().orElse(0);
        double std = Math.sqrt(sub.stream().mapToDouble(d -> Math.pow(d.getClosePrice() - sma, 2)).average().orElse(0));
        return new float[]{(float) (sma + 2 * std), (float) (sma - 2 * std)};
    }

    private float calculateMacd(List<CandleData> data, int fast, int slow) {
        return (float) (calculateEMA(data, fast) - calculateEMA(data, slow));
    }

    private double calculateEMA(List<CandleData> data, int period) {
        if (data.size() < 10) return 0;
        double multiplier = 2.0 / (period + 1);
        double ema = data.get(data.size() - period).getClosePrice();
        for (int i = data.size() - period + 1; i < data.size(); i++) {
            double close = data.get(i).getClosePrice();
            ema = ((close - ema) * multiplier) + ema;
        }
        return ema;
    }

    private void drawPrediction(int index, Number price, String[] result) {
        if (index >= candles.size() || CandleStickChart.getCanvas() == null) return;

        GraphicsContext gc = CandleStickChart.getCanvas().getGraphicsContext2D();
        double chartHeight = 800, chartWidth = 1500;
        double max = candles.stream().mapToDouble(CandleData::getHighPrice).max().orElse(1);
        double min = candles.stream().mapToDouble(CandleData::getLowPrice).min().orElse(0);
        double priceRange = max - min;
        double pixelsPerPrice = chartHeight / priceRange;
        double y = (max - price.doubleValue()) * pixelsPerPrice;
        double x = chartWidth - (candles.size() - index) * 10;

        String prediction = result[0];
        float confidence = Float.parseFloat(result[1]);

        gc.setFill(prediction.equalsIgnoreCase("up") ? Color.LIMEGREEN : Color.RED);
        gc.setFont(javafx.scene.text.Font.font(14));
        gc.fillText(prediction.equalsIgnoreCase("up") ? "▲" : "▼", x + 1, prediction.equalsIgnoreCase("up") ? y - 5 : y + 15);
        gc.setFill(Color.GRAY);
        gc.fillText(String.format("%.0f%%", confidence * 100), x, y + (prediction.equalsIgnoreCase("up") ? -20 : 30));
    }
}