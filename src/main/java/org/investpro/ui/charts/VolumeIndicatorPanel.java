package org.investpro.ui.charts;

import javafx.geometry.Insets;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.TextAlignment;
import lombok.*;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Professional volume indicator chart panel for TradingView-like visualization.
 * Displays volume bars with color coding (green for up, red for down).
 * Includes volume moving averages and market profile analysis.
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
@Getter
@Setter
public class VolumeIndicatorPanel extends StackPane {
    private static final String BG_COLOR = "#0a0e17";
    private static final String UP_COLOR = "#4CAF50"; // Green for up volume
    private static final String DOWN_COLOR = "#F44336"; // Red for down volume
    private static final String MA_COLOR = "#FFC107"; // Yellow for moving average
    private static final String TEXT_COLOR = "#9aa7ba";

    private static final int DEFAULT_HEIGHT = 120;
    private static final int MIN_CANDLE_WIDTH = 2;
    private static final int MAX_CANDLE_WIDTH = 30;
    private static final int CANDLE_SPACING = 2;

    private final Canvas canvas;
    private final List<VolumeBar> volumeBars = new ArrayList<>();
    private final Map<Integer, Double> volumeMA20 = new TreeMap<>();

    private double maxVolume = 1000;
    private double chartHeight = DEFAULT_HEIGHT;
    private double chartWidth = 600;
    private int candleWidth = 6;

    public VolumeIndicatorPanel() {
        canvas = new Canvas();
        canvas.setStyle("-fx-background-color: " + BG_COLOR + ";");
        canvas.setHeight(DEFAULT_HEIGHT);

        // Redraw on resize
        canvas.widthProperty().addListener((obs, oldVal, newVal) -> {
            chartWidth = newVal.doubleValue();
            redraw();
        });
        canvas.heightProperty().addListener((obs, oldVal, newVal) -> {
            chartHeight = newVal.doubleValue();
            redraw();
        });

        getChildren().add(canvas);
        setPadding(new Insets(0));
        setStyle("-fx-background-color: " + BG_COLOR + "; "
                + "-fx-border-color: #263246; "
                + "-fx-border-width: 1 0 0 0;");
    }

    /**
     * Volume bar data structure
     */
    @Getter
    @Builder
    @ToString
    @AllArgsConstructor
    public static class VolumeBar {
        private final double volume;
        private final boolean isUpCandle; // true for close > open, false for close <= open
        private final int index; // Position in the chart
        private final long timestamp;
        private final String label;
    }

    /**
     * Add volume bar for a candle
     */
    public void addVolumeBar(@NotNull VolumeBar bar) {
        volumeBars.add(bar);
        updateMaxVolume();
    }

    /**
     * Add multiple volume bars at once
     */
    public void addVolumeBars(@NotNull List<VolumeBar> bars) {
        volumeBars.addAll(bars);
        updateMaxVolume();
    }

    /**
     * Clear all volume bars
     */
    public void clear() {
        volumeBars.clear();
        volumeMA20.clear();
        maxVolume = 1000;
    }

    /**
     * Update volume moving average (20 periods)
     */
    public void updateVolumeMA20() {
        volumeMA20.clear();
        if (volumeBars.size() < 20)
            return;

        for (int i = 19; i < volumeBars.size(); i++) {
            double sum = 0;
            for (int j = i - 19; j <= i; j++) {
                sum += volumeBars.get(j).volume;
            }
            double avg = sum / 20.0;
            volumeMA20.put(i, avg);
        }
    }

    /**
     * Redraw the volume chart
     */
    public void redraw() {
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Clear canvas
        gc.setFill(Color.web(BG_COLOR));
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        if (volumeBars.isEmpty()) {
            drawEmptyState(gc);
            return;
        }

        // Draw gridlines
        drawGridlines(gc);

        // Draw volume bars
        drawVolumeBars(gc);

        // Draw volume MA
        drawVolumeMA(gc);

        // Draw axis labels
        drawAxisLabels(gc);
    }

    private void drawGridlines(@NotNull GraphicsContext gc) {
        gc.setStroke(Color.web("#263246"));
        gc.setLineWidth(0.5);

        // Horizontal gridlines
        int gridLines = 4;
        for (int i = 1; i < gridLines; i++) {
            double y = (chartHeight / gridLines) * i;
            gc.strokeLine(0, y, chartWidth, y);
        }
    }

