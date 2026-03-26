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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

@Getter
@Setter
public class CoinbaseTradeService {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseTradeService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final long UNAVAILABLE_PRODUCT_COOLDOWN_MS = TimeUnit.MINUTES.toMillis(5);
    private static final long TRANSIENT_FAILURE_COOLDOWN_MS = TimeUnit.SECONDS.toMillis(30);
    private static final ConcurrentMap<String, Long> PRICE_COOLDOWN_UNTIL = new ConcurrentHashMap<>();
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
                        if (response.statusCode() != 200) {
                            logger.warn("Unable to fetch Coinbase trades for {}. HTTP {}: {}", symbol, response.statusCode(), response.body());
                            return Collections.<Trade>emptyList();
                        }

                        JsonNode root = OBJECT_MAPPER.readTree(response.body());
                        List<Trade> trades = new ArrayList<>();
                        for (JsonNode node : root) {
                            Trade trade = new Trade();
                            trade.setTradePair(tradePair);
                            trade.setPrice(node.path("price").asDouble());
                            trade.setAmount(node.path("size").asDouble());
                            trade.setTimestamp(Instant.parse(node.path("time").asText()));
                            trade.setSide("buy".equalsIgnoreCase(node.path("side").asText()) ? Side.BUY : Side.SELL);
                            trades.add(trade);
                        }
                        return trades;
                    } catch (Exception e) {
                        logger.error("Error parsing trades for {}", symbol, e);
                        return Collections.<Trade>emptyList();
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Unable to fetch Coinbase trades for {}", symbol, throwable);
                    return Collections.<Trade>emptyList();
                });
    }

    public CompletableFuture<List<OrderBook>> getOrderBook(TradePair tradePair) {
        String symbol = tradePair.toString('-');
        String url = baseUrl + "/products/" + symbol + "/book?level=2";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    try {
                        if (response.statusCode() != 200) {
                            logger.warn("Unable to fetch Coinbase order book for {}. HTTP {}: {}", symbol, response.statusCode(), response.body());
                            return List.<OrderBook>of();
                        }

                        JsonNode json = OBJECT_MAPPER.readTree(response.body());
                        OrderBook orderBook = new OrderBook();
                        orderBook.setTradePair(tradePair);
                        orderBook.setTimestamp(Instant.now());

                        List<OrderBookEntry> bids = new ArrayList<>();
                        List<OrderBookEntry> asks = new ArrayList<>();
                        for (JsonNode bid : json.path("bids")) {
                            bids.add(new OrderBookEntry(bid.get(0).asDouble(), bid.get(1).asDouble()));
                        }
                        for (JsonNode ask : json.path("asks")) {
                            asks.add(new OrderBookEntry(ask.get(0).asDouble(), ask.get(1).asDouble()));
                        }

                        orderBook.setBidEntries(bids);
                        orderBook.setAskEntries(asks);
                        return List.of(orderBook);
                    } catch (Exception e) {
                        logger.error("Error parsing order book for {}", symbol, e);
                        return List.<OrderBook>of();
                    }
                })
                .exceptionally(throwable -> {
                    logger.error("Unable to fetch Coinbase order book for {}", symbol, throwable);
                    return List.of();
                });
    }

    public Optional<String> getRecentTrades(TradePair tradePair) {
        try {
            List<Trade> trades = fetchRecentTrades(tradePair).get();
            StringBuilder result = new StringBuilder("Recent Trades:\n");
            for (Trade trade : trades) {
                result.append(trade).append("\n");
            }
            return Optional.of(result.toString());
        } catch (Exception e) {
            logger.error("Failed to get recent trades: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    public Double[] getLatestPrice(TradePair pair) {
        String symbol = pair.toString('-');
        try {
            if (isCoolingDown(symbol)) {
                return new Double[]{Double.NaN, Double.NaN};
            }
            String url = baseUrl + "/products/" + symbol + "/ticker";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                if (isUnavailableProductResponse(response.statusCode(), response.body())) {
                    markUnavailable(symbol, "Coinbase latest price unavailable for " + pair + ". Using cooldown.");
                    return new Double[]{Double.NaN, Double.NaN};
                }
                logger.warn("Unable to fetch Coinbase latest price for {}. HTTP {}: {}", pair, response.statusCode(), response.body());
                return new Double[]{Double.NaN, Double.NaN};
            }

            PRICE_COOLDOWN_UNTIL.remove(symbol);
            JsonNode json = OBJECT_MAPPER.readTree(response.body());
            double lastPrice = json.path("price").asDouble(0.0);
            double bid = json.path("bid").asDouble(lastPrice);
            double ask = json.path("ask").asDouble(lastPrice);
            return new Double[]{bid, ask};
        } catch (Exception e) {
            markTransientFailure(symbol, "Coinbase latest price temporarily unavailable for " + pair + ".");
            return new Double[]{Double.NaN, Double.NaN};
        }
    }

    private boolean isCoolingDown(String symbol) {
        Long cooldownUntil = PRICE_COOLDOWN_UNTIL.get(symbol);
        return cooldownUntil != null && cooldownUntil > System.currentTimeMillis();
    }

    private void markUnavailable(String symbol, String message) {
        long now = System.currentTimeMillis();
        Long previous = PRICE_COOLDOWN_UNTIL.put(symbol, now + UNAVAILABLE_PRODUCT_COOLDOWN_MS);
        if (previous == null || previous <= now) {
            logger.warn(message);
        }
    }

    private void markTransientFailure(String symbol, String message) {
        long now = System.currentTimeMillis();
        Long previous = PRICE_COOLDOWN_UNTIL.put(symbol, now + TRANSIENT_FAILURE_COOLDOWN_MS);
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

    public void streamLiveTrades(String symbol, InProgressCandleUpdater updater) {
        logger.info("Coinbase live trade streaming is not enabled yet; using HTTP candle refresh fallback.");
    }

    public Optional<?> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant startTime, long epochSecond, int interval) {
        return Optional.empty();
    }
}
