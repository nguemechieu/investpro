package org.investpro.investpro;

import javafx.application.Platform;
import javafx.scene.chart.XYChart;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.investpro.investpro.model.CandleData;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CandleChartAIManager {

    private final XYChart<String, Number> chart;
    private final List<CandleData> candles;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public CandleChartAIManager(XYChart<String, Number> chart, List<CandleData> candles) {
        this.chart = chart;
        this.candles = candles;
    }

    public void analyzeCandles() {
        executorService.submit(() -> {
            for (int i = 5; i < candles.size(); i++) { // Start after some candles exist
                CandleData prev = candles.get(i - 1);
                CandleData current = candles.get(i);

                // Example AI logic: Detect Bullish Engulfing Pattern
                if (isBullishEngulfing(prev, current)) {
                    int finalI = i;
                    Platform.runLater(() -> drawAISignal(finalI, current.getClosePrice(), Color.LIMEGREEN, "BUY"));
                }

                // Example AI logic: Detect Bearish Engulfing Pattern
                if (isBearishEngulfing(prev, current)) {
                    int finalI = i;
                    Platform.runLater(() -> drawAISignal(finalI, current.getClosePrice(), Color.RED, "SELL"));
                }
            }
        });
    }

    private boolean isBullishEngulfing(CandleData prev, CandleData current) {
        return prev.getClosePrice() < prev.getOpenPrice() &&
                current.getClosePrice() > current.getOpenPrice() &&
                current.getClosePrice() > prev.getOpenPrice() &&
                current.getOpenPrice() < prev.getClosePrice();
    }

    private boolean isBearishEngulfing(CandleData prev, CandleData current) {
        return prev.getClosePrice() > prev.getOpenPrice() &&
                current.getClosePrice() < current.getOpenPrice() &&
                current.getClosePrice() < prev.getOpenPrice() &&
                current.getOpenPrice() > prev.getClosePrice();
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
