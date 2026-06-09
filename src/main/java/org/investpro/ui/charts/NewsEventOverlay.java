package org.investpro.ui.charts;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.market.NewsEvent;
import org.investpro.service.NewsDataProvider;
import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.List;

/**
 * Chart overlay for drawing news events on price charts.
 * Displays markers, lines, and labels for economic events to help traders
 * correlate news with price movements.
 */
@EqualsAndHashCode(callSuper = true)
@Data
@Slf4j
public class NewsEventOverlay extends Canvas {
    private final NewsDataProvider newsDataProvider;
    private List<NewsEvent> newsEvents = List.of();

    private Instant chartStartTime;
    private Instant chartEndTime;
    private double chartWidth;
    private double chartHeight;
    private double chartStartPrice;
    private double chartEndPrice;

    public NewsEventOverlay(@NonNull NewsDataProvider newsDataProvider, double width, double height) {
        super(width, height);
        this.newsDataProvider = newsDataProvider;
        this.chartWidth = width;
        this.chartHeight = height;

        // Listen for news updates
        newsDataProvider.addNewsEventListener((event, action) -> {
            if ("ADDED".equals(action) || "PROCESSED".equals(action)) {
                refreshNewsDisplay();
            }
        });
    }

    /**
     * Update chart time range and price range for coordinate mapping.
     */
    public void setChartBounds(Instant startTime, Instant endTime, double startPrice, double endPrice) {
        this.chartStartTime = startTime;
        this.chartEndTime = endTime;
        this.chartStartPrice = startPrice;
        this.chartEndPrice = endPrice;
        refreshNewsDisplay();
    }

    /**
     * Update news events to display.
     */
    public void setNewsEvents(List<NewsEvent> events) {
        this.newsEvents = events != null ? events : List.of();
        refreshNewsDisplay();
    }

    /**
     * Refresh and redraw the overlay.
     */
    public void refreshNewsDisplay() {
        GraphicsContext gc = getGraphicsContext2D();
        gc.clearRect(0, 0, getWidth(), getHeight());

        if (chartStartTime == null || newsEvents.isEmpty()) {
            return;
        }

        // Draw each news event
        for (NewsEvent event : newsEvents) {
            drawNewsEvent(gc, event);
        }

        // Draw legend
        drawLegend(gc);
    }

    private void drawNewsEvent(GraphicsContext gc, @NonNull NewsEvent event) {
        // Calculate X coordinate from event time
        double xCoord = getXFromTime(event.getEventTime());

        // Skip if outside chart bounds
        if (xCoord < 0 || xCoord > getWidth()) {
            return;
        }

        // Draw vertical line
        Color lineColor = getEventColor(event);
        gc.setStroke(lineColor);
        gc.setLineWidth(2);
        gc.strokeLine(xCoord, 0, xCoord, getHeight());

        // Draw marker at top
        drawEventMarker(gc, xCoord, event);

        // Draw label if space available
        drawEventLabel(gc, xCoord, event);
    }

    private void drawEventMarker(GraphicsContext gc, double x, NewsEvent event) {
        Color color = getEventColor(event);

        // Draw marker circle/triangle at top
        double markerSize = 8;
        gc.setFill(color);

        // Draw triangle pointing down
        double[] xPoints = { x, x - markerSize, x + markerSize };
        double[] yPoints = { 15, 25, 25 };
        gc.fillPolygon(xPoints, yPoints, 3);

        // Draw border
        gc.setStroke(color.brighter());
        gc.setLineWidth(1.5);
        gc.strokePolygon(xPoints, yPoints, 3);
    }

    private void drawEventLabel(GraphicsContext gc, double x, NewsEvent event) {
        gc.setFont(new Font("Monospace", 8));
        gc.setFill(getEventColor(event));
        // Note: GraphicsContext doesn't support setTextAlignment; text is drawn at the
        // specified position

        // Truncate long titles
        String label = event.getTitle().length() > 15
                ? event.getTitle().substring(0, 12) + "..."
                : event.getTitle();

        // Draw label at top
        gc.fillText(label, x, 50, 40);
    }

    private void drawLegend(GraphicsContext gc) {
        int legendX = (int) (getWidth() - 180);
        int legendY = 10;
        int boxWidth = 170;
        int boxHeight = 110;

        // Background
        gc.setFill(new Color(0.05, 0.05, 0.05, 0.8));
        gc.fillRect(legendX, legendY, boxWidth, boxHeight);

        // Border
        gc.setStroke(new Color(0.3, 0.3, 0.3, 1));
        gc.setLineWidth(1);
        gc.strokeRect(legendX, legendY, boxWidth, boxHeight);

        // Title
        gc.setFont(new Font("Monospace", 9));
        gc.setFill(Color.web("#00ff00"));
        gc.fillText("News Events", legendX + 5, legendY + 15);

        // Legend items
        int itemY = legendY + 30;
        int itemSpacing = 18;

        gc.setFont(new Font("Monospace", 8));

        // CRITICAL
        gc.setFill(Color.web("#ff0000"));
        gc.fillRect(legendX + 5, itemY - 5, 8, 8);
        gc.setFill(Color.web("#ff0000"));
        gc.fillText("CRITICAL", legendX + 20, itemY);

        // HIGH
        itemY += itemSpacing;
        gc.setFill(Color.web("#ff6666"));
        gc.fillRect(legendX + 5, itemY - 5, 8, 8);
        gc.setFill(Color.web("#ff6666"));
        gc.fillText("HIGH", legendX + 20, itemY);

        // MEDIUM
        itemY += itemSpacing;
        gc.setFill(Color.web("#ffaa00"));
        gc.fillRect(legendX + 5, itemY - 5, 8, 8);
        gc.setFill(Color.web("#ffaa00"));
        gc.fillText("MEDIUM", legendX + 20, itemY);

        // LOW
        itemY += itemSpacing;
        gc.setFill(Color.web("#888888"));
        gc.fillRect(legendX + 5, itemY - 5, 8, 8);
        gc.setFill(Color.web("#888888"));
        gc.fillText("LOW", legendX + 20, itemY);
    }

    private double getXFromTime(Instant time) {
        if (chartStartTime == null || chartEndTime == null) {
            return -1;
        }

        long totalSeconds = chartEndTime.getEpochSecond() - chartStartTime.getEpochSecond();
        long eventSeconds = time.getEpochSecond() - chartStartTime.getEpochSecond();

        if (totalSeconds <= 0) {
            return -1;
        }

        return (eventSeconds / (double) totalSeconds) * getWidth();
    }

    private Color getEventColor(NewsEvent event) {
        return switch (event.getImportance()) {
            case CRITICAL -> Color.web("#ff0000"); // Red
            case HIGH -> Color.web("#ff6666"); // Light Red
            case MEDIUM -> Color.web("#ffaa00"); // Orange
            case LOW -> Color.web("#888888"); // Gray
        };
    }
}
