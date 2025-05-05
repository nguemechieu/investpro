package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.model.OrderBook;
import org.investpro.investpro.model.OrderBookEntry;
import org.investpro.investpro.model.TradePair;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;


public record CoinbaseMarketDataService(String apiKey, String apiSecret, HttpClient httpClient) {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseMarketDataService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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

    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String url = Coinbase.API_URL + "/products/" + tradePair.toString('-') + "/book?level=2";
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to fetch order book: " + response.body());
                }

                JsonNode root = OBJECT_MAPPER.readTree(response.body());
                List<OrderBookEntry> bids = new ArrayList<>();
                List<OrderBookEntry> asks = new ArrayList<>();

                root.get("bids").forEach(b -> bids.add(new OrderBookEntry(b.get(0).asDouble(), b.get(1).asDouble())));
                root.get("asks").forEach(a -> asks.add(new OrderBookEntry(a.get(0).asDouble(), a.get(1).asDouble())));

                return List.of(new OrderBook(Instant.now(), bids, asks));

            } catch (IOException | InterruptedException e) {
                logger.error("Failed to fetch order book", e);
                throw new RuntimeException(e);
            }
        });
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
