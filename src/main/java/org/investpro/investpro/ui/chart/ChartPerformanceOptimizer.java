package org.investpro.investpro.ui.chart;

import lombok.Getter;
import lombok.Setter;

import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@Getter
@Setter
public class ChartPerformanceOptimizer {


    private int maxCandles = 500; // default max visible candles

    public ChartPerformanceOptimizer() {


    }

    public void optimize(@NotNull CandleStickChart chart) {
        if (chart.getCandlesData().isEmpty()) return;
        List<CandleData> series = chart.getCandlesData();

        while (series.size() > maxCandles) {
            series.removeFirst(); // Remove oldest
        }
    }

    public void setMaxCandles(double v) {
    }
}
