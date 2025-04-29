package org.investpro.chart;

import javafx.scene.Group;
import javafx.scene.control.Tooltip;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.investpro.model.Candle;

public class CandleNode extends Group {

    private final double candleWidth;

    public CandleNode(Candle candle, double candleWidth) {
        this.candleWidth = candleWidth;
        double open = candle.getOpen().doubleValue();
        double close = candle.getClose().doubleValue();
        double high = candle.getHigh().doubleValue();
        double low = candle.getLow().doubleValue();
        double volume = candle.getVolume().doubleValue();

        boolean bullish = close >= open;
        Color color = bullish ? Color.GREEN : Color.RED;

        Rectangle body = createBody(open, close, color);
        Line highWick = createWick(high, Math.max(open, close), color);
        Line lowWick = createWick(Math.min(open, close), low, color);

        getChildren().addAll(highWick, lowWick, body);

        Tooltip tooltip = new Tooltip(buildTooltipText(candle));
        Tooltip.install(this, tooltip);
        tooltip.setShowDelay(Duration.millis(50));
    }

    private Rectangle createBody(double open, double close, Color color) {
        double height = Math.abs(close - open);
        Rectangle body = new Rectangle(candleWidth, Math.max(height, 1));
        body.setFill(color);

        body.setTranslateY(Math.min(close, open));

        return body;
    }

    private Line createWick(double startY, double endY, Color color) {
        Line wick = new Line(candleWidth / 2, startY, candleWidth / 2, endY);
        wick.setStroke(color);
        return wick;
    }

    private String buildTooltipText(Candle candle) {
        return "Time: " + candle.getTime() +
                "\nOpen: " + candle.getOpen() +
                "\nHigh: " + candle.getHigh() +
                "\nLow: " + candle.getLow() +
                "\nClose: " + candle.getClose() +
                "\nVolume: " + candle.getVolume();
    }
}
