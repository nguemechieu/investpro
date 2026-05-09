package org.investpro.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.market.NewsEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * Service for managing economic calendar news events AND RSS news.
 * Handles:
 * - Economic calendar events with blackout periods
 * - Real-time RSS news fetching with sentiment scoring
 * - News bias summarization (buy/sell/neutral)
 * - Signal generation from news
 */
@Slf4j
@Getter
@Setter
public class NewsDataProvider {

        private final List<NewsEvent> newsEvents = new CopyOnWriteArrayList<>();
        private final List<NewsEventListener> listeners = new CopyOnWriteArrayList<>();
        private final RssNewsService rssNewsService;


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
         * Default constructor - initializes with default RSS news service.
         */
        public NewsDataProvider() {
                this.rssNewsService = new RssNewsService();
        }

        /**
         * Constructor with custom RSS news service.
         */
        public NewsDataProvider(RssNewsService rssNewsService) {
                this.rssNewsService = rssNewsService != null ? rssNewsService : new RssNewsService();
        }

        /**
         * Add a news event to the calendar.
         */
        public void addNewsEvent(NewsEvent event) {
                newsEvents.add(event);
                log.info("News event added: {} at {}", event.getTitle(), event.getEventTime());
                notifyListeners(event, "ADDED");
        }

        /**
         * Get all upcoming news events for the visible economic calendar window.
         */
        public List<NewsEvent> getUpcomingNewsEvents() {
                Instant now = Instant.now();
                Instant calendarWindowEnd = now.plus(7, ChronoUnit.DAYS);

                return newsEvents.stream()
                                .filter(event -> !event.getEventTime().isBefore(now) &&
                                                event.getEventTime().isBefore(calendarWindowEnd))
                                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                                .collect(Collectors.toList());
        }

        public void loadSampleCalendarIfEmpty() {
                if (newsEvents.isEmpty()) {
                        loadSampleCalendar();
                }
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

        // ------------------------------------------------------------------
        // RSS NEWS FETCHING AND SENTIMENT ANALYSIS
        // ------------------------------------------------------------------

        /**
         * Fetch real-time RSS news for a symbol with sentiment scoring.
         *
         * @param symbol     Symbol/asset code (e.g., BTC, EUR_USD, AAPL, SPY)
         * @param brokerType Exchange/broker type (crypto, forex, stock, etc.)
         * @param limit      Maximum number of news articles to fetch (1-50)
         * @return List of news events with sentiment scores
         */
        public List<Map<String, Object>> fetchSymbolNews(String symbol, String brokerType, int limit) {
                try {
                        return rssNewsService.fetchSymbolNews(symbol, brokerType, limit);
                } catch (Exception e) {
                        log.warn("Failed to fetch RSS news for {}: {}", symbol, e.getMessage());
                        return new ArrayList<>();
                }
        }

        /**
         * Fetch news for multiple symbols concurrently.
         *
         * @param symbols        List of symbols to fetch news for
         * @param brokerType     Broker/exchange type
         * @param limitPerSymbol Maximum articles per symbol
         * @return Map of symbol -> news events
         */
        public Map<String, List<Map<String, Object>>> fetchMultipleSymbolsNews(List<String> symbols,
                        String brokerType, int limitPerSymbol) {
                if (symbols == null || symbols.isEmpty()) {
                        return new HashMap<>();
                }

                Map<String, List<Map<String, Object>>> results = new HashMap<>();
                for (String symbol : symbols) {
                        results.put(symbol, fetchSymbolNews(symbol, brokerType, limitPerSymbol));
                }
                return results;
        }

        /**
         * Summarize news events into directional bias.
         *
         * @param events      List of news event maps from fetchSymbolNews
         * @param maxAgeHours Only consider news older than this many hours (default 18)
         * @return NewsBias with direction (buy/sell/neutral) and confidence
         */
        public NewsBias summarizeNewsBias(List<Map<String, Object>> events, double maxAgeHours) {
                return summarizeNewsBias(events, maxAgeHours, 0.20, -0.20);
        }

        /**
         * Summarize news events with custom thresholds.
         *
         * @param events        List of news event maps from fetchSymbolNews
         * @param maxAgeHours   Only consider news older than this many hours
         * @param buyThreshold  Score threshold for buy signal (default 0.20)
         * @param sellThreshold Score threshold for sell signal (default -0.20)
         * @return NewsBias with direction (buy/sell/neutral) and confidence
         */
        public NewsBias summarizeNewsBias(List<Map<String, Object>> events, double maxAgeHours,
                        double buyThreshold, double sellThreshold) {
                try {
                        return rssNewsService.summarizeNewsBias(events, maxAgeHours, buyThreshold, sellThreshold);
                } catch (Exception e) {
                        log.warn("Failed to summarize news bias: {}", e.getMessage());
                        return NewsBias.builder()
                                        .direction("neutral")
                                        .score(0.0)
                                        .confidence(0.0)
                                        .reason("Unable to analyze news")
                                        .build();
                }
        }

        /**
         * Convenience method: fetch news and return both events + bias in one call.
         *
         * @param symbol      Symbol to fetch news for
         * @param brokerType  Broker/exchange type
         * @param limit       Maximum articles
         * @param maxAgeHours Max age of news to consider for bias
         * @return Map containing: symbol, events (list), bias (NewsBias)
         */
        public Map<String, Object> fetchAndSummarizeNews(String symbol, String brokerType,
                        int limit, double maxAgeHours) {
                List<Map<String, Object>> events = fetchSymbolNews(symbol, brokerType, limit);
                NewsBias bias = summarizeNewsBias(events, maxAgeHours);

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("symbol", symbol != null ? symbol.toUpperCase().strip() : "");
                result.put("events", events);
                result.put("bias", bias.toMap());

                return result;
        }

        /**
         * Clear the news cache (useful after symbol changes or periodic refresh).
         */
        public void clearNewsCache() {
            rssNewsService.clearCache();
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
