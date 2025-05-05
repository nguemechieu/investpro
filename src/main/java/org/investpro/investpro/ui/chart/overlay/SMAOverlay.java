package org.investpro.investpro.ui.chart.overlay;

import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.ui.chart.CandleStickChart;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.DoubleStream;

@Getter
@Setter
public class SMAOverlay extends RSIOverlay implements ChartOverlay {

    private final int period;
    private Polyline line;

    public SMAOverlay(int period) {
        super(period);
        this.period = period;
    }

    @Override
    public String getName() {
        return "SMA-" + period;
    }

    @Override
    public void apply(CandleStickChart chart, List<CandleData> candles) {
        if (candles.size() < period) return;

        line = new Polyline();
        line.setStroke(Color.ORANGE);
        line.setStrokeWidth(1.5);

        double chartHeight = CandleStickChart.getCanvas().getHeight();
        double chartWidth = CandleStickChart.getCanvas().getWidth();
        double candleWidth = 10;

        for (int i = period - 1; i < candles.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = 0; j < period; j++) {
                sum = sum.add(BigDecimal.valueOf(candles.get(i - j).getClosePrice()));
            }
            BigDecimal avg = sum.divide(BigDecimal.valueOf(period), RoundingMode.HALF_UP);

            double x = chartWidth - ((candles.size() - i) * candleWidth);
            double y = chartHeight * (1 - normalize(chart, avg.doubleValue()));
            line.getPoints().addAll(x, y);
        }

        //chart.getOverlayManager().addOverlay(line);
    }

    @Override
    public void clear(CandleStickChart chart) {
        if (line != null) {
            chart.getOverlayManager().removeOverlayByName(line.toString());
        }
    }

    private double normalize(CandleStickChart chart, double value) {
        DoubleStream max = chart.getCandlesData().stream().mapToDouble(CandleData::getHighPrice);
        double min = chart.getCandlesData().stream().mapToDouble(CandleData::getLowPrice).min().orElse(0);
        return (value - min) / (max.max().orElse(0) - min);
    }
}
