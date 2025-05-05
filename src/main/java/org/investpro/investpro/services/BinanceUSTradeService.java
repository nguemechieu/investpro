package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class BinanceUSTradeService {

    private static final Logger logger = LoggerFactory.getLogger(BinanceUSTradeService.class);
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;
    private final String baseUrl = "https://api.binance.us/api/v3";
    private final ObjectMapper objectMapper = new ObjectMapper();

    public BinanceUSTradeService(String apiKey, String apiSecret, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

    public Optional<String> getOrderBook(String symbol) {
        return sendGetRequest("/depth?symbol=" + symbol + "&limit=100");
    }

    public Optional<String> getLatestPrice(String symbol) {
        return sendGetRequest("/ticker/price?symbol=" + symbol);
    }

    public Optional<String> getRecentTrades(String symbol) {
        return sendGetRequest("/trades?symbol=" + symbol + "&limit=100");
    }

    public Optional<String> placeMarketOrder(String symbol, double quantity, ENUM_ORDER_TYPE type, String side) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("symbol", symbol);
        params.put("side", side);
        params.put("type", type.name());
        params.put("quantity", String.valueOf(quantity));
        params.put("timestamp", String.valueOf(System.currentTimeMillis()));

        String query = buildQuery(params);
        String signature = hmacSha256(apiSecret, query);
        query += "&signature=" + signature;

        return sendPostRequest("/order", query);
    }

    public Optional<JsonNode> getLatestPriceAsJson(String symbol) {
        return getLatestPrice(symbol).flatMap(this::parseJson);
    }

    public Optional<JsonNode> getOrderBookAsJson(String symbol) {
        return getOrderBook(symbol).flatMap(this::parseJson);
    }

    public Optional<JsonNode> getRecentTradesAsJson(String symbol) {
        return getRecentTrades(symbol).flatMap(this::parseJson);
    }

    public CompletableFuture<Void> connectWebSocket(String symbol, WebSocket.Listener listener) {
        String wsUrl = "wss://stream.binance.us:9443/ws/" + symbol.toLowerCase() + "@trade";
        CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(wsUrl), listener);

        return wsFuture.thenAccept(ws -> logger.info("Connected to Binance US WebSocket for symbol: {}", symbol));
    }

    public void connectAndProcessTrades(String symbol, Consumer<JsonNode> tradeProcessor) {
        connectWebSocket(symbol, new WebSocket.Listener() {
            private final StringBuilder messageBuffer = new StringBuilder();

            @Override
            public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                messageBuffer.append(data);
                if (last) {
                    String completeMessage = messageBuffer.toString();
                    messageBuffer.setLength(0);
                    try {
                        JsonNode json = objectMapper.readTree(completeMessage);
                        tradeProcessor.accept(json);
                    } catch (IOException e) {
                        logger.error("Failed to parse trade data from WebSocket", e);
                    }
                }
                webSocket.request(1);
                return CompletableFuture.completedFuture(null);
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                logger.error("WebSocket error for symbol {}: {}", symbol, error.getMessage(), error);
            }
        });
    }

    private Optional<String> sendGetRequest(String endpoint) {
        return retry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("X-MBX-APIKEY", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            } else {
                logger.error("GET request failed: status code {} for endpoint {}", response.statusCode(), endpoint);
                return Optional.empty();
            }
        });
    }

    private Optional<String> sendPostRequest(String endpoint, String query) {
        return retry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint + "?" + query))
                    .header("X-MBX-APIKEY", apiKey)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                return Optional.of(response.body());
            } else {
                logger.error("POST request failed: status code {} for endpoint {}", response.statusCode(), endpoint);
                return Optional.empty();
            }
        });
    }

    private Optional<JsonNode> parseJson(String json) {
        try {
            return Optional.of(objectMapper.readTree(json));
        } catch (IOException e) {
            logger.error("JSON parsing error: ", e);
            return Optional.empty();
        }
    }

    private <T> Optional<T> retry(ApiCall<T> call) {
        Duration delay = INITIAL_DELAY;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                Optional<T> result = call.execute();
                if (result.isPresent()) {
                    return result;
                }
                Thread.sleep(delay.toMillis());
                delay = delay.multipliedBy(2);
            } catch (Exception e) {
                logger.warn("Retry {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                try {
                    Thread.sleep(delay.toMillis());
                    delay = delay.multipliedBy(2);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry sleep interrupted", ie);
                    break;
                }
            }
        }
        return Optional.empty();
    }

    private String hmacSha256(String secretKey, String data) {
        try {
            Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
            SecretKeySpec secret_key = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256_HMAC.init(secret_key);
            byte[] hash = sha256_HMAC.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            logger.error("Error generating HMAC SHA256 signature", e);
            throw new RuntimeException("Unable to sign request", e);
        }
    }

    private String buildQuery(Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!query.isEmpty()) query.append("&");
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
            query.append("=");
            query.append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
        }
        return query.toString();
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        Optional<T> execute() throws Exception;
    }
}
