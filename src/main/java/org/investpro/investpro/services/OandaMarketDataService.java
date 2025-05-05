package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.exchanges.Oanda;
import org.investpro.investpro.model.OrderBook;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

@Getter
@Setter
public class OandaMarketDataService {

    private static final Logger logger = LoggerFactory.getLogger(OandaMarketDataService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long RATE_LIMIT_DELAY_MS = 1000;
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_DELAY_MS = 500;
    private final String accountId;
    private final String apiSecret;
    private final HttpClient client;
    private final HttpRequest.Builder baseRequestBuilder;
    private final Semaphore rateLimiter = new Semaphore(1);
    private final ConcurrentHashMap<String, OrderBook> orderBookCache = new ConcurrentHashMap<>();

    public OandaMarketDataService(String accountId, String apiSecret, HttpClient client) {
        this.accountId = accountId;
        this.apiSecret = apiSecret;
        this.client = client;
        this.baseRequestBuilder = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + apiSecret)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json");
    }

    public String getExchangeMessage() {
        return "OANDA exchange connected.";
    }

    public double fetchLivesBidAsk(TradePair tradePair) {
        try {
            String url = String.format("%s/accounts/%s/pricing?instruments=%s",
                    Oanda.API_URL, accountId, tradePair.toString('_'));
            HttpRequest request = baseRequestBuilder.uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) return 0;

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            for (JsonNode priceNode : root.get("prices")) {
                double bid = priceNode.get("bids").get(0).get("price").asDouble();
                double ask = priceNode.get("asks").get(0).get("price").asDouble();
                return (bid + ask) / 2.0;
            }
        } catch (Exception e) {
            logger.error("Error fetching bid/ask", e);
        }
        return 0;
    }

    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        CompletableFuture<List<OrderBook>> future = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            String cacheKey = tradePair.toString('_');
            if (orderBookCache.containsKey(cacheKey)) {
                future.complete(List.of(orderBookCache.get(cacheKey)));
                return;
            }

            String url = String.format("%s/accounts/%s/pricing?instruments=%s",
                    Oanda.API_URL, accountId, cacheKey);
            try {
                rateLimiter.acquire();
                Thread.sleep(RATE_LIMIT_DELAY_MS);
                fetchWithRetries(url, tradePair, 0, future);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                future.completeExceptionally(e);
            } finally {
                rateLimiter.release();
            }
        });
        return future;
    }

    private void fetchWithRetries(String url, TradePair tradePair, int retryCount, CompletableFuture<List<OrderBook>> future) {
        try {
            HttpRequest request = baseRequestBuilder.uri(URI.create(url)).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429 && retryCount < MAX_RETRIES) {
                long backoff = INITIAL_DELAY_MS * (1L << retryCount);
                Thread.sleep(backoff);
                fetchWithRetries(url, tradePair, retryCount + 1, future);
                return;
            }

            JsonNode root = OBJECT_MAPPER.readTree(response.body());
            List<OrderBook> books = new ArrayList<>();
            for (JsonNode price : root.get("prices")) {
                double bid = price.get("bids").get(0).get("price").asDouble();
                double ask = price.get("asks").get(0).get("price").asDouble();
                double bidSize = price.get("bids").get(0).get("liquidity").asDouble();
                double askSize = price.get("asks").get(0).get("liquidity").asDouble();
                Instant time = Instant.parse(price.get("time").asText());

                OrderBook book = new OrderBook(tradePair, bid, ask, bidSize, askSize, time);
                books.add(book);
                orderBookCache.put(tradePair.toString('_'), book);
            }
            future.complete(books);
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
    }

    public List<TradePair> getTradePairs() throws Exception {
        List<TradePair> pairs = new ArrayList<>();
        String url = String.format("%s/accounts/%s/instruments", Oanda.API_URL, accountId);
        HttpRequest request = baseRequestBuilder.uri(URI.create(url)).GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        JsonNode root = OBJECT_MAPPER.readTree(response.body());
        JsonNode instruments = root.get("instruments");

        if (instruments != null) {
            for (JsonNode instrument : instruments) {
                String[] parts = instrument.get("name").asText().split("_");
                if (parts.length == 2) {
                    TradePair pair = TradePair.of(parts[0], parts[1]);
                    pairs.add(pair);
                }
            }
        }
        return pairs;
    }
}
