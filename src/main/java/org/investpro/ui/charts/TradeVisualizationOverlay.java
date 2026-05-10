package org.investpro.ui.charts;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * TradingView-like trade visualization overlay for charts.
 *
 * Displays entry points, exit points, take-profit, stop-loss levels, and P&L zones.
 * The overlay maps trade timestamps to the visible candle index, so markers align
 * with the actual candle layout instead of being drawn at a fixed placeholder X value.
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
@Getter
@Setter
public class TradeVisualizationOverlay {

    // Colors matching TradingView pro theme
    private static final Color LONG_ENTRY_COLOR = Color.web("#2196F3");
    private static final Color SHORT_ENTRY_COLOR = Color.web("#FF5252");
    private static final Color EXIT_COLOR = Color.web("#FF9800");
    private static final Color TAKE_PROFIT_COLOR = Color.web("#4CAF50");
    private static final Color STOP_LOSS_COLOR = Color.web("#F44336");

    private static final Color PROFIT_ZONE_COLOR = Color.web("#4CAF50").deriveColor(0, 1, 1, 0.15);
    private static final Color LOSS_ZONE_COLOR = Color.web("#F44336").deriveColor(0, 1, 1, 0.15);
    private static final Color TEXT_MUTED_COLOR = Color.web("#9aa7ba");
    private static final Color WHITE_COLOR = Color.web("#ffffff");

    private final List<TradeMarker> tradeMarkers = new ArrayList<>();
    private final List<OrderLevel> orderLevels = new ArrayList<>();
    private final List<PnLZone> pnlZones = new ArrayList<>();

    private double chartWidth = 700;
    private double chartHeight = 600;
    private double priceRange = 100;
    private double priceMin = 0;
    private double priceMax = 100;

    /** Number of visible candles. Kept for compatibility with existing chart code. */
    private double timeRange = 100;

    private List<CandleData> candles = new ArrayList<>();
    private int visibleStartIndex = 0;
    private int visibleEndIndex = -1;
    private double candleWidth = 7.0;

    /**
     * Trade marker for an entry or exit point.
     *
     * @param type       LONG_ENTRY, SHORT_ENTRY, EXIT, CLOSE
     * @param price      execution/marker price
     * @param timestamp  epoch timestamp in seconds or milliseconds
     * @param label      optional display label
     * @param quantity   trade quantity
     * @param takeProfit optional take-profit level
     * @param stopLoss   optional stop-loss level
     */
    @Builder
    public record TradeMarker(
            @NotNull TradeType type,
            double price,
            long timestamp,
            String label,
            double quantity,
            Double takeProfit,
            Double stopLoss) {
    }

    /**
     * Horizontal order level line such as TP, SL, resistance, or support.
     *
     * @param type  level type
     * @param price level price
     * @param label optional label
     */
    @Builder
    public record OrderLevel(@NotNull OrderLevelType type, double price, String label) {
    }

    /**
     * Profit/Loss zone shading.
     *
     * @param priceHigh upper price bound
     * @param priceLow  lower price bound
     * @param isProfit  true for profit zone, false for loss zone
     * @param label     optional zone label
     */
    @Builder
    public record PnLZone(double priceHigh, double priceLow, boolean isProfit, String label) {
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
        RESISTANCE("R", Color.web("#FFC107")),
        SUPPORT("S", Color.web("#2196F3"));

        public final String label;
        public final Color color;

        OrderLevelType(String label, Color color) {
            this.label = label;
            this.color = color;
        }
    }

    /**
     * Updates chart dimensions and price scale.
     */
    public void updateChartDimensions(double width, double height, double priceMin, double priceMax, double timeRange) {
        this.chartWidth = Math.max(1.0, width);
        this.chartHeight = Math.max(1.0, height);
        this.priceMin = Math.min(priceMin, priceMax);
        this.priceMax = Math.max(priceMin, priceMax);
        this.priceRange = Math.max(0.00000001, this.priceMax - this.priceMin);
        this.timeRange = Math.max(1.0, timeRange);

        recalculateCandleWidth();
    }

    /**
     * Updates candle data and visible range in one call. Prefer this from CandleStickChart.
     */
    public void updateCandles(List<CandleData> candles, int visibleStartIndex, int visibleEndIndex) {
        this.candles = candles == null ? new ArrayList<>() : new ArrayList<>(candles);
        this.visibleStartIndex = clampIndex(visibleStartIndex, this.candles.size());
        this.visibleEndIndex = clampIndex(resolveVisibleEndIndex(visibleEndIndex), this.candles.size());

        if (!this.candles.isEmpty() && this.visibleEndIndex < this.visibleStartIndex) {
            this.visibleEndIndex = this.visibleStartIndex;
        }

        recalculateCandleWidth();
    }

    /**
     * Updates only the visible candle window.
     */
    public void updateVisibleRange(int visibleStartIndex, int visibleEndIndex) {
        this.visibleStartIndex = clampIndex(visibleStartIndex, candles.size());
        this.visibleEndIndex = clampIndex(resolveVisibleEndIndex(visibleEndIndex), candles.size());

        if (!candles.isEmpty() && this.visibleEndIndex < this.visibleStartIndex) {
            this.visibleEndIndex = this.visibleStartIndex;
        }

        recalculateCandleWidth();
    }

