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
 * Sync endpoint: GET /v3/accounts/{accountID}/transactions/sinceid?id={lastTransactionID}
 * Initial endpoint: GET /v3/accounts/{accountID}/transactions?from={isoTime}
 */
@Slf4j
public class OandaActivityService extends AbstractExchangeActivityService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Oanda oanda;
    private final HttpClient http;
    private final String apiKey;
    private final String apiBaseUrl;

    /**
     * Primary constructor.
     *
     * @param oanda             OANDA exchange adapter (may be null in tests)
     * @param accountId         OANDA account ID (e.g. "101-001-12345678-001")
     * @param http              shared HttpClient
     * @param apiKey            OANDA bearer token
     * @param apiBaseUrl        API root, e.g. "https://api-fxtrade.oanda.com"
     * @param activityRepository broker event store
     * @param checkpointRepository cursor store
     * @param projectionService event projection engine
     */
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

    /** Legacy constructor for callers that supply only the Oanda adapter. */
    public OandaActivityService(
            Oanda oanda,
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            ActivityProjectionService projectionService
    ) {
        super(OandaActivityMapper.EXCHANGE_ID, null, activityRepository, checkpointRepository, projectionService);
        this.oanda = oanda;
        this.http = null;
        this.apiKey = null;
        this.apiBaseUrl = null;
    }

    @Override
    public CompletableFuture<ActivitySyncResult> syncActivitySince(String cursor) {
        Instant startedAt = Instant.now();
        String resolvedAccountId = accountId;

        if (http == null || apiKey == null || apiKey.isBlank()) {
            return CompletableFuture.completedFuture(ActivitySyncResult.builder()
                    .exchangeId(exchangeId)
                    .accountId(resolvedAccountId)
                    .startedAt(startedAt)
                    .finishedAt(Instant.now())
                    .previousCursor(cursor)
                    .latestCursor(cursor)
                    .successful(false)
                    .warning("OandaActivityService: HTTP client or API key not configured")
                    .build());
        }

        if (resolvedAccountId == null || resolvedAccountId.isBlank()) {
            return CompletableFuture.completedFuture(ActivitySyncResult.builder()
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
                    return errorResult(cursor, resolvedAccountId, startedAt,
                            "HTTP " + response.statusCode() + ": authentication failure");
                }

                if (response.statusCode() != 200) {
                    log.warn("OANDA activity sync: unexpected HTTP {} for {}", response.statusCode(), resolvedAccountId);
                    return errorResult(cursor, resolvedAccountId, startedAt, "HTTP " + response.statusCode());
                }

                List<BrokerActivityEvent> events = parseTransactions(response.body());
                log.info("OANDA activity sync: {} transactions for account {}", events.size(), resolvedAccountId);
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

    /** Accept pre-mapped transactions (e.g. from a WebSocket push). */
    public ActivitySyncResult acceptMappedTransactions(List<BrokerActivityEvent> events, String previousCursor) {
        return processFetchedEvents(previousCursor, events, Instant.now());
    }

    // ── Private helpers ────────────────────────────────────────────────────────────

    private String buildSyncUrl(String acctId, String cursor) {
        String base = (apiBaseUrl == null || apiBaseUrl.isBlank())
                ? "https://api-fxtrade.oanda.com"
                : apiBaseUrl;
        if (cursor != null && !cursor.isBlank()) {
            return base + "/v3/accounts/" + acctId + "/transactions/sinceid?id=" + cursor;
        }
        String from = Instant.now().minusSeconds(7L * 24 * 3600).toString();
        return base + "/v3/accounts/" + acctId + "/transactions?from=" + from;
    }

    private List<BrokerActivityEvent> parseTransactions(String body) {
        List<BrokerActivityEvent> events = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode txns = root.path("transactions");
            if (txns.isMissingNode()) txns = root.path("transaction");
            if (txns.isArray()) {
                for (JsonNode txn : txns) {
                    try { events.add(OandaActivityMapper.mapTransaction(txn)); }
                    catch (Exception e) { log.warn("Skipping unmappable OANDA transaction: {}", txn, e); }
                }
            } else if (txns.isObject()) {
                events.add(OandaActivityMapper.mapTransaction(txns));
            }
        } catch (Exception e) {
            log.error("Failed to parse OANDA transactions response", e);
        }
        return events;
    }

    private ActivitySyncResult errorResult(String cursor, String acctId, Instant startedAt, String message) {
        return ActivitySyncResult.builder()
                .exchangeId(exchangeId)
                .accountId(acctId)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .previousCursor(cursor)
                .latestCursor(cursor)
                .successful(false)
                .error(message)
                .build();
    }
}
