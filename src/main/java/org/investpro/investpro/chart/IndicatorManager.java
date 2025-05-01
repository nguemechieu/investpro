package org.investpro.investpro.chart;

import javafx.scene.chart.XYChart;
import org.investpro.investpro.model.Candle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

public class IndicatorManager {

    private final XYChart<String, Number> chart;

    public IndicatorManager(XYChart<String, Number> chart) {
        this.chart = chart;
    }

    public void addSimpleMovingAverage(List<Candle> candles, int period) {
        if (candles.size() < period) return;

        XYChart.Series<String, Number> maSeries = new XYChart.Series<>();
        maSeries.setName("SMA-" + period);

        for (int i = period - 1; i < candles.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = 0; j < period; j++) {
                sum = sum.add(candles.get(i - j).getClose());
            }
            BigDecimal avg = sum.divide(BigDecimal.valueOf(period), RoundingMode.HALF_UP);

            XYChart.Data<String, Number> point = new XYChart.Data<>(candles.get(i).getTime().toString(), avg);
            maSeries.getData().add(point);
        }

        chart.getData().add(maSeries);
    }

    public void addRSI(List<Candle> candles, int period) {
        if (candles.size() < period + 1) return;

        XYChart.Series<String, Number> rsiSeries = new XYChart.Series<>();
        rsiSeries.setName("RSI-" + period);

        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            double change = candles.get(i).getClose().subtract(candles.get(i - 1).getClose()).doubleValue();
            gains.add(Math.max(0, change));
            losses.add(Math.max(0, -change));
        }

        for (int i = period; i < gains.size(); i++) {
            double avgGain = average(gains.subList(i - period, i));
            double avgLoss = average(losses.subList(i - period, i));

            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsi = 100 - (100 / (1 + rs));

            XYChart.Data<String, Number> point = new XYChart.Data<>(candles.get(i).getTime().toString(), rsi);
            rsiSeries.getData().add(point);
        }

        chart.getData().add(rsiSeries);
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
