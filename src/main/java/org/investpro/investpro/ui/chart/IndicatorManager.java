package org.investpro.investpro.ui.chart;

import javafx.scene.paint.Color;
import javafx.scene.shape.Polyline;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.model.CandleData;
import org.investpro.investpro.ui.chart.overlay.RSIOverlay;
import org.investpro.investpro.ui.chart.overlay.SMAOverlay;

import org.jetbrains.annotations.NotNull;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class IndicatorManager {

    private final CandleStickChart chart;
    private SMAOverlay smaOverlay;
    private RSIOverlay rsiOverlay;

    public IndicatorManager(CandleStickChart chart) {
        this.chart = chart;
    }

    public void addSimpleMovingAverage(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period) return;

        SMAOverlay smaLine = new SMAOverlay(period);
        smaLine.getRsiLine().setStroke(Color.YELLOW);
        smaLine.getLine().setStrokeWidth(1.2);

        double chartHeight = CandleStickChart.getCanvas().getHeight();
        double chartWidth = CandleStickChart.getCanvas().getWidth();
        double candleWidth = chart.getCandleWidth();

        for (int i = period - 1; i < candles.size(); i++) {
            BigDecimal sum = BigDecimal.ZERO;
            for (int j = 0; j < period; j++) {
                sum = sum.add(BigDecimal.valueOf(candles.get(i - j).getClosePrice()));
            }
            BigDecimal avg = sum.divide(BigDecimal.valueOf(period), RoundingMode.HALF_UP);

            double price = avg.doubleValue();
            double x = chartWidth - ((candles.size() - i) * candleWidth);
            double y = chartHeight - chart.getOverlayManager().priceToPixel(price);

            smaLine.getRsiLine().getPoints().addAll(x, y);
        }

        removeSMA(); // ensure previous one is cleared
        smaOverlay = smaLine;
        chart.getOverlayManager().addOverlay(smaOverlay);
    }

    public void addRSI(List<CandleData> candles, int period) {
        if (candles == null || candles.size() < period + 1) return;

        Polyline rsiLine = new Polyline();
        rsiLine.setStroke(Color.CYAN);
        rsiLine.setStrokeWidth(1.5);

        List<Double> gains = new ArrayList<>();
        List<Double> losses = new ArrayList<>();

        for (int i = 1; i < candles.size(); i++) {
            double change = candles.get(i).getClosePrice();//.subtract(candles.get(i - 1).getClose()).doubleValue();
            gains.add(Math.max(change, 0));
            losses.add(Math.max(-change, 0));
        }
        double chartHeight = chart.getCanvas().getHeight();
        double chartWidth = chart.getCanvas().getWidth();
        double candleWidth = chart.getCandleWidth();

        for (int i = period; i < gains.size(); i++) {
            double avgGain = average(gains.subList(i - period, i));
            double avgLoss = average(losses.subList(i - period, i));
            double rs = avgLoss == 0 ? 100 : avgGain / avgLoss;
            double rsi = 100 - (100 / (1 + rs));

            double x = chartWidth - ((candles.size() - i) * candleWidth);
            double y = chartHeight - (rsi / 100.0 * chartHeight); // 0-100 normalized

            rsiLine.getPoints().addAll(x, y);
        }

        removeRSI(); // clear old
        rsiOverlay.setRsiLine(rsiLine);
        chart.getOverlayManager().addOverlay(rsiOverlay);
    }

    public void clearIndicators() {
        removeSMA();
        removeRSI();
    }

    private void removeSMA() {
        if (smaOverlay != null) {
            chart.getOverlayManager().removeOverlayByName(smaOverlay.toString());
            smaOverlay = null;
        }
    }

    private void removeRSI() {
        if (rsiOverlay != null) {
            chart.getOverlayManager().removeOverlayByName(rsiOverlay.toString());
            rsiOverlay = null;
        }
    }

    private double average(@NotNull List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }
}
