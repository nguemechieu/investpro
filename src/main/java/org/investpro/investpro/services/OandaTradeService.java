package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.ENUM_ORDER_TYPE;
import org.investpro.investpro.exchanges.Oanda;
import org.investpro.investpro.model.Trade;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class OandaTradeService {

    private static final Logger logger = LoggerFactory.getLogger(OandaTradeService.class);
    private static final int MAX_RETRIES = 3;
    private static final Duration INITIAL_DELAY = Duration.ofSeconds(1);
    private final String accountId;
    private final String apiSecret;
    private final HttpClient httpClient;
    private final String baseUrl = Oanda.API_URL;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OandaTradeService(String accountId, String apiSecret, HttpClient httpClient) {
        this.accountId = accountId;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

    public Optional<String> getAccountSummary() {
        return sendGetRequest("/accounts/" + accountId + "/summary");
    }

    public Optional<String> getOpenTrades() {
        return sendGetRequest("/accounts/" + accountId + "/openTrades");
    }

    public Optional<String> getInstrumentCandles(String instrument, String granularity) {
        String endpoint = String.format("/instruments/%s/candles?granularity=%s", instrument, granularity);
        return sendGetRequest(endpoint);
    }


    public Optional<String> placeOrder(String instrument, @NotNull ENUM_ORDER_TYPE type, double units, double stopLoss, double takeProfit) {
        StringBuilder orderJson = new StringBuilder();
        orderJson.append("{\"order\":{")
                .append("\"instrument\":\"").append(instrument).append("\",")
                .append("\"units\":\"").append(units).append("\",");

        switch (type) {
            case MARKET -> orderJson.append("\"type\":\"MARKET\",");
            case LIMIT -> orderJson.append("\"type\":\"LIMIT\",");
            case STOP -> orderJson.append("\"type\":\"STOP\",");
            case MARKET_IF_TOUCHED -> orderJson.append("\"type\":\"MARKET_IF_TOUCHED\",");
        }

        orderJson.append("\"positionFill\":\"DEFAULT\"");

        if (stopLoss > 0) {
            orderJson.append(",\"stopLossOnFill\":{\"price\":\"").append(stopLoss).append("\"}");
        }
        if (takeProfit > 0) {
            orderJson.append(",\"takeProfitOnFill\":{\"price\":\"").append(takeProfit).append("\"}");
        }

        orderJson.append("}}");

        return sendPostRequest("/accounts/" + accountId + "/orders", orderJson.toString());
    }

    public Optional<String> modifyOrder(String orderId, double newUnits, double newStopLoss, double newTakeProfit) {
        StringBuilder orderJson = new StringBuilder();
        orderJson.append("{\"order\":{")
                .append("\"units\":\"").append(newUnits).append("\"");

        if (newStopLoss > 0) {
            orderJson.append(",\"stopLossOnFill\":{\"price\":\"").append(newStopLoss).append("\"}");
        }
        if (newTakeProfit > 0) {
            orderJson.append(",\"takeProfitOnFill\":{\"price\":\"").append(newTakeProfit).append("\"}");
        }

        orderJson.append("}}");

        return sendPutRequest("/accounts/" + accountId + "/orders/" + orderId, orderJson.toString());
    }

    public Optional<String> cancelOrder(String orderId) {
        return sendPutRequest("/accounts/" + accountId + "/orders/" + orderId + "/cancel", "{}");
    }

    public Optional<String> getOrderBook(String instrument) {
        return sendGetRequest("/instruments/" + instrument + "/orderBook");
    }

    public Optional<String> getLatestPrice(String instrument) {
        return sendGetRequest("/accounts/" + accountId + "/pricing?instruments=" + instrument);
    }

    public Optional<String> getRecentTrades(@NotNull TradePair instrument) {
        return Optional.of(Objects.requireNonNull(getOrderBook(instrument.toString('_')).orElse(null)));

        //sendGetRequest("/accounts/" + accountId + "/trades?instrument=" + instrument.toString('_'));
    }

    public Optional<JsonNode> getLatestPriceAsJson(String instrument) {
        return getLatestPrice(instrument).flatMap(this::parseJson);
    }

    public Optional<JsonNode> getOrderBookAsJson(String instrument) {
        return getOrderBook(instrument).flatMap(this::parseJson);
    }

    public Optional<JsonNode> getRecentTradesAsJson(TradePair instrument) {
        return getRecentTrades(instrument).flatMap(this::parseJson);
    }

    private @NotNull Optional<String> sendGetRequest(String endpoint) {
        return retry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Authorization", "Bearer " + getApiSecret())
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            } else {
                logger.error("GET request failed: status code {} for endpoint {}", response.statusCode(), endpoint);
                throw new RuntimeException(response.body());

                // return Optional.empty();
            }
        });
    }

    public CompletableFuture<Trade> fetchRecentTrades(TradePair tradePair) {
        Optional<String> res = getRecentTrades(tradePair);
        logger.info("Fetched trade data: {}", res);


        CompletableFuture<Trade> future = new CompletableFuture<>();
        try {
            ObjectMapper mapper = new ObjectMapper();
            List<Trade> trades = List.of();


            JsonNode trades0 = mapper.readValue(res.toString(), JsonNode.class);
            for (JsonNode jsonNode : trades0) {
                if (jsonNode.isArray()) {
                    trades = mapper.readValue(jsonNode.asText(), List.class);
                }

            }

            if (!trades.isEmpty()) {
                Trade tr = new Trade();
                future.complete(tr);
            } else {
                future.completeExceptionally(new RuntimeException("No trades found"));
            }
        } catch (Exception e) {
            logger.error("Failed to parse trades JSON", e);
            future.completeExceptionally(e);
        }

        return future;
    }

    private Optional<String> sendPostRequest(String endpoint, String jsonBody) {
        return retry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Authorization", "Bearer " + apiSecret)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 201 || response.statusCode() == 200) {
                return Optional.of(response.body());
            } else {
                logger.error("POST request failed: status code {} for endpoint {}", response.statusCode(), endpoint);
                return Optional.empty();
            }
        });
    }

    private Optional<String> sendPutRequest(String endpoint, String jsonBody) {
        return retry(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + endpoint))
                    .header("Authorization", "Bearer " + apiSecret)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return Optional.of(response.body());
            } else {
                logger.error("PUT request failed: status code {} for endpoint {}", response.statusCode(), endpoint);
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
                delay = delay.multipliedBy(2); // exponential backoff
            } catch (Exception e) {
                logger.warn("Retry {}/{} failed: {}", attempt, MAX_RETRIES, e.getMessage());
                try {
                    Thread.sleep(delay.toMillis());
                    delay = delay.multipliedBy(2); // exponential backoff
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    logger.error("Retry sleep interrupted", ie);
                    break;
                }
            }
        }
        return Optional.empty();
    }

    @FunctionalInterface
    private interface ApiCall<T> {
        Optional<T> execute() throws Exception;
    }
}
