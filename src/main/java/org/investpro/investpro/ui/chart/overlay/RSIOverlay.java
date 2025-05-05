package org.investpro.investpro.ui.chart.overlay;

import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.ui.chart.CandleStickChart;


import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class RSIOverlay implements ChartOverlay {
    private final int period;
    private Polyline rsiLine;

    public RSIOverlay(int period) {
        this.period = period;
    }

    @Override
    public String getName() {
        return "RSI-" + period;
    }

    @Override
    public void apply(CandleStickChart chart, List<CandleData> candles) {
        if (candles.size() <= period) return;

        rsiLine = new Polyline();
        rsiLine.setStroke(Color.CYAN);
        rsiLine.setStrokeWidth(1.5);

        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            double change = candles.get(i).getClosePrice();
            gains.add(Math.max(0, change));
            losses.add(Math.max(0, -change));
        }

        double width = CandleStickChart.getCanvas().getWidth();
        double height = CandleStickChart.getCanvas().getHeight();
        double candleWidth = 10;

        for (int i = period; i < gains.size(); i++) {
            double avgGain = average(gains.subList(i - period, i));
            double avgLoss = average(losses.subList(i - period, i));
            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsi = 100 - (100 / (1 + rs));

            double x = width - ((candles.size() - i) * candleWidth);
            double y = height - (rsi / 100.0 * height);
            rsiLine.getPoints().addAll(x, y);
        }

        chart.getOverlayManager().addOverlay(new RSIOverlay(period));
    }

    @Override
    public void clear(CandleStickChart chart) {
        if (rsiLine != null) chart.getOverlayManager().removeOverlayByName(rsiLine.toString());
    }

    private double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