    /**
     * Adds a trade marker.
     */
    public void addTradeMarker(@NotNull TradeMarker marker) {
        tradeMarkers.add(marker);
    }

    /**
     * Adds an order level.
     */
    public void addOrderLevel(@NotNull OrderLevel level) {
        orderLevels.add(level);
    }

    /**
     * Adds a P&L zone.
     */
    public void addPnLZone(@NotNull PnLZone zone) {
        pnlZones.add(zone);
    }

    /**
     * Clears all overlays.
     */
    public void clear() {
        tradeMarkers.clear();
        orderLevels.clear();
        pnlZones.clear();
    }

    /**
     * Renders overlay on canvas.
     */
    public void render(@NotNull Canvas canvas, @NotNull GraphicsContext gc) {
        canvas.setWidth(chartWidth);
        canvas.setHeight(chartHeight);
        gc.clearRect(0, 0, chartWidth, chartHeight);

        if (tradeMarkers.isEmpty() && orderLevels.isEmpty() && pnlZones.isEmpty()) {
            return;
        }

        drawPnLZones(gc);
        drawOrderLevels(gc);
        drawTradeMarkers(gc);
        drawTradeConnections(gc);
        gc.setLineDashes(0.0);
    }

    private void drawPnLZones(@NotNull GraphicsContext gc) {
        for (PnLZone zone : pnlZones) {
            double y1 = priceToY(zone.priceLow());
            double y2 = priceToY(zone.priceHigh());
            double yTop = Math.min(y1, y2);
            double height = Math.abs(y1 - y2);

            Color color = zone.isProfit() ? PROFIT_ZONE_COLOR : LOSS_ZONE_COLOR;
            gc.setFill(color);
            gc.fillRect(0, yTop, chartWidth, height);

            if (zone.label() != null && !zone.label().isBlank()) {
                gc.setFill(TEXT_MUTED_COLOR);
                gc.setFont(Font.font(11));
                gc.setTextAlign(TextAlignment.RIGHT);
                gc.fillText(zone.label(), chartWidth - 10, yTop + 20);
            }
        }
    }

    private void drawOrderLevels(@NotNull GraphicsContext gc) {
        for (OrderLevel level : orderLevels) {
            double y = priceToY(level.price());
            if (y < 0 || y > chartHeight) {
                continue;
            }

            gc.setStroke(level.type().color);
            gc.setLineWidth(1.5);
            gc.setLineDashes(4, 3);
            gc.strokeLine(0, y, chartWidth, y);

            gc.setFill(level.type().color);
            gc.setFont(Font.font(10));
            gc.setTextAlign(TextAlignment.LEFT);

            String priceText = String.format("%.5f", level.price());
            String displayLabel = level.label() == null || level.label().isBlank()
                    ? level.type().label
                    : level.label();
            gc.fillText(displayLabel + " " + priceText, 5, y - 5);

            gc.setTextAlign(TextAlignment.RIGHT);
            gc.fillText(priceText, chartWidth - 5, y - 5);
        }

        gc.setLineDashes(0.0);
    }

    private void drawTradeMarkers(@NotNull GraphicsContext gc) {
        for (TradeMarker marker : tradeMarkers) {
            double x = timeToX(marker.timestamp());
            double y = priceToY(marker.price());

            if (x < 0 || x > chartWidth || y < 0 || y > chartHeight) {
                continue;
            }

            double markerRadius = 8;
            gc.setFill(marker.type().color);
            gc.fillOval(x - markerRadius, y - markerRadius, markerRadius * 2, markerRadius * 2);

            gc.setStroke(WHITE_COLOR);
            gc.setLineWidth(2);
            gc.strokeOval(x - markerRadius, y - markerRadius, markerRadius * 2, markerRadius * 2);

            gc.setFill(marker.type().color);
            gc.setFont(Font.font(10));
            gc.setTextAlign(TextAlignment.CENTER);

            String infoText = marker.label() == null || marker.label().isBlank()
                    ? marker.type().label
                    : marker.label();
            if (marker.quantity() > 0) {
                infoText += String.format(" (%.4f)", marker.quantity());
            }

            double labelY = y > chartHeight / 2.0 ? y - 25 : y + 25;
            gc.fillText(infoText, x, labelY);

            double priceY = y > chartHeight / 2.0 ? labelY - 15 : labelY + 15;
            gc.fillText(String.format("%.5f", marker.price()), x, priceY);
        }
    }

