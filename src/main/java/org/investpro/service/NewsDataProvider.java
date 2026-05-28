package org.investpro.service;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.market.NewsEvent;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for managing economic calendar news events AND RSS news.
 * Handles:
 * - Economic calendar events with blackout periods
 * - Real-time RSS news fetching with sentiment scoring
 * - ForexFactory weekly economic calendar (live JSON feed)
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
        private final ForexFactoryCalendarService forexFactoryCalendarService;

        private boolean newsBlackoutEnabled = true;

        @SuppressWarnings("unused")
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

        /** Default constructor — uses default RSS and ForexFactory services. */
        public NewsDataProvider() {
                this.rssNewsService = new RssNewsService();
                this.forexFactoryCalendarService = new ForexFactoryCalendarService();
        }

        /** Constructor with custom RSS news service. */
        public NewsDataProvider(RssNewsService rssNewsService) {
                this.rssNewsService = rssNewsService != null ? rssNewsService : new RssNewsService();
                this.forexFactoryCalendarService = new ForexFactoryCalendarService();
        }

        // ------------------------------------------------------------------
        // FOREXFACTORY CALENDAR
        // ------------------------------------------------------------------

        /**
         * Fetches this week's economic calendar from ForexFactory and merges the
         * events into the local calendar, skipping exact duplicates (same title + time).
         *
         * <p>This is an async, non-blocking call. Events are added as they arrive.
         */
        public void loadForexFactoryCalendar() {
                log.info("Loading ForexFactory calendar...");
                forexFactoryCalendarService.fetchThisWeekAsync().thenAccept(events -> {
                        int added = 0;
                        for (NewsEvent event : events) {
                                if (!isDuplicate(event)) {
                                        addNewsEvent(event);
                                        added++;
                                }
                        }
                        log.info("ForexFactory calendar: {} new events merged (total: {})", added, newsEvents.size());
                }).exceptionally(ex -> {
                        log.warn("ForexFactory calendar load failed: {}", ex.getMessage());
                        return null;
                });
        }

        /**
         * Starts a scheduled refresh of the ForexFactory calendar.
         *
         * <p>An initial fetch is performed immediately, then repeated every
         * {@code intervalHours} hours. The scheduler is daemon-threaded and
         * will stop automatically when the JVM exits.
         *
         * @param intervalHours refresh interval in hours (minimum 1)
         */
        public ScheduledExecutorService startForexFactoryAutoRefresh(int intervalHours) {
                long interval = Math.max(1, intervalHours);
                ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                        Thread t = new Thread(r, "ff-calendar-refresh");
                        t.setDaemon(true);
                        return t;
                });
                scheduler.scheduleAtFixedRate(
                        this::loadForexFactoryCalendar,
                        0, interval, TimeUnit.HOURS
                );
                log.info("ForexFactory auto-refresh started (every {}h)", interval);
                return scheduler;
        }

        // ------------------------------------------------------------------
        // CALENDAR MANAGEMENT
        // ------------------------------------------------------------------

        /** Adds a news event to the calendar. */
        public void addNewsEvent(NewsEvent event) {
                newsEvents.add(event);
                log.info("News event added: {} at {}", event.getTitle(), event.getEventTime());
                notifyListeners(event, "ADDED");
        }

        /** Returns all upcoming events within a 7-day window. */
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

        /** Returns high-impact events for a specific currency. */
        public List<NewsEvent> getHighImpactEvents(String currency) {
                return newsEvents.stream()
                                .filter(event -> event.getCurrency().equals(currency))
                                .filter(event -> event.getImportance() == NewsEvent.Importance.HIGH ||
                                                event.getImportance() == NewsEvent.Importance.CRITICAL)
                                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                                .collect(Collectors.toList());
        }

        /** Returns {@code true} if any blackout window is currently active. */
        public boolean isNewsBlackoutActive() {
                if (!newsBlackoutEnabled) return false;
                return newsEvents.stream().anyMatch(NewsEvent::isBlackoutActive);
        }

        /** Returns all events with an active blackout window. */
        public List<NewsEvent> getActiveBlackoutEvents() {
                return newsEvents.stream()
                                .filter(NewsEvent::isBlackoutActive)
                                .collect(Collectors.toList());
        }

        /** Returns events occurring within the next 60 minutes. */
        public List<NewsEvent> getImmediateUpcomingEvents() {
                Instant now = Instant.now();
                Instant oneHourLater = now.plus(60, ChronoUnit.MINUTES);

                return newsEvents.stream()
                                .filter(event -> event.getEventTime().isAfter(now) &&
                                                event.getEventTime().isBefore(oneHourLater))
                                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                                .collect(Collectors.toList());
        }

        /** Returns all events affecting a specific currency. */
        public List<NewsEvent> getEventsForCurrency(String currency) {
                return newsEvents.stream()
                                .filter(event -> event.getCurrency().equalsIgnoreCase(currency))
                                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                                .collect(Collectors.toList());
        }

        /** Returns events within a time range (for chart markers). */
        public List<NewsEvent> getEventsInRange(Instant startTime, Instant endTime) {
                return newsEvents.stream()
                                .filter(event -> !event.getEventTime().isBefore(startTime) &&
                                                !event.getEventTime().isAfter(endTime))
                                .sorted(Comparator.comparing(NewsEvent::getEventTime))
                                .collect(Collectors.toList());
        }

        /** Loads static sample events for testing when no live calendar is available. */
        public void loadSampleCalendar() {
                Instant now = Instant.now();

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

                addNewsEvent(new NewsEvent(
                                "ECB Monetary Policy Decision",
                                "EUR",
                                now.plus(3, ChronoUnit.DAYS).plus(12, ChronoUnit.HOURS),
                                NewsEvent.Importance.CRITICAL,
                                NewsEvent.Sentiment.NEUTRAL));

                addNewsEvent(new NewsEvent(
                                "UK Retail Sales Data",
                                "GBP",
                                now.plus(2, ChronoUnit.DAYS).plus(9, ChronoUnit.HOURS),
                                NewsEvent.Importance.HIGH,
                                NewsEvent.Sentiment.NEUTRAL));

                log.info("Sample economic calendar loaded with {} events", newsEvents.size());
        }

        /** Marks an event as processed (prevents duplicate signal generation). */
        public void markEventProcessed(String eventId) {
                newsEvents.stream()
                                .filter(e -> e.getEventId().equals(eventId))
                                .forEach(e -> {
                                        e.setProcessed(true);
                                        notifyListeners(e, "PROCESSED");
                                });
        }

        /** Returns unprocessed events eligible for signal generation. */
        public List<NewsEvent> getSignalGeneratingEvents() {
                return newsEvents.stream()
                                .filter(NewsEvent::isGenerateSignal)
                                .filter(event -> !event.isProcessed())
                                .collect(Collectors.toList());
        }

        public void addNewsEventListener(NewsEventListener listener) {
                listeners.add(listener);
        }

        public void removeNewsEventListener(NewsEventListener listener) {
                listeners.remove(listener);
        }

        // ------------------------------------------------------------------
        // RSS NEWS FETCHING AND SENTIMENT ANALYSIS
        // ------------------------------------------------------------------

        public List<Map<String, Object>> fetchSymbolNews(String symbol, String brokerType, int limit) {
                try {
                        return rssNewsService.fetchSymbolNews(symbol, brokerType, limit);
                } catch (Exception e) {
                        log.warn("Failed to fetch RSS news for {}: {}", symbol, e.getMessage());
                        return new ArrayList<>();
                }
        }

        public Map<String, List<Map<String, Object>>> fetchMultipleSymbolsNews(List<String> symbols,
                        String brokerType, int limitPerSymbol) {
                if (symbols == null || symbols.isEmpty()) return new HashMap<>();

                Map<String, List<Map<String, Object>>> results = new HashMap<>();
                for (String symbol : symbols) {
                        results.put(symbol, fetchSymbolNews(symbol, brokerType, limitPerSymbol));
                }
                return results;
        }

        public NewsBias summarizeNewsBias(List<Map<String, Object>> events, double maxAgeHours) {
                return summarizeNewsBias(events, maxAgeHours, 0.20, -0.20);
        }

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

        public void clearNewsCache() {
                rssNewsService.clearCache();
        }

        public List<Map<String, Object>> fetchGeneralMarketNews(int limit) {
                try {
                        return rssNewsService.fetchGeneralMarketNews(limit);
                } catch (Exception e) {
                        log.warn("Failed to fetch general market news: {}", e.getMessage());
                        return new ArrayList<>();
                }
        }

        public int getActiveBlackoutCount() {
                return (int) newsEvents.stream().filter(NewsEvent::isBlackoutActive).count();
        }

        public void clearAll() {
                newsEvents.clear();
        }

        // ------------------------------------------------------------------
        // Internal helpers
        // ------------------------------------------------------------------

        private boolean isDuplicate(NewsEvent incoming) {
                return newsEvents.stream().anyMatch(existing ->
                        existing.getTitle().equalsIgnoreCase(incoming.getTitle()) &&
                        existing.getEventTime().equals(incoming.getEventTime()));
        }

        private void notifyListeners(NewsEvent event, String action) {
                listeners.forEach(listener -> listener.onNewsEventChange(event, action));
        }

        /** Listener interface for news event changes. */
        public interface NewsEventListener {
                void onNewsEventChange(NewsEvent event, String action);
        }
}
