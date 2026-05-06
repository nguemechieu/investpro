package org.investpro.models.market;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a significant economic or market news event that impacts trading.
 * Used for charting news markers and implementing news-based trading rules.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NewsEvent {

    public enum Importance {
        LOW, // Minor impact
        MEDIUM, // Moderate impact
        HIGH, // Significant impact
        CRITICAL // Extreme impact - usually triggers volatility
    }

    public enum Sentiment {
        BULLISH, // Positive for market
        BEARISH, // Negative for market
        NEUTRAL // No clear directional bias
    }

    private String eventId;
    private String title;
    private String description;
    private String currency; // e.g., USD, EUR, GBP - affected currency
    private Instant eventTime; // When the news is announced
    private Instant createdAt; // When the event was added to system
    private Importance importance;
    private Sentiment sentiment;

    // Lock/blackout window settings
    private int minutesBefore; // Minutes before event to avoid trading (default: 30)
    private int minutesAfter; // Minutes after event to avoid trading (default: 30)
    private boolean blackoutEnabled; // Whether trading is blocked during this event

    // Signal generation
    private boolean generateSignal; // Whether to create trading signals for this event
    private String signalStrength; // STRONG, MODERATE, WEAK

    // Tracking
    private boolean processed; // Whether the event has been processed

    public NewsEvent(String title, String currency, Instant eventTime, Importance importance, Sentiment sentiment) {
        this.eventId = UUID.randomUUID().toString();
        this.title = title;
        this.currency = currency;
        this.eventTime = eventTime;
        this.createdAt = Instant.now();
        this.importance = importance;
        this.sentiment = sentiment;
        this.minutesBefore = 30;
        this.minutesAfter = 30;
        this.blackoutEnabled = importance == Importance.HIGH || importance == Importance.CRITICAL;
        this.generateSignal = true;
        this.processed = false;
    }

    /**
     * Check if trading should be blocked right now for this event.
     */
    public boolean isBlackoutActive() {
        if (!blackoutEnabled) {
            return false;
        }

        Instant now = Instant.now();
        Instant blockStart = eventTime.minusSeconds((long) minutesBefore * 60);
        Instant blockEnd = eventTime.plusSeconds((long) minutesAfter * 60);

        return !now.isBefore(blockStart) && !now.isAfter(blockEnd);
    }

    /**
     * Get the remaining time until this event (in seconds).
     */
    public long getSecondsUntilEvent() {
        return (eventTime.getEpochSecond() - Instant.now().getEpochSecond());
    }

    /**
     * Check if event is upcoming (within next 2 hours).
     */
    public boolean isUpcoming() {
        long secondsUntil = getSecondsUntilEvent();
        return secondsUntil > 0 && secondsUntil <= 7200; // 2 hours
    }

    /**
     * Check if event just occurred (within last 30 minutes).
     */
    public boolean isRecent() {
        long secondsSince = Instant.now().getEpochSecond() - eventTime.getEpochSecond();
        return secondsSince >= 0 && secondsSince <= 1800; // 30 minutes
    }
}
