package org.investpro.investpro.chart;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;

import org.investpro.investpro.model.Candle;
import org.jetbrains.annotations.NotNull;

import java.util.NavigableMap;

public class CandleChartRenderer {

    private final GraphicsContext graphicsContext;
    private final double chartWidth;
    private final double chartHeight;
    private final int candleWidth;

    public CandleChartRenderer(GraphicsContext graphicsContext, double chartWidth, double chartHeight, int candleWidth) {
        this.graphicsContext = graphicsContext;
        this.chartWidth = chartWidth;
        this.chartHeight = chartHeight;
        this.candleWidth = candleWidth;
    }

    public void clearChartCanvas() {
        graphicsContext.setFill(Color.BLACK);
        graphicsContext.fillRect(0, 0, chartWidth, chartHeight);
    }

    public void drawGridLines() {
        graphicsContext.setStroke(Color.GRAY);
        graphicsContext.setLineWidth(0.5);
        for (int i = 0; i < chartWidth; i += 50) {
            graphicsContext.strokeLine(i, 0, i, chartHeight);
        }
        for (int j = 0; j < chartHeight; j += 50) {
            graphicsContext.strokeLine(0, j, chartWidth, j);
        }
    }

    public void drawCandles(@NotNull NavigableMap<Integer, Candle> candles, double pixelsPerMonetaryUnit) {
        int candleIndex = 0;
        for (Candle candle : candles.descendingMap().values()) {
            drawCandle(candle, candleIndex++, pixelsPerMonetaryUnit);
        }
    }

    private void drawCandle(@NotNull Candle candle, int candleIndex, double pixelsPerMonetaryUnit) {
        boolean isBullish = (candle.getClose().doubleValue() >= candle.getOpen().doubleValue());
        Color candleColor = isBullish ? Color.GREEN : Color.RED;

        double openY = (candle.getOpen().floatValue()) * pixelsPerMonetaryUnit;
        double closeY = (candle.getClose().floatValue()) * pixelsPerMonetaryUnit;
        double highY = (candle.getHigh().floatValue()) * pixelsPerMonetaryUnit;
        double lowY = (candle.getLow().doubleValue()) * pixelsPerMonetaryUnit;

        double x = chartWidth - (candleIndex + 1) * candleWidth;

        graphicsContext.setFill(candleColor);
        graphicsContext.fillRect(x, Math.min(openY, closeY), candleWidth - 2, Math.abs(openY - closeY));

        graphicsContext.setStroke(candleColor);
        graphicsContext.strokeLine(x + candleWidth / 2.0, highY, x + candleWidth / 2.0, lowY);
    }

    public void drawLoadingText(int progress) {
        graphicsContext.setFill(Color.YELLOW);
        graphicsContext.setFont(new Font("Arial", 16));
        graphicsContext.fillText("Loading...%" + progress, chartWidth / 2 - 50, chartHeight / 2);
    }

    public void drawNoDataRetry(int attempt, int maxAttempts) {
        graphicsContext.setFill(Color.RED);
        graphicsContext.setFont(new Font("Arial", 16));
        graphicsContext.fillText("Retrying... attempt " + attempt + "/" + maxAttempts, chartWidth / 2 - 50, chartHeight / 2);
    }
}
