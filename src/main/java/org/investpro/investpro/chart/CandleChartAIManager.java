package org.investpro.investpro.chart;

import javafx.application.Platform;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.paint.Color;
import org.investpro.investpro.TooltipHelper;
import org.investpro.investpro.ai.InvestProAIBacktester;
import org.investpro.investpro.ai.InvestProAIPredictorClient;
import org.investpro.investpro.model.Candle;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.stream.Collectors;

public class CandleChartAIManager {

    private final XYChart<String, Number> chart;
    private final List<Candle> candles;
    private final InvestProAIPredictorClient aiClient = new InvestProAIPredictorClient();
    private Number price;

    public CandleChartAIManager(XYChart<String, Number> chart, List<Candle> candles) {
        this.chart = chart;
        this.candles = candles;

    }

    public void analyzeCandles() {
        if (candles.size() < 20) {
            return; // need at least 20 candles for good feature extraction
        }

        // Get last 20 candles
        List<Candle> lastCandles = candles.subList(candles.size() - 20, candles.size());

        // Extract features
        List<Double> features = lastCandles.stream()
                .map(c -> c.getClose().doubleValue()) // simplistic for now (or call real FeatureExtractor)
                .collect(Collectors.toList());

        // Call AI Predictor
        var predictionResult = aiClient.predict(features);

        if (!predictionResult.prediction().equalsIgnoreCase("unknown")) {
            int latestIndex = candles.size() - 1;
            Candle latestCandle = candles.get(latestIndex);

            Platform.runLater(() -> {
                drawPrediction(latestIndex, latestCandle.getClose(), predictionResult);
            });
        }
    }

    private void drawPrediction(int index, Number price, InvestProAIPredictorClient.PredictionResult predictionResult) {
        if (chart.getData().isEmpty() || index >= chart.getData().get(0).getData().size()) {
            return;
        }

        XYChart.Series<String, Number> series = chart.getData().get(0);
        XYChart.Data<String, Number> dataPoint = series.getData().get(index);

        // Choose color and arrow text based on prediction
        String arrow = predictionResult.prediction().equalsIgnoreCase("up") ? "▲" : "▼";
        Color color = predictionResult.prediction().equalsIgnoreCase("up") ? Color.LIMEGREEN : Color.RED;

        // Create a label node for the arrow
        Label arrowLabel = new Label(arrow);
        arrowLabel.setStyle("-fx-font-size: 18px; -fx-text-fill: " + toRgbString(color) + ";");

        // Adjust position
        if (predictionResult.prediction().equalsIgnoreCase("up")) {
            arrowLabel.setTranslateY(-18); // above the candle
        } else {
            arrowLabel.setTranslateY(18);  // below the candle
        }
        List<Candle> historicalCandles = loadYourCandles(); // however you load them
        InvestProAIBacktester backtester = new InvestProAIBacktester();
        backtester.runBacktest(historicalCandles);

        // Attach tooltip
        TooltipHelper.attachTooltip(arrowLabel,
                "AI Prediction: " + predictionResult.prediction().toUpperCase() +
                        "\nConfidence: " + String.format("%.2f", predictionResult.confidence() * 100) + "%");

        // Attach to data point
        dataPoint.setNode(arrowLabel);
    }

    private List<Candle> loadYourCandles() {

        return candles;
    }

    private @NotNull String toRgbString(@NotNull Color c) {
        return "rgb("
                + (int) (c.getRed() * 255) + ","
                + (int) (c.getGreen() * 255) + ","
                + (int) (c.getBlue() * 255) + ")";
    }

}
