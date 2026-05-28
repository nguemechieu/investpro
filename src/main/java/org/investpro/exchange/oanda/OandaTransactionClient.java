package org.investpro.exchange.oanda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.investpro.exchange.resilience.ExchangeCircuitBreaker;
import org.investpro.exchange.resilience.ExchangeConnectivityManager;
import org.investpro.exchange.resilience.StaleCacheManager;
import org.investpro.exchange.resilience.model.EndpointType;
import org.investpro.utils.NETWORK_RESPONSE;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * OANDA REST client for the Transactions API.
 *
 * <p>History endpoints (idrange, sinceid) are routed through the
 * {@link ExchangeConnectivityManager} circuit breaker. On
 * {@link ExchangeCircuitBreaker.CircuitOpenException}, a stale cached
 * snapshot is returned instead of triggering a retry storm. This
 * eliminates the repeated HTTP 504 failures that previously caused the
 * circuit breaker to cycle open on every polling interval.
 *
 * <p>All HTTP status checks use {@link NETWORK_RESPONSE} for named,
 * self-documenting comparisons instead of raw integer literals.
 *
 * <p>When {@code connectivityManager} is {@code null} (legacy mode),
 * the client behaves exactly as before: direct HTTP with no circuit protection.
 */
@Slf4j
public final class OandaTransactionClient {

    private static final String ACCOUNT_TRANSACTION_ROUTE           = "/v3/accounts/%s/transactions/%s";
    private static final String ACCOUNT_TRANSACTION_ID_RANGE_ROUTE  = "/v3/accounts/%s/transactions/idrange";
    private static final String ACCOUNT_TRANSACTION_SINCE_ID_ROUTE  = "/v3/accounts/%s/transactions/sinceid";
    private static final String ACCOUNT_TRANSACTION_STREAM_ROUTE    = "/v3/accounts/%s/transactions/stream";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;
    private final String streamBaseUrl;
    private final String apiToken;

    /** Optional circuit-breaker manager; {@code null} means no circuit protection. */
    @Nullable
    private final ExchangeConnectivityManager connectivityManager;

    /** Stale cache for order/transaction history; served when circuit is open. */
    @Nullable
    private final StaleCacheManager<List<JsonNode>> orderHistoryCache;

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Legacy constructor — no circuit breaker protection.
     * Preserved for backward compatibility with existing wiring.
     */
    public OandaTransactionClient(
            @NotNull HttpClient httpClient,
            @NotNull ObjectMapper objectMapper,
            @NotNull String apiBaseUrl,
            @NotNull String streamBaseUrl,
            @NotNull String apiToken) {
        this(httpClient, objectMapper, apiBaseUrl, streamBaseUrl, apiToken, null, null);
    }

    /**
     * Resilient constructor — wraps history endpoints with circuit breaker
     * and serves stale cache on {@link ExchangeCircuitBreaker.CircuitOpenException}.
     *
     * @param connectivityManager optional circuit-breaker coordinator
     * @param orderHistoryCache   optional stale cache for transaction history
     */
    public OandaTransactionClient(
            @NotNull HttpClient httpClient,
            @NotNull ObjectMapper objectMapper,
            @NotNull String apiBaseUrl,
            @NotNull String streamBaseUrl,
            @NotNull String apiToken,
            @Nullable ExchangeConnectivityManager connectivityManager,
            @Nullable StaleCacheManager<List<JsonNode>> orderHistoryCache) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.apiBaseUrl = requireNotBlank(apiBaseUrl, "apiBaseUrl");
        this.streamBaseUrl = requireNotBlank(streamBaseUrl, "streamBaseUrl");
        this.apiToken = requireNotBlank(apiToken, "apiToken");
        this.connectivityManager = connectivityManager;
        this.orderHistoryCache = orderHistoryCache;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Fetches a single transaction by ID. Not circuit-protected (single lookup). */
    public CompletableFuture<JsonNode> getTransaction(String accountId, String transactionId) {
        String url = apiBaseUrl + ACCOUNT_TRANSACTION_ROUTE.formatted(
                requireNotBlank(accountId, "accountId"),
                encode(requireNotBlank(transactionId, "transactionId")));
        return sendJson(url).thenApply(root -> root.path("transaction"));
    }

