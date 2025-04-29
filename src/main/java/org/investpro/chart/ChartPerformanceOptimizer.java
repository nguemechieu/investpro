package org.investpro.chart;

import javafx.scene.chart.XYChart;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChartPerformanceOptimizer {

    private final XYChart<String, Number> chart;

    private int maxCandles = 500; // default max visible candles

    public ChartPerformanceOptimizer(XYChart<String, Number> chart) {
        this.chart = chart;
    }

    public void optimize() {
        if (chart.getData().isEmpty()) return;
        XYChart.Series<String, Number> series = chart.getData().getFirst();

        while (series.getData().size() > maxCandles) {
            series.getData().removeFirst(); // Remove oldest
        }
    }

}
