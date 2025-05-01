package org.investpro.investpro.chart;

import javafx.scene.Group;
import javafx.scene.chart.Axis;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.investpro.investpro.model.Candle;

public class CandleNode extends Group {

    private final double candleWidth;
    private final Rectangle body;
    private final Line highWick;
    private final Line lowWick;
    private Candle currentCandle;

    public CandleNode(Candle candle, double candleWidth) {
        this.candleWidth = candleWidth;
        this.currentCandle = candle;

        double open = candle.getOpen().doubleValue();
        double close = candle.getClose().doubleValue();
        double high = candle.getHigh().doubleValue();
        double low = candle.getLow().doubleValue();

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

    private String buildTooltipText(Candle candle) {
        return String.format("Time: %s\nOpen: %.2f\nHigh: %.2f\nLow: %.2f\nClose: %.2f\nVolume: %.2f",
                candle.getTime(),
                candle.getOpen().doubleValue(),
                candle.getHigh().doubleValue(),
                candle.getLow().doubleValue(),
                candle.getClose().doubleValue(),
                candle.getVolume().doubleValue());
    }

    public void update(double x, double y, Axis<Number> yAxis) {
        double openY = yAxis.getDisplayPosition(currentCandle.getOpen().doubleValue());
        double closeY = yAxis.getDisplayPosition(currentCandle.getClose().doubleValue());
        double highY = yAxis.getDisplayPosition(currentCandle.getHigh().doubleValue());
        double lowY = yAxis.getDisplayPosition(currentCandle.getLow().doubleValue());

        boolean bullish = currentCandle.getClose().doubleValue() >= currentCandle.getOpen().doubleValue();
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

    public void updateCandle(Candle candle) {
        this.currentCandle = candle;
    }
}