    private void drawVolumeBars(@NotNull GraphicsContext gc) {
        int startIndex = Math.max(0, volumeBars.size() - 120);
        int endIndex = volumeBars.size();
        int visibleCount = endIndex - startIndex;

        if (visibleCount == 0)
            return;

        double barWidth = Math.max(MIN_CANDLE_WIDTH, Math.min(MAX_CANDLE_WIDTH,
                (chartWidth - CANDLE_SPACING * visibleCount) / visibleCount));

        for (int i = startIndex; i < endIndex; i++) {
            VolumeBar bar = volumeBars.get(i);
            int displayIndex = i - startIndex;

            // Calculate X position
            double x = displayIndex * (barWidth + CANDLE_SPACING);
            if (x >= chartWidth)
                break;

            // Calculate bar height based on volume
            double barHeight = (bar.volume / maxVolume) * (chartHeight - 20);
            barHeight = Math.max(1, barHeight);

            // Y position (bottom of chart)
            double y = chartHeight - barHeight;

            // Color based on candle direction
            gc.setFill(bar.isUpCandle ? Color.web(UP_COLOR) : Color.web(DOWN_COLOR));
            gc.fillRect(x, y, barWidth, barHeight);

            // Draw subtle border
            gc.setStroke(bar.isUpCandle ? Color.web("#66bb6a") : Color.web("#ef5350"));
            gc.setLineWidth(0.5);
            gc.strokeRect(x, y, barWidth, barHeight);
        }
    }

    private void drawVolumeMA(@NotNull GraphicsContext gc) {
        if (volumeMA20.isEmpty() || volumeBars.size() < 20)
            return;

        int startIndex = Math.max(0, volumeBars.size() - 120);
        int endIndex = volumeBars.size();
        int visibleCount = endIndex - startIndex;

        if (visibleCount == 0)
            return;

        double barWidth = Math.max(MIN_CANDLE_WIDTH, Math.min(MAX_CANDLE_WIDTH,
                (chartWidth - CANDLE_SPACING * visibleCount) / visibleCount));

        gc.setStroke(Color.web(MA_COLOR));
        gc.setLineWidth(2);

        Double lastY = null;

        for (int i = startIndex + 19; i < endIndex; i++) {
            if (i >= volumeMA20.size())
                break;

            Double maValue = volumeMA20.get(i);
            if (maValue == null)
                continue;

            int displayIndex = i - startIndex;
            double x = displayIndex * (barWidth + CANDLE_SPACING) + barWidth / 2;

            // Calculate MA line height
            double lineHeight = (maValue / maxVolume) * (chartHeight - 20);
            lineHeight = Math.max(1, lineHeight);
            double y = chartHeight - lineHeight;

            // Draw line segment
            if (lastY != null) {
                Double lastX = (displayIndex - 1) * (barWidth + CANDLE_SPACING) + barWidth / 2;
                gc.strokeLine(lastX, lastY, x, y);
            }

            lastY = y;
        }
    }

    private void drawAxisLabels(@NotNull GraphicsContext gc) {
        gc.setFill(Color.web(TEXT_COLOR));
        gc.setFont(new Font(10));
        gc.setTextAlign(TextAlignment.RIGHT);

        // Draw max volume label
        String maxVolLabel = formatVolume(maxVolume);
        gc.fillText(maxVolLabel, chartWidth - 5, 15);

        // Draw mid volume label
        String midVolLabel = formatVolume(maxVolume / 2);
        gc.fillText(midVolLabel, chartWidth - 5, chartHeight / 2);
    }

    private void drawEmptyState(@NotNull GraphicsContext gc) {
        gc.setFill(Color.web(TEXT_COLOR));
        gc.setFont(new Font(13));
        gc.setTextAlign(TextAlignment.CENTER);
        gc.fillText("No volume data available", chartWidth / 2, chartHeight / 2);
    }

    private void updateMaxVolume() {
        if (volumeBars.isEmpty()) {
            maxVolume = 1000;
            return;
        }

        double max = volumeBars.stream()
                .mapToDouble(VolumeBar::getVolume)
                .max()
                .orElse(1000);

        // Round up to nearest nice number
        if (max < 100) {
            maxVolume = 100;
        } else if (max < 1000) {
            maxVolume = Math.ceil(max / 100) * 100;
        } else if (max < 10000) {
            maxVolume = Math.ceil(max / 1000) * 1000;
        } else {
            maxVolume = Math.ceil(max / 10000) * 10000;
        }
    }

    private String formatVolume(double volume) {
        if (volume >= 1_000_000) {
            return String.format("%.0fM", volume / 1_000_000);
        } else if (volume >= 1_000) {
            return String.format("%.0fK", volume / 1_000);
        } else {
            return String.format("%.0f", volume);
        }
    }

    /**
     * Set custom height for the volume panel
     */
    public void setChartHeight(double height) {
        this.chartHeight = height;
        canvas.setHeight(height);
        redraw();
    }

    /**
     * Export volume data as formatted string
     */
    public String exportVolumeStats() {
        if (volumeBars.isEmpty())
            return "No volume data";

        double totalVolume = volumeBars.stream().mapToDouble(VolumeBar::getVolume).sum();
        double upVolume = volumeBars.stream()
                .filter(VolumeBar::isUpCandle)
                .mapToDouble(VolumeBar::getVolume)
                .sum();
        double downVolume = totalVolume - upVolume;

        return String.format("Volume Stats: Total=%.0f, Up=%.0f (%.1f%%), Down=%.0f (%.1f%%)",
                totalVolume,
                upVolume, (upVolume / totalVolume) * 100,
                downVolume, (downVolume / totalVolume) * 100);
    }
}
