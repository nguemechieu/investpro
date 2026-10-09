package org.investpro.exchange.schwab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
class SchwabApiClient {

    private static final ObjectMapper MAPPER = Schwab.MAPPER;

    private final SchwabApiConfig config;
    private final SchwabTokenManager tokenService;
    private final HttpClient httpClient;

    SchwabApiClient(
            @NotNull SchwabApiConfig config,
            @NotNull SchwabTokenManager tokenService,
            @NotNull HttpClient httpClient) {
        this.config = config;
        this.tokenService = tokenService;
        this.httpClient = httpClient;
    }

    private final java.util.concurrent.atomic.AtomicLong nextRequestAt = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong blockedUntil = new java.util.concurrent.atomic.AtomicLong();

    JsonNode fetchAccountNumbers() throws IOException, InterruptedException {
        return sendJson("GET", config.traderApiBaseUrl() + "/accounts/accountNumbers", null);
    }

    JsonNode fetchAccounts() throws IOException, InterruptedException {
        return sendJson("GET", config.traderApiBaseUrl() + "/accounts?fields=positions", null);
    }

    JsonNode fetchAccount(String accountHash) throws IOException, InterruptedException {
        return sendJson("GET", config.traderApiBaseUrl() + "/accounts/" + pathSegment(accountHash) + "?fields=positions", null);
    }

    private static String pathSegment(String value) throws IOException {
        if (value == null || value.isBlank()) throw new IOException("Schwab account or order identifier is missing.");
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    JsonNode fetchQuote(@NotNull String symbol) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        return sendJson("GET", config.marketDataBaseUrl() + "/" + encoded + "/quotes", null);
    }

    String placeOrder(@NotNull String accountId, @NotNull JsonNode payload) throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", config.traderApiBaseUrl() + "/accounts/" + pathSegment(accountId) + "/orders",
                payload.toString());
        String location = response.headers().firstValue("location").orElse("");
        if (!location.isBlank()) {
            try {
                String path = URI.create(location).getPath();
                String id = path.substring(path.lastIndexOf('/') + 1);
                if (!id.matches("[0-9]+")) throw new IllegalArgumentException();
                return id;
            } catch (RuntimeException error) {
                throw new IOException("Schwab accepted the request but returned an invalid order identifier. Check orders before resubmitting.");
            }
        }

        if (response.body() != null && !response.body().isBlank()) {
            JsonNode body = MAPPER.readTree(response.body());
            String orderId = body.path("orderId").asText("");
            if (!orderId.isBlank()) {
                return orderId;
            }
        }

        throw new IOException("Schwab accepted the request without an order identifier. Check open orders before submitting again.");
    }

    boolean cancelOrder(@NotNull String accountId, @NotNull String orderId) throws IOException, InterruptedException {
        HttpResponse<String> response = send("DELETE",
                config.traderApiBaseUrl() + "/accounts/" + pathSegment(accountId) + "/orders/" + pathSegment(orderId), null);
        return response.statusCode() == 200 || response.statusCode() == 202 || response.statusCode() == 204;
    }

    private JsonNode sendJson(String method, String url, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = send(method, url, body);
        if (response.body() == null || response.body().isBlank()) {
            return MAPPER.createObjectNode();
        }
        try { return MAPPER.readTree(response.body()); }
        catch (com.fasterxml.jackson.core.JsonProcessingException error) { throw new IOException("Schwab returned malformed API data."); }
    }

    private @NonNull HttpResponse<String> send(String method, String url, String body) throws IOException, InterruptedException {
        URI uri = URI.create(url);
        URI origin = URI.create(config.baseUrl());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !java.util.Objects.equals(uri.getHost(), origin.getHost())
                || uri.getPort() != origin.getPort() || uri.getUserInfo() != null)
            throw new IOException("Schwab request endpoint is not an authorized HTTPS origin.");
        String token = tokenService.getAccessToken();
        boolean refreshed = false;
        for (int attempt = 0; ; attempt++) {
        awaitRateLimit();

        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(30))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");

        if (body != null) {
            builder.header("Content-Type", "application/json");
        }

        HttpRequest request = builder.method(method, publisher).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 401) {
            if (refreshed) { tokenService.invalidate(); throw new SchwabAuthenticationException("Schwab authorization expired or rejected. Reauthorize in the Schwab panel."); }
            try { token = tokenService.refreshAfterUnauthorized(token).get(35, java.util.concurrent.TimeUnit.SECONDS); }
            catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException error) {
                throw new SchwabAuthenticationException("Schwab could not refresh authentication. Reauthorize or check connectivity.");
            }
            refreshed = true; continue;
        }
        if (response.statusCode() == 403) throw new SchwabApiException(403, "Schwab access denied. Check authorized accounts, app products and permissions.");
        if (response.statusCode() == 429) {
            long wait = retryDelay(response, attempt);
            long now = System.currentTimeMillis();
            long deadline = wait > Long.MAX_VALUE - now ? Long.MAX_VALUE : now + wait;
            blockedUntil.accumulateAndGet(deadline, Math::max);
            nextRequestAt.accumulateAndGet(deadline, Math::max);
            if (!"GET".equals(method) || attempt >= 2 || wait > 30000)
                throw new SchwabApiException(429, "Schwab rate limit reached. Wait before requesting again; verify any order status before resubmitting.");
            continue;
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new SchwabApiException(response.statusCode(), "Schwab API request failed (HTTP " + response.statusCode() + ").");
        return response;
        }
    }

    private void awaitRateLimit() throws IOException, InterruptedException {
        long now = System.currentTimeMillis();
        long slot = nextRequestAt.getAndUpdate(previous -> {
            long start = Math.max(previous, now);
            return start > Long.MAX_VALUE - 550 ? Long.MAX_VALUE : start + 550;
        });
        long wait = Math.max(0, slot - now);
        if (wait > 30000) throw new SchwabApiException(429, "Schwab requests are paused for the server rate limit. Try again later.");
        if (wait > 0) Thread.sleep(wait);
        // A different in-flight request may have received 429 while this caller waited.
        while (true) {
            long remaining = Math.max(0, blockedUntil.get() - System.currentTimeMillis());
            if (remaining == 0) return;
            if (System.currentTimeMillis() - now + remaining > 30000)
                throw new SchwabApiException(429, "Schwab requests are paused for the server rate limit. Try again later.");
            Thread.sleep(remaining);
        }
    }

    private static long retryDelay(HttpResponse<?> response, int attempt) {
        long delay = Math.min(8000, 1000L << Math.min(attempt, 3)) + java.util.concurrent.ThreadLocalRandom.current().nextLong(250);
        String value = response.headers().firstValue("Retry-After").orElse("");
        try { return Math.max(delay, Math.multiplyExact(Long.parseLong(value), 1000)); }
        catch (Exception ignored) {
            try { return Math.max(delay, java.time.ZonedDateTime.parse(value, java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - System.currentTimeMillis()); }
            catch (Exception malformed) { return delay; }
        }
    }
}