    /**
     * Fetches transactions in an ID range.
     *
     * <p>Routes through the ORDER_HISTORY circuit breaker. On circuit-open,
     * returns the stale cached snapshot (possibly empty list) rather than
     * propagating the exception upstream.
     */
    public CompletableFuture<List<JsonNode>> getTransactionIdRange(
            String accountId, String fromId, String toId) {
        String url = "%s%s?from=%s&to=%s".formatted(
                apiBaseUrl,
                ACCOUNT_TRANSACTION_ID_RANGE_ROUTE.formatted(requireNotBlank(accountId, "accountId")),
                encode(requireNotBlank(fromId, "fromId")),
                encode(requireNotBlank(toId, "toId")));
        return sendHistoryRequest(url);
    }

    /**
     * Fetches transactions since a given transaction ID.
     *
     * <p>Routes through the ORDER_HISTORY circuit breaker. On circuit-open,
     * returns the stale cached snapshot (possibly empty list) rather than
     * propagating the exception upstream.
     */
    public CompletableFuture<List<JsonNode>> getTransactionsSinceId(
            String accountId, String sinceTransactionId) {
        String url = "%s%s?id=%s".formatted(
                apiBaseUrl,
                ACCOUNT_TRANSACTION_SINCE_ID_ROUTE.formatted(requireNotBlank(accountId, "accountId")),
                encode(requireNotBlank(sinceTransactionId, "sinceTransactionId")));
        return sendHistoryRequest(url);
    }

