package org.investpro.ui.charts;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * TradingView-like trade visualization overlay for charts.
 * Displays entry points, exit points, take-profit, stop-loss levels, and P&L
 * zones.
 *
 * @author NOEL NGUEMECHIEU
 */
public class TradeVisualizationOverlay {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM dd")
            .withZone(ZoneId.systemDefault());

    // Colors matching TradingView pro theme
    private static final Color LONG_ENTRY_COLOR = Color.web("#2196F3"); // Blue
    private static final Color SHORT_ENTRY_COLOR = Color.web("#FF5252"); // Red
    private static final Color EXIT_COLOR = Color.web("#FF9800"); // Orange
    private static final Color TAKE_PROFIT_COLOR = Color.web("#4CAF50"); // Green
    private static final Color STOP_LOSS_COLOR = Color.web("#F44336"); // Deep Red

    private static final Color PROFIT_ZONE_COLOR = Color.web("#4CAF50").deriveColor(0, 1, 1, 0.15); // Green with alpha
    private static final Color LOSS_ZONE_COLOR = Color.web("#F44336").deriveColor(0, 1, 1, 0.15); // Red with alpha

    private final List<TradeMarker> tradeMarkers = new ArrayList<>();
    private final List<OrderLevel> orderLevels = new ArrayList<>();
    private final List<PnLZone> pnlZones = new ArrayList<>();

    private double chartWidth = 600;
    private double chartHeight = 400;
    private double priceRange = 100;
    private double priceMin = 0;
    private double timeRange = 100; // Number of visible candles

    /**
     * Trade marker (entry/exit point)
     *
     * @param type LONG_ENTRY, SHORT_ENTRY, EXIT
     */
        @Builder
        public record TradeMarker(@NotNull TradeType type, double price, long timestamp, String label, double quantity,
                                  Double takeProfit, Double stopLoss) {
    }

    /**
     * Order level line (TP, SL, etc.)
     *
     * @param type TAKE_PROFIT, STOP_LOSS, RESISTANCE, SUPPORT
     */
        @Builder
        public record OrderLevel(@NotNull OrderLevelType type, double price, String label) {
    }

    /**
     * Profit/Loss zone shading
     */
    @Getter
    @Builder
    @ToString
    @AllArgsConstructor
    public static class PnLZone {
        private final double priceHigh;
        private final double priceLow;
        private final boolean isProfit;
        private final String label;
    }

    public enum TradeType {
        LONG_ENTRY("🟢 LONG", LONG_ENTRY_COLOR),
        SHORT_ENTRY("🔴 SHORT", SHORT_ENTRY_COLOR),
        EXIT("🟡 EXIT", EXIT_COLOR),
        CLOSE("⏹ CLOSE", EXIT_COLOR);

        public final String label;
        public final Color color;

