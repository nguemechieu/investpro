package org.investpro.ai;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;

import java.util.List;

public class InvestProOverlayDrawer {

    private final Canvas canvas;
    private final GraphicsContext gc;

    public InvestProOverlayDrawer(Canvas canvas) {
        this.canvas = canvas;
        this.gc = canvas.getGraphicsContext2D();
    }

    public void drawSupportLevels(List<Double> levels) {
        gc.setStroke(Color.GREEN);
        gc.setLineWidth(1.5);

        for (Double level : levels) {
            gc.strokeLine(0, mapPriceToY(level), canvas.getWidth(), mapPriceToY(level));
        }
    }

    public void drawResistanceLevels(List<Double> levels) {
        gc.setStroke(Color.RED);
        gc.setLineWidth(1.5);

        for (Double level : levels) {
            gc.strokeLine(0, mapPriceToY(level), canvas.getWidth(), mapPriceToY(level));
        }
    }

    public void annotateText(String text, double price, Color color) {
        gc.setFill(color);
        gc.fillText(text, 5, mapPriceToY(price) - 5);
    }

    public void clear() {
        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
    }

    private double mapPriceToY(double price) {
        // TODO: Replace this with real mapping logic between price and Y-pixel
        // right now we just assume 1:1 mapping
        return canvas.getHeight() - price;
    }
}
