package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.investpro.model.OrderBook;
import org.investpro.investpro.model.OrderBookEntry;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public class BinanceUSMarketDataService {

    private static final Logger logger = LoggerFactory.getLogger(BinanceUSMarketDataService.class);
    private static final String API_URL = "https://api.binance.us/api/v3";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String apiKey;
    private final HttpClient client;

    public BinanceUSMarketDataService(String apiKey, HttpClient client) {
        this.apiKey = apiKey;
        this.client = client;
    }

    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        Objects.requireNonNull(tradePair, "TradePair cannot be null");
        CompletableFuture<List<OrderBook>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                String uriStr = API_URL + "/depth?symbol=" + tradePair.toString().replace("/", "") + "&limit=100";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(uriStr))
                        .header("X-MBX-APIKEY", apiKey)
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to fetch order book: " + response.body());
                }

                JsonNode rootNode = OBJECT_MAPPER.readTree(response.body());
                List<OrderBookEntry> bidEntries = parseOrderEntries(rootNode.get("bids"));
                List<OrderBookEntry> askEntries = parseOrderEntries(rootNode.get("asks"));

                OrderBook orderBook = new OrderBook(java.time.Instant.now(), bidEntries, askEntries);
                futureResult.complete(List.of(orderBook));

            } catch (IOException | InterruptedException e) {
                futureResult.completeExceptionally(e);
            }
        });

        return futureResult;
    }

    private List<OrderBookEntry> parseOrderEntries(JsonNode entriesNode) {
        List<OrderBookEntry> entries = new ArrayList<>();
        if (entriesNode != null && entriesNode.isArray()) {
            for (JsonNode entry : entriesNode) {
                double price = entry.get(0).asDouble();
                double size = entry.get(1).asDouble();
                entries.add(new OrderBookEntry(price, size));
            }
        }
        return entries;
    }

    public double fetchLiveBidAsk(TradePair tradePair) {
        try {
            String uri = API_URL + "/ticker/bookTicker?symbol=" + tradePair.toString().replace("/", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(uri))
                    .header("X-MBX-APIKEY", apiKey)
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.warn("Failed to fetch bid/ask prices: {}", response.body());
                return 0;
            }

            JsonNode node = OBJECT_MAPPER.readTree(response.body());
            double bid = node.get("bidPrice").asDouble();
            double ask = node.get("askPrice").asDouble();
            return (bid + ask) / 2;
        } catch (Exception e) {
            logger.error("Error fetching bid/ask for {}: {}", tradePair, e.getMessage());
            return 0;
        }
    }

    public String getExchangeMessage() {
        return "Binance US Market Data Service Active";
    }

    public double fetchLivesBidAsk(TradePair tradePair) {
        return 0;
    }

    public List<TradePair> getTradePairs() {
        return new ArrayList<>();
    }
}