        TradeType(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    public enum OrderLevelType {
        TAKE_PROFIT("TP", TAKE_PROFIT_COLOR),
        STOP_LOSS("SL", STOP_LOSS_COLOR),
        RESISTANCE("R", Color.web("#FFC107")), // Amber
        SUPPORT("S", Color.web("#2196F3")); // Blue

        public final String label;
        public final Color color;

        OrderLevelType(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    /**
     * Update chart dimensions and scale
     */
    public void updateChartDimensions(double width, double height, double priceMin, double priceMax, double timeRange) {
        this.chartWidth = width;
        this.chartHeight = height;
        this.priceMin = priceMin;
        this.priceRange = priceMax - priceMin;
        this.timeRange = timeRange;
    }

    /**
     * Add trade marker (entry/exit)
     */
    public void addTradeMarker(@NotNull TradeMarker marker) {
        tradeMarkers.add(marker);
    }

    /**
     * Add order level (TP, SL, etc.)
     */
    public void addOrderLevel(@NotNull OrderLevel level) {
        orderLevels.add(level);
    }

    /**
     * Add P&L zone
     */
    public void addPnLZone(@NotNull PnLZone zone) {
        pnlZones.add(zone);
    }

    /**
     * Clear all overlays
     */
    public void clear() {
        tradeMarkers.clear();
        orderLevels.clear();
        pnlZones.clear();
    }

    /**
     * Render overlay on canvas
     */
    public void render(@NotNull Canvas canvas, @NotNull GraphicsContext gc) {
        if (tradeMarkers.isEmpty() && orderLevels.isEmpty() && pnlZones.isEmpty()) {
            return;
        }

        // Draw P&L zones first (background)
        drawPnLZones(gc);

        // Draw order level lines
        drawOrderLevels(gc);

        // Draw trade entry/exit markers
        drawTradeMarkers(gc);

        // Draw trade connection lines (entry to TP/SL)
        drawTradeConnections(gc);
    }

    private void drawPnLZones(@NotNull GraphicsContext gc) {
        for (PnLZone zone : pnlZones) {
            double y1 = priceToY(zone.priceLow);
            double y2 = priceToY(zone.priceHigh);
            double yTop = Math.min(y1, y2);
            double height = Math.abs(y1 - y2);

            Color color = zone.isProfit ? PROFIT_ZONE_COLOR : LOSS_ZONE_COLOR;
            gc.setFill(color);
            gc.fillRect(0, yTop, chartWidth, height);

            // Zone label
            if (zone.label != null && !zone.label.isEmpty()) {
                gc.setFill(Color.web("#9aa7ba"));
                gc.setFont(new Font(11));
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(zone.label, chartWidth - 10, yTop + 20);
            }
        }
    }

    private void drawOrderLevels(@NotNull GraphicsContext gc) {
        for (OrderLevel level : orderLevels) {
            double y = priceToY(level.price);

            // Draw horizontal line
            gc.setStroke(level.type.color);
            gc.setLineWidth(1.5);
            gc.setLineDashes(new double[] { 4, 3 }); // Dashed line
            gc.strokeLine(0, y, chartWidth, y);

            // Draw level label with price
            gc.setFill(level.type.color);
            gc.setFont(new Font(10));
            gc.setTextAlign(TextAlignment.LEFT);
            String priceText = String.format("%.2f", level.price);
            String labelText = String.format("%s %s", level.type.label, priceText);
            gc.fillText(labelText, 5, y - 5);

            // Draw price on right axis
            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(priceText, chartWidth - 5, y - 5);
        }
        gc.setLineDashes(null); // Reset dash pattern
    }

    private void drawTradeMarkers(@NotNull GraphicsContext gc) {
        for (TradeMarker marker : tradeMarkers) {
            double x = timeToX(marker.timestamp);
            double y = priceToY(marker.price);

            if (x < 0 || x > chartWidth || y < 0 || y > chartHeight) {
                continue; // Skip markers outside visible area
            }

            // Draw marker circle
            double markerRadius = 8;
            gc.setFill(marker.type.color);
            gc.fillOval(x - markerRadius, y - markerRadius, markerRadius * 2, markerRadius * 2);

            // Draw marker border
            gc.setStroke(Color.web("#ffffff"));
            gc.setLineWidth(2);
            gc.strokeOval(x - markerRadius, y - markerRadius, markerRadius * 2, markerRadius * 2);

            // Draw marker label above/below
            gc.setFill(marker.type.color);
            gc.setFont(new Font(10));
            gc.setTextAlign(TextAlignment.CENTER);

            String infoText = marker.type.label;
            if (marker.quantity > 0) {
                infoText += String.format(" (%.4f)", marker.quantity);
            }

            double labelY = y > chartHeight / 2 ? y - 25 : y + 25;
            gc.fillText(infoText, x, labelY);

            // Draw price label
            String priceText = String.format("%.2f", marker.price);
            double priceY = y > chartHeight / 2 ? labelY - 15 : labelY + 15;
            gc.fillText(priceText, x, priceY);
        }
    }

    private void drawTradeConnections(@NotNull GraphicsContext gc) {
        // Find matching entry/exit pairs
        for (TradeMarker entry : tradeMarkers) {
            if (entry.type != TradeType.LONG_ENTRY && entry.type != TradeType.SHORT_ENTRY) {
                continue;
            }

            double entryX = timeToX(entry.timestamp);
            double entryY = priceToY(entry.price);

            // Draw TP line
            if (entry.takeProfit != null) {
                double tpY = priceToY(entry.takeProfit);
                gc.setStroke(TAKE_PROFIT_COLOR);
                gc.setLineWidth(2);
                gc.setLineDashes(new double[] { 6, 2 });
                gc.strokeLine(entryX, entryY, chartWidth, tpY);
                gc.setLineDashes(null);
            }

            // Draw SL line
            if (entry.stopLoss != null) {
                double slY = priceToY(entry.stopLoss);
                gc.setStroke(STOP_LOSS_COLOR);
                gc.setLineWidth(2);
                gc.setLineDashes(new double[] { 6, 2 });
                gc.strokeLine(entryX, entryY, chartWidth, slY);
                gc.setLineDashes(null);
            }
        }
    }

    /**
     * Convert time/timestamp to X coordinate
     */
    private double timeToX(long timestamp) {
        // This is a simplified version - in real implementation,
        // this would need to integrate with the actual chart's time axis
        return chartWidth * 0.5; // Placeholder
    }

    /**
     * Convert price to Y coordinate
     */
    private double priceToY(double price) {
        if (priceRange == 0)
            return chartHeight / 2;
        double normalizedPrice = (price - priceMin) / priceRange;
        return chartHeight * (1 - normalizedPrice); // Invert Y axis (lower price = higher Y)
    }

    /**
     * Get trade info for tooltip
     */
    public String getTradeInfoAtPrice(double price) {
        StringBuilder info = new StringBuilder();
        info.append(String.format("Price: %.2f\n", price));

        // Check for nearby markers
        for (TradeMarker marker : tradeMarkers) {
            if (Math.abs(marker.price - price) < priceRange * 0.02) {
                info.append(String.format("%s @ %.2f\n", marker.type.label, marker.price));
            }
        }

        return info.toString();
    }
}
