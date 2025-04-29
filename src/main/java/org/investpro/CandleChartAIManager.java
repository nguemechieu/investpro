package org.investpro;

import javafx.application.Platform;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.investpro.model.Candle;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CandleChartAIManager {

    private final XYChart<String, Number> chart;
    private final List<Candle> candles;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CandleChartAIManager(XYChart<String, Number> chart, List<Candle> candles) {
        this.chart = chart;
        this.candles = candles;
    }

    public void analyzeCandles() {
        executorService.submit(() -> {
            for (int i = 5; i < candles.size(); i++) { // Start after some candles exist
                Candle prev = candles.get(i - 1);
                Candle current = candles.get(i);

                // Example AI logic: Detect Bullish Engulfing Pattern
                if (isBullishEngulfing(prev, current)) {
                    int finalI = i;
                    Platform.runLater(() -> drawAISignal(finalI, current.getClose(), Color.LIMEGREEN, "BUY"));
                }

                // Example AI logic: Detect Bearish Engulfing Pattern
                if (isBearishEngulfing(prev, current)) {
                    int finalI = i;
                    Platform.runLater(() -> drawAISignal(finalI, current.getClose(), Color.RED, "SELL"));
                }
            }
        });
    }

    private boolean isBullishEngulfing(Candle prev, Candle current) {
        return prev.getClose().doubleValue() < prev.getOpen().doubleValue() &&
                current.getClose().doubleValue() > current.getOpen().doubleValue() &&
                current.getClose().doubleValue() > prev.getOpen().doubleValue() &&
                current.getOpen().doubleValue() < prev.getClose().doubleValue();
    }

    private boolean isBearishEngulfing(Candle prev, Candle current) {
        return prev.getClose().doubleValue() > prev.getOpen().doubleValue() &&
                current.getClose().doubleValue() < current.getOpen().doubleValue() &&
                current.getClose().doubleValue() < prev.getOpen().doubleValue() &&
                current.getOpen().doubleValue() > prev.getClose().doubleValue();
    }

    private void drawAISignal(int index, Number price, Color color, String label) {
        XYChart.Series<String, Number> series;
        if (chart.getData().isEmpty()) {
            series = new XYChart.Series<>();
            chart.getData().add(series);
        } else {
            series = chart.getData().get(0); // Assume main series is at 0
        }

        if (index >= series.getData().size()) return;

        XYChart.Data<String, Number> data = series.getData().get(index);
        Circle signal = new Circle(5, color);
        signal.setTranslateY(-10); // Slightly above candle
        data.setNode(signal);

        TooltipHelper.attachTooltip(signal, label + "\nPrice: " + price);
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