    private void drawTradeConnections(@NotNull GraphicsContext gc) {
        for (TradeMarker entry : tradeMarkers) {
            if (entry.type() != TradeType.LONG_ENTRY && entry.type() != TradeType.SHORT_ENTRY) {
                continue;
            }

            double entryX = timeToX(entry.timestamp());
            double entryY = priceToY(entry.price());

            if (entryX < 0 || entryX > chartWidth || entryY < 0 || entryY > chartHeight) {
                continue;
            }

            if (entry.takeProfit() != null && Double.isFinite(entry.takeProfit())) {
                double tpY = priceToY(entry.takeProfit());
                if (tpY >= 0 && tpY <= chartHeight) {
                    gc.setStroke(TAKE_PROFIT_COLOR);
                    gc.setLineWidth(2);
                    gc.setLineDashes(6, 2);
                    gc.strokeLine(entryX, entryY, chartWidth, tpY);
                }
            }

            if (entry.stopLoss() != null && Double.isFinite(entry.stopLoss())) {
                double slY = priceToY(entry.stopLoss());
                if (slY >= 0 && slY <= chartHeight) {
                    gc.setStroke(STOP_LOSS_COLOR);
                    gc.setLineWidth(2);
                    gc.setLineDashes(6, 2);
                    gc.strokeLine(entryX, entryY, chartWidth, slY);
                }
            }
        }

        gc.setLineDashes(0.0);
    }

    /**
     * Converts timestamp to an X coordinate based on the nearest candle index.
     * This keeps trade markers aligned with candle rendering.
     */
    private double timeToX(long timestamp) {
        if (candles == null || candles.isEmpty() || chartWidth <= 0) {
            return -1;
        }

        normalizeVisibleRangeIfNeeded();

        long timestampMillis = normalizeTimestampMillis(timestamp);
        int candleIndex = findNearestCandleIndex(timestampMillis);
        if (candleIndex < 0 || candleIndex < visibleStartIndex || candleIndex > visibleEndIndex) {
            return -1;
        }

        int visibleIndex = candleIndex - visibleStartIndex;
        return visibleIndex * candleWidth + candleWidth / 2.0;
    }

    private int findNearestCandleIndex(long timestampMillis) {
        if (candles == null || candles.isEmpty()) {
            return -1;
        }

        int low = 0;
        int high = candles.size() - 1;

        while (low <= high) {
            int mid = low + (high - low) / 2;
            long candleTime = normalizeTimestampMillis(candles.get(mid).getOpenTime());

            if (candleTime == timestampMillis) {
                return mid;
            }

            if (candleTime < timestampMillis) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }

        int nearest = Math.max(0, Math.min(low, candles.size() - 1));

        if (nearest > 0) {
            long previousTime = normalizeTimestampMillis(candles.get(nearest - 1).getOpenTime());
            long currentTime = normalizeTimestampMillis(candles.get(nearest).getOpenTime());
            if (Math.abs(timestampMillis - previousTime) <= Math.abs(currentTime - timestampMillis)) {
                return nearest - 1;
            }
        }

        return nearest;
    }

    /**
     * Converts price to Y coordinate.
     */
    private double priceToY(double price) {
        if (!Double.isFinite(price) || priceRange <= 0) {
            return chartHeight / 2.0;
        }

        double normalizedPrice = (price - priceMin) / priceRange;
        return chartHeight * (1.0 - normalizedPrice);
    }

    /**
     * Returns trade info for a tooltip near a price level.
     */
    public String getTradeInfoAtPrice(double price) {
        StringBuilder info = new StringBuilder();
        info.append(String.format("Price: %.5f%n", price));

        for (TradeMarker marker : tradeMarkers) {
            if (Math.abs(marker.price() - price) < priceRange * 0.02) {
                info.append(String.format("%s @ %.5f%n", marker.type().label, marker.price()));
            }
        }

        return info.toString();
    }

    private void recalculateCandleWidth() {
        int visibleCount = getVisibleCandleCount();
        if (visibleCount <= 0) {
            visibleCount = (int) Math.max(1.0, timeRange);
        }
        candleWidth = Math.max(1.0, chartWidth / Math.max(1, visibleCount));
    }

    private int getVisibleCandleCount() {
        if (candles == null || candles.isEmpty()) {
            return 0;
        }
        normalizeVisibleRangeIfNeeded();
        return Math.max(1, visibleEndIndex - visibleStartIndex + 1);
    }

    private void normalizeVisibleRangeIfNeeded() {
        if (candles == null || candles.isEmpty()) {
            visibleStartIndex = 0;
            visibleEndIndex = -1;
            return;
        }

        visibleStartIndex = clampIndex(visibleStartIndex, candles.size());
        visibleEndIndex = clampIndex(resolveVisibleEndIndex(visibleEndIndex), candles.size());

        if (visibleEndIndex < visibleStartIndex) {
            visibleEndIndex = visibleStartIndex;
        }
    }

    private int resolveVisibleEndIndex(int candidate) {
        if (candidate < 0 && candles != null && !candles.isEmpty()) {
            return candles.size() - 1;
        }
        return candidate;
    }

    private int clampIndex(int index, int size) {
        if (size <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(index, size - 1));
    }

    private long normalizeTimestampMillis(long timestamp) {
        return timestamp < 10_000_000_000L ? timestamp * 1000L : timestamp;
    }
}
