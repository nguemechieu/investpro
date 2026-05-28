package org.investpro.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.investpro.models.market.NewsEvent;
import org.investpro.utils.NETWORK_RESPONSE;
import org.jetbrains.annotations.NotNull;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Fetches the ForexFactory weekly economic calendar from:
 * <pre>https://nfs.faireconomy.media/ff_calendar_thisweek.json</pre>
 *
 * <p>Each JSON entry has the shape:
 * <pre>
 * {
 *   "title":    "Non-Farm Payroll",
 *   "country":  "USD",
 *   "date":     "2026-05-30T08:30:00-04:00",
 *   "impact":   "High",          // High | Medium | Low | Holiday
 *   "forecast": "180K",
 *   "previous": "177K"
 * }
 * </pre>
 *
 * <p>Impact mapping:
 * <ul>
 *   <li>{@code High}    → {@link NewsEvent.Importance#HIGH} with blackout enabled</li>
 *   <li>{@code Medium}  → {@link NewsEvent.Importance#MEDIUM}</li>
 *   <li>{@code Low}     → {@link NewsEvent.Importance#LOW}</li>
 *   <li>{@code Holiday} → {@link NewsEvent.Importance#LOW}, blackout disabled</li>
 * </ul>
 *
 * <p>HTTP responses are classified using {@link NETWORK_RESPONSE}.
 */
@Slf4j
public final class ForexFactoryCalendarService {

    private static final String CALENDAR_URL =
            "https://nfs.faireconomy.media/ff_calendar_thisweek.json";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public ForexFactoryCalendarService() {
        this(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build(),
            new ObjectMapper()
        );
    }

    public ForexFactoryCalendarService(
            @NotNull HttpClient httpClient,
            @NotNull ObjectMapper objectMapper) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches this week's economic calendar asynchronously.
     *
     * @return a future that resolves to the list of parsed {@link NewsEvent}s,
     *         or an empty list if the request fails
     */
    public CompletableFuture<List<NewsEvent>> fetchThisWeekAsync() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(CALENDAR_URL))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .header("User-Agent", "InvestPro/1.0")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();
                    if (!isOk(status)) {
                        log.warn("ForexFactory calendar returned {} ({})",
                                status, describeStatus(status));
                        return List.<NewsEvent>of();
                    }
                    return parseEvents(response.body());
                })
                .exceptionally(ex -> {
                    log.warn("ForexFactory calendar fetch failed: {}", ex.getMessage());
                    return List.of();
                });
    }

    // ─────────────────────────────────────────────────────────────────────────

    private List<NewsEvent> parseEvents(String json) {
        List<NewsEvent> result = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                log.warn("ForexFactory calendar: unexpected JSON structure");
                return result;
            }
            for (JsonNode node : root) {
                try {
                    result.add(parseEvent(node));
                } catch (Exception e) {
                    log.debug("Skipping malformed FF calendar entry: {}", e.getMessage());
                }
            }
            log.info("ForexFactory calendar: loaded {} events", result.size());
        } catch (Exception e) {
            log.warn("ForexFactory calendar parse error: {}", e.getMessage());
        }
        return result;
    }

    private NewsEvent parseEvent(JsonNode node) {
        String title    = node.path("title").asText("");
        String country  = node.path("country").asText("");
        String dateStr  = node.path("date").asText("");
        String impact   = node.path("impact").asText("Low");
        String forecast = node.path("forecast").asText("");
        String previous = node.path("previous").asText("");

        Instant eventTime = Instant.now();
        if (!dateStr.isBlank()) {
            try {
                eventTime = OffsetDateTime.parse(dateStr).toInstant();
            } catch (Exception ex) {
                log.debug("Could not parse FF date '{}': {}", dateStr, ex.getMessage());
            }
        }

        NewsEvent.Importance importance = mapImpact(impact);
        boolean isHoliday = "Holiday".equalsIgnoreCase(impact);

        NewsEvent event = new NewsEvent(
                title,
                country,
                eventTime,
                importance,
                NewsEvent.Sentiment.NEUTRAL
        );
        event.setDescription(buildDescription(impact, forecast, previous));

        if (isHoliday) {
            event.setBlackoutEnabled(false);
            event.setGenerateSignal(false);
        }

        return event;
    }

    private static NewsEvent.Importance mapImpact(String impact) {
        return switch (impact) {
            case "High"   -> NewsEvent.Importance.HIGH;
            case "Medium" -> NewsEvent.Importance.MEDIUM;
            default       -> NewsEvent.Importance.LOW;
        };
    }

    private static String buildDescription(String impact, String forecast, String previous) {
        StringBuilder sb = new StringBuilder("[FF] Impact: ").append(impact);
        if (!forecast.isBlank()) sb.append(" | Forecast: ").append(forecast);
        if (!previous.isBlank()) sb.append(" | Previous: ").append(previous);
        return sb.toString();
    }

    // ── NETWORK_RESPONSE helpers ──────────────────────────────────────────────

    /** Returns {@code true} when the HTTP status indicates a successful 2xx response. */
    private static boolean isOk(int status) {
        return status == NETWORK_RESPONSE.SERVER_OK.getResponseCode()
            || status == NETWORK_RESPONSE.CREATED.getResponseCode()
            || status == NETWORK_RESPONSE.ACCEPTED.getResponseCode()
            || status == NETWORK_RESPONSE.NO_CONTENT.getResponseCode();
    }

    /**
     * Returns a human-readable description of a status code using the
     * {@link NETWORK_RESPONSE} enum, falling back to the raw code when unknown.
     */
    private static String describeStatus(int status) {
        for (NETWORK_RESPONSE r : NETWORK_RESPONSE.values()) {
            if (r.getResponseCode() == status) {
                return r.name();
            }
        }
        return "UNKNOWN";
    }
}
