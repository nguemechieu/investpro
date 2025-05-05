package org.investpro.investpro.ui.chart;

import javafx.scene.Group;
import javafx.scene.chart.Axis;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

public class CandleNode extends Group {

    private final double candleWidth;
    private final Rectangle body;
    private final Line highWick;
    private final Line lowWick;
    private final CandleData currentCandle;
    double x, y;

    public CandleNode(@NotNull CandleData candle, double candleWidth) {
        this.candleWidth = candleWidth;
        this.currentCandle = candle;

        double open = candle.getOpenPrice();//().doubleValue();
        double close = candle.getClosePrice();//().doubleValue();
        double high = candle.getHighPrice();//().doubleValue();
        double low = candle.getLowPrice();//().doubleValue();

        boolean bullish = close >= open;
        Color color = bullish ? Color.GREEN : Color.RED;

        this.body = createBody(open, close, color);
        this.highWick = createWick(high, Math.max(open, close), color);
        this.lowWick = createWick(Math.min(open, close), low, color);

        getChildren().addAll(highWick, lowWick, body);

        Tooltip tooltip = new Tooltip(buildTooltipText(candle));
        Tooltip.install(this, tooltip);
        tooltip.setShowDelay(Duration.millis(50));
    }

    private Rectangle createBody(double open, double close, Color color) {
        double height = Math.abs(close - open);
        Rectangle body = new Rectangle(candleWidth, height == 0 ? 1 : height);
        body.setFill(color);
        return body;
    }

    private Line createWick(double startY, double endY, Color color) {
        Line wick = new Line(candleWidth / 2, startY, candleWidth / 2, endY);
        wick.setStroke(color);
        return wick;
    }

    private String buildTooltipText(CandleData candle) {
        return String.format("Time: %s\nOpen: %.2f\nHigh: %.2f\nLow: %.2f\nClose: %.2f\nVolume: %.2f",
                candle.getOpenTime(),
                candle.getOpenPrice(),//().doubleValue(),
                candle.getHighPrice(),//.doubleValue(),
                candle.getLowPrice(),//.doubleValue(),
                candle.getClosePrice(),//.doubleValue(),
                candle.getVolume());
    }

    public void update(double x, double y, Axis<Number> yAxis) {
        this.x = x;
        this.y = y;
        double openY = yAxis.getDisplayPosition(currentCandle.getOpenPrice());
        double closeY = yAxis.getDisplayPosition(currentCandle.getClosePrice());
        double highY = yAxis.getDisplayPosition(currentCandle.getHighPrice());
        double lowY = yAxis.getDisplayPosition(currentCandle.getLowPrice());
        yAxis.getDisplayPosition(currentCandle.getVolume());


        boolean bullish = currentCandle.getClosePrice() >= currentCandle.getOpenPrice();
        Color color = bullish ? Color.GREEN : Color.RED;

        // Update body
        double bodyTop = Math.min(openY, closeY);
        double bodyHeight = Math.abs(closeY - openY);
        body.setHeight(bodyHeight == 0 ? 1 : bodyHeight);
        body.setY(bodyTop);
        body.setX(x - candleWidth / 2);
        body.setFill(color);

        // Update wicks
        highWick.setStartX(x);
        highWick.setEndX(x);
        highWick.setStartY(highY);
        highWick.setEndY(Math.min(openY, closeY));
        highWick.setStroke(color);

        lowWick.setStartX(x);
        lowWick.setEndX(x);
        lowWick.setStartY(Math.max(openY, closeY));
        lowWick.setEndY(lowY);
        lowWick.setStroke(color);
    }

}
