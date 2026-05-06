package org.investpro.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.market.NewsEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Service for managing economic calendar news events.
 * Handles news event storage, blackout period enforcement, and signal
 * generation.
 */
@Slf4j
public class NewsDataProvider {

    private final List<NewsEvent> newsEvents = new CopyOnWriteArrayList<>();
    private final List<NewsEventListener> listeners = new CopyOnWriteArrayList<>();

    @Getter
    private boolean newsBlackoutEnabled = true; // Master toggle for news lockout

    // Calendar of major economic indicators by currency
    private static final Map<String, String[]> MAJOR_INDICATORS = Map.ofEntries(
            Map.entry("USD", new String[] {
                    "FOMC Meeting", "Non-Farm Payroll", "CPI", "Consumer Sentiment",
                    "Unemployment Rate", "GDP", "Retail Sales", "PPI", "Fed Funds Rate"
            }),
            Map.entry("EUR", new String[] {
                    "ECB Interest Rate", "Eurozone CPI", "Manufacturing PMI",
                    "Services PMI", "German IFO", "Unemployment Rate"
            }),
            Map.entry("GBP", new String[] {
                    "Bank of England Rate", "UK Inflation", "Retail Sales",
                    "Manufacturing PMI", "Services PMI"
            }),
            Map.entry("JPY", new String[] {
                    "BOJ Meeting", "Japan CPI", "GDP", "Industrial Production",
                    "Nikkei 225", "Manufacturing PMI"
            }));

    /**
     * Add a news event to the calendar.
     */
    public void addNewsEvent(NewsEvent event) {
        newsEvents.add(event);
        log.info("News event added: {} at {}", event.getTitle(), event.getEventTime());
        notifyListeners(event, "ADDED");
    }

    /**
     * Get all upcoming news events (next 24 hours).
     */
    public List<NewsEvent> getUpcomingNewsEvents() {
        Instant now = Instant.now();
        Instant tomorrow = now.plus(24, ChronoUnit.HOURS);

        return newsEvents.stream()
                .filter(event -> !event.getEventTime().isBefore(now) &&
                        event.getEventTime().isBefore(tomorrow))
                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                .collect(Collectors.toList());
    }

    /**
     * Get high-impact news events for a specific currency.
     */
    public List<NewsEvent> getHighImpactEvents(String currency) {
        return newsEvents.stream()
                .filter(event -> event.getCurrency().equals(currency))
                .filter(event -> event.getImportance() == NewsEvent.Importance.HIGH ||
                        event.getImportance() == NewsEvent.Importance.CRITICAL)
                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                .collect(Collectors.toList());
    }

    /**
     * Check if trading is currently blocked by news events.
     */
    public boolean isNewsBlackoutActive() {
        if (!newsBlackoutEnabled) {
            return false;
        }

        return newsEvents.stream()
                .anyMatch(NewsEvent::isBlackoutActive);
    }

    /**
     * Get currently active blackout events (blocking trades).
     */
    public List<NewsEvent> getActiveBlackoutEvents() {
        return newsEvents.stream()
                .filter(NewsEvent::isBlackoutActive)
                .collect(Collectors.toList());
    }

    /**
     * Get events happening soon (within next 60 minutes).
     */
    public List<NewsEvent> getImmediateUpcomingEvents() {
        Instant now = Instant.now();
        Instant oneHourLater = now.plus(60, ChronoUnit.MINUTES);

        return newsEvents.stream()
                .filter(event -> event.getEventTime().isAfter(now) &&
                        event.getEventTime().isBefore(oneHourLater))
                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                .collect(Collectors.toList());
    }

    /**
     * Get news events affecting a specific trading pair currency.
     */
    public List<NewsEvent> getEventsForCurrency(String currency) {
        return newsEvents.stream()
                .filter(event -> event.getCurrency().equalsIgnoreCase(currency))
                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                .collect(Collectors.toList());
    }

    /**
     * Get news events in a time range (for chart display).
     */
    public List<NewsEvent> getEventsInRange(Instant startTime, Instant endTime) {
        return newsEvents.stream()
                .filter(event -> !event.getEventTime().isBefore(startTime) &&
                        !event.getEventTime().isAfter(endTime))
                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                .collect(Collectors.toList());
    }

    /**
     * Add a sample calendar for testing and demonstration.
     */
    public void loadSampleCalendar() {
        // Sample upcoming events
        Instant now = Instant.now();

        // USD Events (next week)
        addNewsEvent(new NewsEvent(
                "Federal Reserve Interest Rate Decision",
                "USD",
                now.plus(2, ChronoUnit.DAYS).plus(14, ChronoUnit.HOURS),
                NewsEvent.Importance.CRITICAL,
                NewsEvent.Sentiment.NEUTRAL));

        addNewsEvent(new NewsEvent(
                "Non-Farm Payroll Report",
                "USD",
                now.plus(4, ChronoUnit.DAYS).plus(13, ChronoUnit.HOURS),
                NewsEvent.Importance.CRITICAL,
                NewsEvent.Sentiment.NEUTRAL));

        // EUR Events
        addNewsEvent(new NewsEvent(
                "ECB Monetary Policy Decision",
                "EUR",
                now.plus(3, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS),
                NewsEvent.Importance.CRITICAL,
                NewsEvent.Sentiment.NEUTRAL));

        // GBP Events
        addNewsEvent(new NewsEvent(
                "UK Retail Sales Data",
                "GBP",
                now.plus(2, ChronoUnit.DAYS).plus(9, ChronoUnit.HOURS),
                NewsEvent.Importance.HIGH,
                NewsEvent.Sentiment.NEUTRAL));

        log.info("Sample economic calendar loaded with {} events", newsEvents.size());
    }

    /**
     * Mark event as processed.
     */
    public void markEventProcessed(String eventId) {
        newsEvents.stream()
                .filter(e -> e.getEventId().equals(eventId))
                .forEach(e -> {
                    e.setProcessed(true);
                    notifyListeners(e, "PROCESSED");
                });
    }

    /**
     * Get all events that should generate trading signals.
     */
    public List<NewsEvent> getSignalGeneratingEvents() {
        return newsEvents.stream()
                .filter(NewsEvent::isGenerateSignal)
                .filter(event -> !event.isProcessed())
                .collect(Collectors.toList());
    }

    /**
     * Register listener for news event changes.
     */
    public void addNewsEventListener(NewsEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove listener.
     */
    public void removeNewsEventListener(NewsEventListener listener) {
        listeners.remove(listener);
    }

    private void notifyListeners(NewsEvent event, String action) {
        listeners.forEach(listener -> listener.onNewsEventChange(event, action));
    }

    /**
     * Get total active blackouts.
     */
    public int getActiveBlackoutCount() {
        return (int) newsEvents.stream()
                .filter(NewsEvent::isBlackoutActive)
                .count();
    }

    /**
     * Clear all events (for testing).
     */
    public void clearAll() {
        newsEvents.clear();
    }

    /**
     * Listener interface for news event changes.
     */
    public interface NewsEventListener {
        void onNewsEventChange(NewsEvent event, String action);
    }
}

