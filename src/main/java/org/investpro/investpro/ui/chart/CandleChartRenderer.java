package org.investpro.investpro.ui.chart;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;


import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class CandleChartRenderer {

    private final GraphicsContext graphicsContext;
    private final double chartWidth;
    private final double chartHeight;
    private final int candleWidth;

    public CandleChartRenderer(GraphicsContext graphicsContext, int candleWidth, double chartWidth, double chartHeight) {
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

    public void drawCandles(@NotNull List<CandleData> candles, double pixelsPerMonetaryUnit, GraphicsContext graphicsContext) {
        int candleIndex = 0;
        for (CandleData candle : candles) {
            drawCandle(candle, candleIndex++, pixelsPerMonetaryUnit, graphicsContext);
        }
    }

    private void drawCandle(CandleData candle, int candleIndex, double pixelsPerMonetaryUnit, @NotNull GraphicsContext graphicsContext) {
        boolean isBullish = (candle.getClosePrice() >= candle.getOpenPrice());
        Color candleColor = isBullish ? Color.GREEN : Color.RED;

        double openY = (candle.getOpenPrice() * pixelsPerMonetaryUnit);
        double closeY = (candle.getClosePrice()) * pixelsPerMonetaryUnit;
        double highY = (candle.getHighPrice()) * pixelsPerMonetaryUnit;
        double lowY = (candle.getLowPrice()) * pixelsPerMonetaryUnit;

        double x = chartWidth - (candleIndex + 1) * candleWidth;

        graphicsContext.setFill(candleColor);
        graphicsContext.fillRect(x, Math.min(openY, closeY), candleWidth - 2, Math.abs(openY - closeY));

        graphicsContext.setStroke(candleColor);
        graphicsContext.strokeLine(x + candleWidth / 2.0, highY, x + candleWidth / 2.0, lowY);
    }

    public void drawLoadingText(double progress, @NotNull GraphicsContext graphicsContext) {
        graphicsContext.setFill(Color.YELLOW);
        graphicsContext.setFont(new Font("Arial", 16));
        graphicsContext.fillText("Loading...%" + progress, chartWidth / 2 - 50, chartHeight / 2);
    }

    public void drawNoDataRetry(int attempt, int maxAttempts, @NotNull GraphicsContext graphicsContext) {
        graphicsContext.setFill(Color.RED);
        graphicsContext.setFont(new Font("Arial", 16));
        graphicsContext.fillText("Retrying... attempt " + attempt + "/" + maxAttempts, chartWidth / 2 - 50, chartHeight / 2);
    }

}
