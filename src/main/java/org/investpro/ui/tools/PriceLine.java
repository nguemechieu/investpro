package org.investpro.ui.tools;

import javafx.scene.paint.Color;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Represents a horizontal price level drawn on the candlestick chart.
 *
 * Used for:
 * - support / resistance
 * - current bid / ask lines
 * - stop-loss / take-profit levels
 * - manual trader annotations
 * - AI/SmartBot suggested levels
 */
@Getter
@Setter
public class PriceLine {

    private double price;
    private Color color;
    private String label;

    private boolean visible = true;
    private boolean dashed = true;
    private boolean labelVisible = true;

    private double lineWidth = 1.0;

    public PriceLine(double price, Color color, String label) {
        this.price = sanitizePrice(price);
        this.color = color == null ? Color.web("#f59e0b") : color;
        this.label = label == null ? "" : label.trim();
    }

    public PriceLine(double price, Color color, String label, boolean dashed) {
        this(price, color, label);
        this.dashed = dashed;
    }

    public PriceLine(
            double price,
            Color color,
            String label,
            boolean visible,
            boolean dashed,
            boolean labelVisible,
            double lineWidth
    ) {
        this.price = sanitizePrice(price);
        this.color = color == null ? Color.web("#f59e0b") : color;
        this.label = label == null ? "" : label.trim();
        this.visible = visible;
        this.dashed = dashed;
        this.labelVisible = labelVisible;
        this.lineWidth = sanitizeLineWidth(lineWidth);
    }

    @Contract("_ -> new")
    public static @NotNull PriceLine support(double price) {
        return new PriceLine(price, Color.web("#22c55e"), "Support", true, true, true, 1.2);
    }

    @Contract("_ -> new")
    public static @NotNull PriceLine resistance(double price) {
        return new PriceLine(price, Color.web("#ef4444"), "Resistance", true, true, true, 1.2);
    }

    public static PriceLine stopLoss(double price) {
        return new PriceLine(price, Color.web("#dc2626"), "SL", true, true, true, 1.4);
    }

    @Contract("_ -> new")
    public static @NotNull PriceLine takeProfit(double price) {
        return new PriceLine(price, Color.web("#16a34a"), "TP", true, true, true, 1.4);
    }

    @Contract("_ -> new")
    public static @NotNull PriceLine entry(double price) {
        return new PriceLine(price, Color.web("#3b82f6"), "Entry", true, false, true, 1.3);
    }

    @Contract("_ -> new")
    public static @NotNull PriceLine bid(double price) {
        return new PriceLine(price, Color.web("#22c55e"), "Bid", true, true, true, 1.0);
    }

    @Contract("_ -> new")
    public static @NotNull PriceLine ask(double price) {
        return new PriceLine(price, Color.web("#ef4444"), "Ask", true, true, true, 1.0);
    }

    public boolean isValid() {
        return Double.isFinite(price) && price > 0;
    }

    public PriceLine copy() {
        return new PriceLine(
                price,
                color,
                label,
                visible,
                dashed,
                labelVisible,
                lineWidth
        );
    }

    public void setPrice(double price) {
        this.price = sanitizePrice(price);
    }

    public void setColor(Color color) {
        this.color = color == null ? Color.web("#f59e0b") : color;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label.trim();
    }

    public void setLineWidth(double lineWidth) {
        this.lineWidth = sanitizeLineWidth(lineWidth);
    }

    private double sanitizePrice(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            return 0.0;
        }

        return value;
    }

    private double sanitizeLineWidth(double value) {
        if (!Double.isFinite(value) || value <= 0) {
            return 1.0;
        }

        return Math.min(value, 6.0);
    }

    @Override
    public String toString() {
        return "PriceLine{price=%s, label='%s', visible=%s, dashed=%s, lineWidth=%s}"
                .formatted(price, label, visible, dashed, lineWidth);
    }
}