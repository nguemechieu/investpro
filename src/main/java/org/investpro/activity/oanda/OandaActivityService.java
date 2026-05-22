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
import org.investpro.exchange.Oanda;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Syncs OANDA transactions into {@link BrokerActivityEvent}s.
 *
 * <p>Cursor = lastTransactionID (OANDA integer string).
 * Endpoint: GET /v3/accounts/{accountID}/transactions/sinceid?id={lastTransactionID}
 * Initial endpoint: GET /v3/accounts/{accountID}/transactions?from={from}
 */
@Slf4j
public class OandaActivityService extends AbstractExchangeActivityService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Oanda oanda;
    private final HttpClient http;
    private final String apiKey;
    private final String apiBaseUrl;

    public OandaActivityService(
            Oanda oanda,
            HttpClient http,
            String apiKey,
            String apiBaseUrl,
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            ActivityProjectionService projectionService
    ) {
        super(OandaActivityMapper.EXCHANGE_ID,
                oanda != null ? oanda.getAccountId() : null,
                activityRepository,
                checkpointRepository,
                projectionService);
        this.oanda = oanda;
        this.http = http;
        this.apiKey = apiKey;
        this.apiBaseUrl = apiBaseUrl;
    }

    @Override
    public CompletableFuture<ActivitySyncResult> syncActivitySince(String cursor) {
        Instant startedAt = Instant.now();
        String resolvedAccountId = accountId;
        if (resolvedAccountId == null || resolvedAccountId.isBlank()) {
            return CompletableFuture.completedFuture(
                    ActivitySyncResult.builder()
                            .exchangeId(exchangeId)
                            .startedAt(startedAt)
                            .finishedAt(Instant.now())
                            .previousCursor(cursor)
                            .latestCursor(cursor)
                            .successful(false)
                            .warning("OANDA account ID not configured")
                            .build());
        }

        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = buildSyncUrl(resolvedAccountId, cursor);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Authorization", "Bearer " + apiKey)
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(30))
                        .GET()
                        .build();

                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401 || response.statusCode() == 403) {
                    log.warn("OANDA activity sync: auth error {} for account {}", response.statusCode(), resolvedAccountId);
                    return ActivitySyncResult.builder()
                            .exchangeId(exchangeId)
                            .accountId(resolvedAccountId)
                            .startedAt(startedAt)
                            .finishedAt(Instant.now())
                            .previousCursor(cursor)
                            .latestCursor(cursor)
                            .successful(false)
                            .error("HTTP " + response.statusCode() + ": authentication failure")
                            .build();
                }

                if (response.statusCode() != 200) {
                    log.warn("OANDA activity sync: unexpected HTTP {} for account {}", response.statusCode(), resolvedAccountId);
                    return ActivitySyncResult.builder()
                            .exchangeId(exchangeId)
                            .accountId(resolvedAccountId)
                            .startedAt(startedAt)
                            .finishedAt(Instant.now())
                            .previousCursor(cursor)
                            .latestCursor(cursor)
                            .successful(false)
                            .error("HTTP " + response.statusCode())
                            .build();
                }

                List<BrokerActivityEvent> events = parseTransactions(response.body(), resolvedAccountId);
                log.info("OANDA activity sync: {} transactions fetched for account {}", events.size(), resolvedAccountId);
                return processFetchedEvents(cursor, events, startedAt);

            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return errorResult(cursor, resolvedAccountId, startedAt, "Interrupted: " + ex.getMessage());
            } catch (Exception ex) {
                log.error("OANDA activity sync failed for account {}", resolvedAccountId, ex);
                return errorResult(cursor, resolvedAccountId, startedAt, ex.getMessage());
            }
        });
    }

    @Override
    public boolean supportsRealtimeActivityStream() {
        return oanda != null && Boolean.TRUE.equals(oanda.isConnected());
    }

    /** Accept pre-mapped transactions (e.g. from a WebSocket push or transaction client). */
    public ActivitySyncResult acceptMappedTransactions(List<BrokerActivityEvent> events, String previousCursor) {
        return processFetchedEvents(previousCursor, events, Instant.now());
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String buildSyncUrl(String accountId, String cursor) {
        String base = (apiBaseUrl == null || apiBaseUrl.isBlank())
                ? "https://api-fxtrade.oanda.com"
                : apiBaseUrl;
        if (cursor != null && !cursor.isBlank()) {
            return base + "/v3/accounts/" + accountId + "/transactions/sinceid?id=" + cursor;
        }
        // No cursor: fetch last 7 days
        String from = Instant.now().minusSeconds(7 * 24 * 3600).toString();
        return base + "/v3/accounts/" + accountId + "/transactions?from=" + from;
    }

    private List<BrokerActivityEvent> parseTransactions(String body, String resolvedAccountId) {
        List<BrokerActivityEvent> events = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode txns = root.path("transactions");
            if (txns.isMissingNode()) txns = root.path("transaction");
            if (txns.isArray()) {
                for (JsonNode txn : txns) {
                    try {
                        BrokerActivityEvent event = OandaActivityMapper.mapTransaction(txn);
                        events.add(event);
                    } catch (Exception e) {
                        log.warn("Failed to map OANDA transaction: {}", txn, e);
                    }
                }
            } else if (txns.isObject()) {
                events.add(OandaActivityMapper.mapTransaction(txns));
            }
        } catch (Exception e) {
            log.error("Failed to parse OANDA transactions response", e);
        }
        return events;
    }

    private ActivitySyncResult errorResult(String cursor, String accountId, Instant startedAt, String message) {
        return ActivitySyncResult.builder()
                .exchangeId(exchangeId)
                .accountId(accountId)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .previousCursor(cursor)
                .latestCursor(cursor)
                .successful(false)
                .error(message)
                .build();
    }
}
