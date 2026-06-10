package org.investpro.activity.oanda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.AbstractExchangeActivityService;
import org.investpro.activity.ActivityCheckpointRepository;
import org.investpro.activity.ActivityProjectionService;
import org.investpro.activity.ActivitySyncResult;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.exchange.oanda.Oanda;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class OandaActivityService extends AbstractExchangeActivityService {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(20);

    private final Oanda oanda;
    private final HttpClient http;
    private final String apiKey;
    private final String apiBaseUrl;

    /** Primary 8-param constructor. */
    public OandaActivityService(
            Oanda oanda,
            String accountId,
            HttpClient http,
            String apiKey,
            String apiBaseUrl,
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            ActivityProjectionService projectionService
    ) {
        super(OandaActivityMapper.EXCHANGE_ID, accountId, activityRepository, checkpointRepository, projectionService);
        this.oanda = oanda;
        this.http = http;
        this.apiKey = apiKey;
        this.apiBaseUrl = apiBaseUrl;
    }

    /** Legacy 4-param constructor kept for backward compatibility with ExchangeActivityServiceFactory. */
    public OandaActivityService(
            Oanda oanda,
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            ActivityProjectionService projectionService
    ) {
        this(oanda, null,
                HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build(),
                null, null,
                activityRepository, checkpointRepository, projectionService);
    }

    @Override
    public CompletableFuture<ActivitySyncResult> syncActivitySince(String cursor) {
        Instant startedAt = Instant.now();
        return CompletableFuture.supplyAsync(() -> {
            if (apiKey == null || apiKey.isBlank()) {
                return ActivitySyncResult.builder()
                        .exchangeId(exchangeId)
                        .accountId(accountId)
                        .startedAt(startedAt)
                        .finishedAt(Instant.now())
                        .previousCursor(cursor)
                        .latestCursor(cursor)
                        .eventsFetched(0)
                        .eventsProcessed(0)
                        .successful(false)
                        .warning("OANDA apiKey not configured; skipping REST fetch")
                        .build();
            }
            if (accountId == null || accountId.isBlank()) {
                return ActivitySyncResult.builder()
                        .exchangeId(exchangeId)
                        .accountId(accountId)
                        .startedAt(startedAt)
                        .finishedAt(Instant.now())
                        .previousCursor(cursor)
                        .latestCursor(cursor)
                        .eventsFetched(0)
                        .eventsProcessed(0)
                        .successful(false)
                        .warning("OANDA accountId not configured; skipping REST fetch")
                        .build();
            }
            String base = (apiBaseUrl != null && !apiBaseUrl.isBlank()) ? apiBaseUrl : "https://api-fxtrade.oanda.com";
            String url;
            if (cursor != null && !cursor.isBlank()) {
                url = base + "/v3/accounts/" + accountId + "/transactions/sinceid?id=" + cursor;
            } else {
                String sevenDaysAgo = Instant.now().minus(Duration.ofDays(7)).toString();
                url = base + "/v3/accounts/" + accountId + "/transactions?from=" + sevenDaysAgo;
            }
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(HTTP_TIMEOUT)
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "application/json")
                        .GET()
                        .build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 401 || status == 403) {
                    return ActivitySyncResult.builder()
                            .exchangeId(exchangeId)
                            .accountId(accountId)
                            .startedAt(startedAt)
                            .finishedAt(Instant.now())
                            .previousCursor(cursor)
                            .latestCursor(cursor)
                            .eventsFetched(0)
                            .eventsProcessed(0)
                            .successful(false)
                            .warning("OANDA REST returned HTTP " + status + " (auth failure) for account=" + accountId)
                            .build();
                }
                if (status != 200) {
                    return ActivitySyncResult.builder()
                            .exchangeId(exchangeId)
                            .accountId(accountId)
                            .startedAt(startedAt)
                            .finishedAt(Instant.now())
                            .previousCursor(cursor)
                            .latestCursor(cursor)
                            .eventsFetched(0)
                            .eventsProcessed(0)
                            .successful(false)
                            .warning("OANDA REST returned HTTP " + status + " for account=" + accountId)
                            .build();
                }
                JsonNode root = MAPPER.readTree(response.body());
                JsonNode transactions = root.path("transactions");
                List<BrokerActivityEvent> events = new ArrayList<>();
                if (transactions.isArray()) {
                    for (JsonNode tx : transactions) {
                        try {
                            events.add(OandaActivityMapper.mapTransaction(tx));
                        } catch (Exception ex) {
                            log.warn("Failed to map OANDA transaction: {}", ex.getMessage());
                        }
                    }
                }
                return processFetchedEvents(cursor, events, startedAt);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return ActivitySyncResult.builder()
                        .exchangeId(exchangeId)
                        .accountId(accountId)
                        .startedAt(startedAt)
                        .finishedAt(Instant.now())
                        .previousCursor(cursor)
                        .latestCursor(cursor)
                        .eventsFetched(0).eventsProcessed(0)
                        .successful(false)
                        .warning("OANDA activity sync interrupted")
                        .build();
            } catch (Exception ex) {
                log.error("OANDA activity sync error for account={}", accountId, ex);
                return ActivitySyncResult.builder()
                        .exchangeId(exchangeId)
                        .accountId(accountId)
                        .startedAt(startedAt)
                        .finishedAt(Instant.now())
                        .previousCursor(cursor)
                        .latestCursor(cursor)
                        .eventsFetched(0).eventsProcessed(0)
                        .successful(false)
                        .warning("OANDA activity sync error: " + ex.getMessage())
                        .build();
            }
        });
    }

    @Override
    public boolean supportsRealtimeActivityStream() {
        return oanda != null && Boolean.TRUE.equals(oanda.isConnected());
    }

    public ActivitySyncResult acceptMappedTransactions(List<BrokerActivityEvent> events, String previousCursor) {
        return processFetchedEvents(previousCursor, events, Instant.now());
    }
}
