package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.InProgressCandleUpdater;
import org.investpro.investpro.Side;
import org.investpro.investpro.exchanges.Oanda;
import org.investpro.investpro.model.OrderBook;
import org.investpro.investpro.model.Trade;
import org.investpro.investpro.model.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Getter
@Setter
public class OandaTradeService {

    private static final Logger logger = LoggerFactory.getLogger(OandaTradeService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;
    private final String baseUrl = Oanda.API_URL;

    public OandaTradeService(String apiKey, String apiSecret, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

    public CompletableFuture<List<Trade>> fetchRecentTrades(TradePair tradePair) {
        String instrument = tradePair.toString('_');
        String url = baseUrl + "/accounts/" + apiKey + "/trades?instrument=" + instrument;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        JsonNode root = OBJECT_MAPPER.readTree(response.body());
                        List<Trade> trades = new ArrayList<>();
                        JsonNode tradesNode = root.path("trades");
                        for (JsonNode node : tradesNode) {
                            Trade trade = new Trade();
                            trade.setPrice(node.get("price").asDouble());
                            trade.setSize(node.get("currentUnits").asDouble());
                            trade.setTimestamp(Instant.parse(node.get("openTime").asText()));
                            trade.setSide(Side.valueOf(node.get("currentUnits").asDouble() > 0 ? "BUY" : "SELL"));
                            trades.add(trade);
                        }
                        return trades;
                    } catch (Exception e) {
                        logger.error("Error parsing trades for {}", instrument, e);
                        return Collections.emptyList();
                    }
                });
    }

    public CompletableFuture<List<OrderBook>> getOrderBook(String instrument) {
        logger.info("🔄 Order book fetch not implemented for Oanda.");
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    public Optional<String> getRecentTrades(TradePair tradePair) {
        try {
            List<Trade> trades = fetchRecentTrades(tradePair).get();
            StringBuilder result = new StringBuilder("Recent Trades:\n");
            for (Trade trade : trades) {
                result.append(trade.toString()).append("\n");
            }
            return Optional.of(result.toString());
        } catch (Exception e) {
            logger.error("Failed to get recent trades: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Double[] getLatestPrice(TradePair pair) {
        try {
            String instrument = pair.toString('_');
            String url = baseUrl + "/accounts/" + apiKey + "/pricing?instruments=" + instrument;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Bearer " + apiSecret)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            JsonNode priceNode = json.path("prices").get(0);

            double bid = priceNode.path("bids").get(0).path("price").asDouble();
            double ask = priceNode.path("asks").get(0).path("price").asDouble();
            return new Double[]{bid, ask};

        } catch (Exception e) {
            logger.error("Failed to fetch latest price for {}: {}", pair, e.getMessage());
            return new Double[]{0.0, 0.0};
        }
    }


    public void streamLiveTrades(String instrument, InProgressCandleUpdater updater) {
        String url = baseUrl + "/accounts/" + apiKey + "/pricing/stream?instruments=" + instrument;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Bearer " + apiSecret)
                .GET()
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines())
                .thenAccept(response -> response.body().forEach(line -> {
                    try {
                        if (line.contains("price")) {
                            JsonNode json = OBJECT_MAPPER.readTree(line);
                            JsonNode priceNode = json.path("bids").get(0);
                            double price = priceNode.path("price").asDouble();

                            updater.update(price, Instant.now());
                        }
                    } catch (Exception e) {
                        logger.error("Failed to parse streaming line: {}", line, e);
                    }
                }));
    }
}