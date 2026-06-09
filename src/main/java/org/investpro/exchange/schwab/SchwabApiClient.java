package org.investpro.exchange.schwab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Slf4j
class SchwabApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SchwabApiConfig config;
    private final SchwabOAuthTokenService tokenService;
    private final HttpClient httpClient;

    SchwabApiClient(@NotNull SchwabApiConfig config, @NotNull SchwabOAuthTokenService tokenService) {
        this(config, tokenService, HttpClient.newHttpClient());
    }

    SchwabApiClient(
            @NotNull SchwabApiConfig config,
            @NotNull SchwabOAuthTokenService tokenService,
            @NotNull HttpClient httpClient) {
        this.config = config;
        this.tokenService = tokenService;
        this.httpClient = httpClient;
    }

    JsonNode fetchAccounts() throws IOException, InterruptedException {
        return sendJson("GET", config.traderApiBaseUrl() + "/accounts?fields=positions", null);
    }

    JsonNode fetchQuote(@NotNull String symbol) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(symbol, StandardCharsets.UTF_8);
        return sendJson("GET", config.marketDataBaseUrl() + "/" + encoded + "/quotes", null);
    }

    String placeOrder(@NotNull String accountId, @NotNull JsonNode payload) throws IOException, InterruptedException {
        HttpResponse<String> response = send("POST", config.traderApiBaseUrl() + "/accounts/" + accountId + "/orders",
                payload.toString());
        String location = response.headers().firstValue("location").orElse("");
        if (!location.isBlank()) {
            return location;
        }

        if (response.body() != null && !response.body().isBlank()) {
            JsonNode body = MAPPER.readTree(response.body());
            String orderId = body.path("orderId").asText("");
            if (!orderId.isBlank()) {
                return orderId;
            }
        }

        return "accepted";
    }

    boolean cancelOrder(@NotNull String accountId, @NotNull String orderId) throws IOException, InterruptedException {
        HttpResponse<String> response = send("DELETE",
                config.traderApiBaseUrl() + "/accounts/" + accountId + "/orders/" + orderId, null);
        return response.statusCode() == 200 || response.statusCode() == 202 || response.statusCode() == 204;
    }

    private JsonNode sendJson(String method, String url, String body) throws IOException, InterruptedException {
        HttpResponse<String> response = send(method, url, body);
        if (response.body() == null || response.body().isBlank()) {
            return MAPPER.createObjectNode();
        }
        return MAPPER.readTree(response.body());
    }

    private HttpResponse<String> send(String method, String url, String body) throws IOException, InterruptedException {
        String token = tokenService.getAccessToken();

        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);

        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");

        if (body != null) {
            builder.header("Content-Type", "application/json");
        }

        HttpRequest request = builder.method(method, publisher).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.debug("Schwab API call failed method={} url={} status={} body={}", method, url, response.statusCode(),
                    response.body());
            throw new IllegalStateException("Schwab API call failed with HTTP " + response.statusCode());
        }

        return response;
    }
}