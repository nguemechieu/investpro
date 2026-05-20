package org.investpro.exchange.oanda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jetbrains.annotations.NotNull;

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

public final class OandaTransactionClient {

    private static final String ACCOUNT_TRANSACTION_ROUTE = "/v3/accounts/%s/transactions/%s";
    private static final String ACCOUNT_TRANSACTION_ID_RANGE_ROUTE = "/v3/accounts/%s/transactions/idrange";
    private static final String ACCOUNT_TRANSACTION_SINCE_ID_ROUTE = "/v3/accounts/%s/transactions/sinceid";
    private static final String ACCOUNT_TRANSACTION_STREAM_ROUTE = "/v3/accounts/%s/transactions/stream";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiBaseUrl;
    private final String streamBaseUrl;
    private final String apiToken;

    public OandaTransactionClient(
            @NotNull HttpClient httpClient,
            @NotNull ObjectMapper objectMapper,
            @NotNull String apiBaseUrl,
            @NotNull String streamBaseUrl,
            @NotNull String apiToken) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.apiBaseUrl = requireNotBlank(apiBaseUrl, "apiBaseUrl");
        this.streamBaseUrl = requireNotBlank(streamBaseUrl, "streamBaseUrl");
        this.apiToken = requireNotBlank(apiToken, "apiToken");
    }

    public CompletableFuture<JsonNode> getTransaction(String accountId, String transactionId) {
        String url = apiBaseUrl + ACCOUNT_TRANSACTION_ROUTE.formatted(
                requireNotBlank(accountId, "accountId"),
                encode(requireNotBlank(transactionId, "transactionId")));
        return sendJson(url).thenApply(root -> root.path("transaction"));
    }

    public CompletableFuture<List<JsonNode>> getTransactionIdRange(String accountId, String fromId, String toId) {
        String url = "%s%s?from=%s&to=%s".formatted(
                apiBaseUrl,
                ACCOUNT_TRANSACTION_ID_RANGE_ROUTE.formatted(requireNotBlank(accountId, "accountId")),
                encode(requireNotBlank(fromId, "fromId")),
                encode(requireNotBlank(toId, "toId")));
        return sendJson(url).thenApply(this::extractTransactions);
    }

    public CompletableFuture<List<JsonNode>> getTransactionsSinceId(String accountId, String sinceTransactionId) {
        String url = "%s%s?id=%s".formatted(
                apiBaseUrl,
                ACCOUNT_TRANSACTION_SINCE_ID_ROUTE.formatted(requireNotBlank(accountId, "accountId")),
                encode(requireNotBlank(sinceTransactionId, "sinceTransactionId")));
        return sendJson(url).thenApply(this::extractTransactions);
    }

    public CompletableFuture<Void> streamTransactions(
            String accountId,
            Consumer<JsonNode> onMessage,
            Consumer<Throwable> onError) {
        Objects.requireNonNull(onMessage, "onMessage must not be null");
        Consumer<Throwable> errorHandler = onError == null ? ignored -> {
        } : onError;

        String url = streamBaseUrl + ACCOUNT_TRANSACTION_STREAM_ROUTE.formatted(
                requireNotBlank(accountId, "accountId"));
        HttpRequest request = authenticatedBuilder(url).GET().build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream())
                .thenAccept(response -> {
                    if (!isSuccess(response.statusCode())) {
                        throw new RuntimeException(
                                "OANDA transaction stream failed HTTP %d".formatted(response.statusCode()));
                    }
                    consumeStream(response.body(), onMessage, errorHandler);
                })
                .exceptionally(throwable -> {
                    errorHandler.accept(unwrap(throwable));
                    return null;
                });
    }

    private CompletableFuture<JsonNode> sendJson(String url) {
        HttpRequest request = requestBuilder(url).GET().build();
        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (!isSuccess(response.statusCode())) {
                        throw new RuntimeException("OANDA transaction request failed HTTP %d: %s"
                                .formatted(response.statusCode(), response.body()));
                    }
                    try {
                        return objectMapper.readTree(response.body());
                    } catch (Exception exception) {
                        throw new RuntimeException("Unable to parse OANDA transaction response", exception);
                    }
                });
    }

    private HttpRequest.Builder requestBuilder(String url) {
        return authenticatedBuilder(url)
                .timeout(Duration.ofSeconds(30));
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
                if (line.isBlank()) {
                    continue;
                }
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

    private boolean isSuccess(int statusCode) {
        return statusCode >= 200 && statusCode < 300;
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