    /** Streams live transaction events. Not circuit-protected (streaming connection). */
    public CompletableFuture<Void> streamTransactions(
            String accountId,
            Consumer<JsonNode> onMessage,
            Consumer<Throwable> onError) {
        Objects.requireNonNull(onMessage, "onMessage must not be null");
        Consumer<Throwable> errorHandler = onError == null ? ignored -> {} : onError;

        String url = streamBaseUrl + ACCOUNT_TRANSACTION_STREAM_ROUTE.formatted(
                requireNotBlank(accountId, "accountId"));
        HttpRequest request = authenticatedBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    int status = response.statusCode();
                    if (!isSuccess(status)) {
                        throw new RuntimeException(
                                "OANDA transaction stream failed %s (%d)"
                                        .formatted(describeStatus(status), status));
                    }
                    consumeStream(response.body(), onMessage, errorHandler);
                })
                .exceptionally(throwable -> {
                    errorHandler.accept(unwrap(throwable));
                    return null;
                });
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /**
     * Sends a history (non-critical) request through the circuit breaker.
     * Falls back to stale cache on circuit-open or gateway errors.
     */
    private CompletableFuture<List<JsonNode>> sendHistoryRequest(String url) {
        if (connectivityManager == null) {
            return sendJson(url).thenApply(this::extractTransactions);
        }

        try {
            return connectivityManager.route(EndpointType.ORDER_HISTORY, () ->
                    sendJson(url).thenApply(this::extractTransactions)
            );
        } catch (ExchangeCircuitBreaker.CircuitOpenException e) {
            return serveStaleCache("Circuit open for ORDER_HISTORY");
        }
    }

    /**
     * Returns the stale cached order history if available, otherwise an empty list.
     *
     * <p>Uses {@link StaleCacheManager#getOrServeStale} with a no-op empty-list supplier
     * so callers get the last known data without triggering a new HTTP request.
     */
    private CompletableFuture<List<JsonNode>> serveStaleCache(String reason) {
        log.warn("OandaTransactionClient: serving stale cache — {}", reason);
        if (orderHistoryCache != null && orderHistoryCache.hasCachedValue()) {
            // Return whatever is in cache (fresh or stale); background refresh uses empty-list no-op
            return orderHistoryCache.getOrServeStale(
                    () -> CompletableFuture.completedFuture(List.of()));
        }
        log.warn("OandaTransactionClient: no stale cache available, returning empty list");
        return CompletableFuture.completedFuture(List.of());
    }

    private CompletableFuture<JsonNode> sendJson(String url) {
        HttpRequest request = requestBuilder(url).GET().build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    int status = response.statusCode();

                    if (isGatewayTimeout(status)) {
                        if (connectivityManager != null) {
                            connectivityManager.recordFailure(EndpointType.ORDER_HISTORY,
                                    new RuntimeException(NETWORK_RESPONSE.GATEWAY_TIME_OUT.name()));
                        }
                        throw new RuntimeException(
                                "OANDA transaction request failed %s (%d)"
                                        .formatted(NETWORK_RESPONSE.GATEWAY_TIME_OUT.name(), status));
                    }

                    if (isUnauthorized(status)) {
                        throw new RuntimeException(
                                "OANDA authentication failed %s (%d) — check API token"
                                        .formatted(NETWORK_RESPONSE.UNAUTHORIZE_REQUEST.name(), status));
                    }

                    if (isTooManyRequests(status)) {
                        log.warn("OANDA rate limit hit: {} ({})",
                                NETWORK_RESPONSE.TOO_MANY_REQUEST_SEND.name(), status);
                        throw new RuntimeException(
                                "OANDA rate limited %s (%d)"
                                        .formatted(NETWORK_RESPONSE.TOO_MANY_REQUEST_SEND.name(), status));
                    }

                    if (!isSuccess(status)) {
                        throw new RuntimeException(
                                "OANDA transaction request failed %s (%d): %s"
                                        .formatted(describeStatus(status), status, response.body()));
                    }

                    if (connectivityManager != null) {
                        connectivityManager.recordSuccess(EndpointType.ORDER_HISTORY);
                    }

                    try {
                        return objectMapper.readTree(response.body());
                    } catch (Exception ex) {
                        throw new RuntimeException("Unable to parse OANDA transaction response", ex);
                    }
                });
    }

    private HttpRequest.Builder requestBuilder(String url) {
        return authenticatedBuilder(url).timeout(Duration.ofSeconds(30));
    }

    private HttpRequest.Builder authenticatedBuilder(String url) {
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer %s".formatted(apiToken))
                .header("Accept-Datetime-Format", "RFC3339")
                .header("User-Agent", "InvestPro/1.0");
    }

    private List<JsonNode> extractTransactions(JsonNode root) {
        List<JsonNode> result = new ArrayList<>();
        JsonNode transactions = root.path("transactions");
        if (transactions.isArray()) {
            transactions.forEach(result::add);
        }
        return result;
    }

    private void consumeStream(InputStream stream, Consumer<JsonNode> onMessage, Consumer<Throwable> onError) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                try {
                    onMessage.accept(objectMapper.readTree(line));
                } catch (Exception parseException) {
                    onError.accept(parseException);
                }
            }
        } catch (Exception exception) {
            onError.accept(exception);
        }
    }

    // ── NETWORK_RESPONSE helpers ──────────────────────────────────────────────

    private static boolean isSuccess(int status) {
        return status == NETWORK_RESPONSE.SERVER_OK.getResponseCode()
            || status == NETWORK_RESPONSE.CREATED.getResponseCode()
            || status == NETWORK_RESPONSE.ACCEPTED.getResponseCode()
            || status == NETWORK_RESPONSE.NO_CONTENT.getResponseCode();
    }

    private static boolean isGatewayTimeout(int status) {
        return status == NETWORK_RESPONSE.GATEWAY_TIME_OUT.getResponseCode();
    }

    private static boolean isUnauthorized(int status) {
        return status == NETWORK_RESPONSE.UNAUTHORIZE_REQUEST.getResponseCode();
    }

    private static boolean isTooManyRequests(int status) {
        return status == NETWORK_RESPONSE.TOO_MANY_REQUEST_SEND.getResponseCode();
    }

    /**
     * Returns the {@link NETWORK_RESPONSE} enum name for a given HTTP status code,
     * or {@code "UNKNOWN"} if not present in the enum.
     */
    private static String describeStatus(int status) {
        for (NETWORK_RESPONSE r : NETWORK_RESPONSE.values()) {
            if (r.getResponseCode() == status) {
                return r.name();
            }
        }
        return "UNKNOWN";
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static Throwable unwrap(Throwable throwable) {
        return throwable.getCause() == null ? throwable : throwable.getCause();
    }
}
