package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.models.OrderBook;
import org.investpro.investpro.models.OrderBookEntry;
import org.investpro.investpro.models.TradePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;


public record CoinbaseMarketDataService(String apiKey, String apiSecret, HttpClient httpClient) {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseMarketDataService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long UNAVAILABLE_PRODUCT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long TRANSIENT_FAILURE_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(30);
    private static final ConcurrentMap<String, Long> ORDER_BOOK_COOLDOWN_UNTIL = new ConcurrentHashMap<>();

    @Contract(pure = true)
    public @NotNull String getExchangeMessage() {
        return "Coinbase Market Data Service Active";
    }

    public double fetchLivesBidAsk(TradePair tradePair) {
        try {
            String url = Coinbase.API_URL + "/products/" + tradePair.toString('-') + "/book?level=1";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Failed to fetch bid/ask prices: {}", response.body());
                return 0;
            }

            JsonNode node = OBJECT_MAPPER.readTree(response.body());
            double bid = node.get("bids").get(0).get(0).asDouble();
            double ask = node.get("asks").get(0).get(0).asDouble();
            return (bid + ask) / 2;

        } catch (Exception e) {
            logger.error("Error fetching live bid/ask", e);
            return 0;
        }
    }

    @Contract("_ -> new")
    public @NotNull CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture.supplyAsync(() -> {
            String symbol = tradePair.toString('-');
            if (isCoolingDown(symbol)) {
                return List.<OrderBook>of();
            }
            try {
                String url = Coinbase.API_URL + "/products/" + symbol + "/book?level=2";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    if (isUnavailableProductResponse(response.statusCode(), response.body())) {
                        markUnavailable(symbol, "Coinbase order book unavailable for " + symbol + ". Using cooldown.");
                        return List.<OrderBook>of();
                    }
                    logger.warn("Failed to fetch Coinbase order book for {}. HTTP {}: {}", symbol, response.statusCode(), response.body());
                    return List.<OrderBook>of();
                }

                ORDER_BOOK_COOLDOWN_UNTIL.remove(symbol);
                JsonNode root = OBJECT_MAPPER.readTree(response.body());
                List<OrderBookEntry> bids = new ArrayList<>();
                List<OrderBookEntry> asks = new ArrayList<>();

                root.get("bids").forEach(b -> bids.add(new OrderBookEntry(b.get(0).asDouble(), b.get(1).asDouble())));
                root.get("asks").forEach(a -> asks.add(new OrderBookEntry(a.get(0).asDouble(), a.get(1).asDouble())));

                OrderBook orderBook = new OrderBook(tradePair, java.time.Instant.now(), bids, asks);
                return List.of(orderBook);

            } catch (IOException | InterruptedException e) {
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                markTransientFailure(symbol, "Coinbase order book temporarily unavailable for " + symbol + ".");
                return List.of();
            }
        });
    }

    private boolean isCoolingDown(String symbol) {
        Long cooldownUntil = ORDER_BOOK_COOLDOWN_UNTIL.get(symbol);
        return cooldownUntil != null && cooldownUntil > System.currentTimeMillis();
    }

    private void markUnavailable(String symbol, String message) {
        long now = System.currentTimeMillis();
        Long previous = ORDER_BOOK_COOLDOWN_UNTIL.put(symbol, now + UNAVAILABLE_PRODUCT_COOLDOWN_MS);
        if (previous == null || previous <= now) {
            logger.warn(message);
        }
    }

    private void markTransientFailure(String symbol, String message) {
        long now = System.currentTimeMillis();
        Long previous = ORDER_BOOK_COOLDOWN_UNTIL.put(symbol, now + TRANSIENT_FAILURE_COOLDOWN_MS);
        if (previous == null || previous <= now) {
            logger.warn(message);
        }
    }

    private boolean isUnavailableProductResponse(int statusCode, String body) {
        if (body == null) {
            return statusCode == 404;
        }
        String normalized = body.toLowerCase();
        return statusCode == 404
                || normalized.contains("notfound")
                || normalized.contains("not found")
                || normalized.contains("delisted")
                || normalized.contains("not allowed for delisted products");
    }

    public List<TradePair> getTradePairs() {
        List<TradePair> tradePairs = new ArrayList<>();
        String url = Coinbase.API_URL + "/products";

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new IOException("Failed to fetch trade pairs. HTTP status: " + response.statusCode() +
                        ", Body: " + response.body());
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());

            for (JsonNode product : root) {
                JsonNode baseNode = product.get("base_currency");
                JsonNode quoteNode = product.get("quote_currency");

                if (baseNode != null && quoteNode != null) {
                    tradePairs.add(TradePair.of(baseNode.asText(), quoteNode.asText()));
                } else {
                    logger.warn("Invalid product data: {}", product);
                }
            }

        } catch (IOException | InterruptedException e) {
            logger.error("Error fetching trade pairs from Coinbase: {}", e.getMessage(), e);
            // Optionally rethrow or return partial result
        } catch (Exception e) {
            logger.error("Unexpected error in getTradePairs(): {}", e.getMessage(), e);
        }

        return tradePairs;
    }
}
