package org.investpro.ui.charts;

import lombok.Builder;
import lombok.Data;
import javafx.scene.paint.Color;
import java.time.Instant;

/**
 * Represents an event to be drawn on the chart.
 * Events include: trade entries/exits, stop losses, take profits, economic
 * news, etc.
 */
@Data
@Builder
public class ChartEvent {
    public enum EventType {
        TRADE_ENTRY("Entry", "#22c55e"), // Green
        TRADE_EXIT("Exit", "#ef4444"), // Red
        STOP_LOSS("Stop Loss", "#ef4444"), // Red
        TAKE_PROFIT("Take Profit", "#22c55e"), // Green
        NEWS("News", "#f59e0b"), // Amber/Yellow
        ECONOMIC_EVENT("Economic Event", "#f59e0b"), // Amber/Yellow
        LEVEL_SUPPORT("Support", "#60a5fa"), // Blue
        LEVEL_RESISTANCE("Resistance", "#f97316"); // Orange

        public final String label;
        public final String hexColor;

        EventType(String label, String hexColor) {
            this.label = label;
            this.hexColor = hexColor;
        }
    }

    // Unique identifier
    private String id;

    // Time of the event (x-axis position)
    private Instant timestamp;
    private int openTimeInSeconds;

    // Event details
    private EventType type;
    private String label;
    private String description;
    private double value; // Price level, if applicable

    // Visual properties
    private String hexColor;
    private boolean visible;
    private int lineWidth;

    public static ChartEvent of(EventType type, Instant timestamp, String label, String description) {
        return ChartEvent.builder()
                .id(java.util.UUID.randomUUID().toString())
                .timestamp(timestamp)
                .type(type)
                .label(label)
                .description(description)
                .hexColor(type.hexColor)
                .visible(true)
                .lineWidth(2)
                .build();
    }

    public static ChartEvent ofTrade(EventType type, Instant timestamp, double price, String description) {
        return ChartEvent.builder()
                .id(java.util.UUID.randomUUID().toString())
                .timestamp(timestamp)
                .type(type)
                .label(type.label)
                .description(description)
                .value(price)
                .hexColor(type.hexColor)
                .visible(true)
                .lineWidth(2)
                .build();
    }

    public static ChartEvent ofNews(String newsTitle, Instant timestamp, String impact) {
        return ChartEvent.builder()
                .id(java.util.UUID.randomUUID().toString())
                .timestamp(timestamp)
                .type(EventType.NEWS)
                .label("📰 " + newsTitle)
                .description(impact)
                .hexColor(EventType.NEWS.hexColor)
                .visible(true)
                .lineWidth(1)
                .build();
    }

    public Color getColor() {
        return Color.web(hexColor);
    }
}
