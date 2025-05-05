package org.investpro.investpro.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.InProgressCandleUpdater;
import org.investpro.investpro.Side;
import org.investpro.investpro.exchanges.Coinbase;
import org.investpro.investpro.model.OrderBook;
import org.investpro.investpro.model.OrderBookEntry;
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
public class CoinbaseTradeService {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseTradeService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private final String apiKey;
    private final String apiSecret;
    private final HttpClient httpClient;
    private final String baseUrl = Coinbase.API_URL;

    public CoinbaseTradeService(String apiKey, String apiSecret, HttpClient httpClient) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.httpClient = httpClient;
    }

    public CompletableFuture<List<Trade>> fetchRecentTrades(TradePair tradePair) {
        String symbol = tradePair.toString('-');
        String url = baseUrl + "/products/" + symbol + "/trades";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JavaClient")
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        JsonNode root = OBJECT_MAPPER.readTree(response.body());
                        List<Trade> trades = new ArrayList<>();
                        for (JsonNode node : root) {
                            Trade trade = new Trade();
                            trade.setPrice(node.get("price").asDouble());
                            trade.setAmount(node.get("size").asDouble());
                            trade.setTimestamp(Instant.parse(node.get("time").asText()));
                            trade.setSide("buy".equalsIgnoreCase(node.get("side").asText()) ? Side.BUY : Side.SELL);
                            trades.add(trade);
                        }
                        return trades;
                    } catch (Exception e) {
                        logger.error("Error parsing trades for {}", symbol, e);
                        return Collections.emptyList();
                    }
                });
    }

    public CompletableFuture<List<OrderBook>> getOrderBook(TradePair tradePair) {
        String symbol = tradePair.toString('-');
        String url = baseUrl + "/products/" + symbol + "/book?level=2";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return CompletableFuture.completedFuture(

                (List<OrderBook>) httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> {
                            try {
                                JsonNode json = OBJECT_MAPPER.readTree(response.body());
                                OrderBook orderBook = new OrderBook();
                                orderBook.setTradePair(tradePair);
                                orderBook.setTimestamp(Instant.now());

                                List<OrderBookEntry> bids = new ArrayList<>();
                                List<OrderBookEntry> asks = new ArrayList<>();

                                for (JsonNode bid : json.get("bids")) {
                                    double price = bid.get(0).asDouble();
                                    double size = bid.get(1).asDouble();
                                    bids.add(new OrderBookEntry(price, size));
                                }

                                for (JsonNode ask : json.get("asks")) {
                                    double price = ask.get(0).asDouble();
                                    double size = ask.get(1).asDouble();
                                    asks.add(new OrderBookEntry(price, size));
                                }

                                orderBook.setBidEntries(bids);
                                orderBook.setAskEntries(asks);
                                return orderBook;

                            } catch (Exception e) {
                                logger.error("Error parsing order book for {}", symbol, e);
                                return new OrderBook();
                            }
                        }));
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
            String symbol = pair.toString('-');
            String url = baseUrl + "/products/" + symbol + "/ticker";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JsonNode json = OBJECT_MAPPER.readTree(response.body());

            double bid = json.get("bid").asDouble();
            double ask = json.get("ask").asDouble();
            return new Double[]{bid, ask};

        } catch (Exception e) {
            logger.error("Failed to fetch latest price for {}: {}", pair, e.getMessage());
            return new Double[]{0.0, 0.0};
        }
    }

    public void streamLiveTrades(String symbol, InProgressCandleUpdater updater) {
        logger.info("🔄 Live trade streaming not implemented for Coinbase yet.");
        // TODO: Add WebSocket support if needed
    }

    public Optional<?> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant startTime, long epochSecond, int interval) {
        return getRecentTrades(tradePair);
    }
}
