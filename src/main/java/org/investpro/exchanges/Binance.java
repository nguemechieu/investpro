package org.investpro.exchanges;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import org.investpro.*;
import org.investpro.model.Candle;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

import static org.investpro.BinanceUtils.HmacSHA256;
import static org.investpro.exchanges.Binance.BinanceCandleDataSupplier.OBJECT_MAPPER;
import static org.investpro.exchanges.Oanda.numCandles;

public class Binance extends Exchange {

    static HttpClient client = HttpClient.newHttpClient();
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static final Logger logger = LoggerFactory.getLogger(Binance.class);
    public static final String API_URL = "https://api.binance.com/api/v3";  // Use Binance.com API
    String apiKey;
    static long timestamp = fetchServerTime();

    private static long fetchServerTime() {
        try {
            requestBuilder.uri(URI.create(
                    "%s/api/v3/time".formatted(API_URL)
            ));
            HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Error fetching server time: %d".formatted(response.statusCode()));
            }

            return Long.parseLong(response.body());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error fetching server time", e);
        }
    }

    public Binance(String apikey, String apiSecret) {
        super(apikey, apiSecret);
        Exchange.apiSecret = apiSecret;
        this.apiKey = apikey;
    }

    @Override
    public List<Fee> getTradingFee() {
        return null;
    }

    @Override
    public List<Account> getAccounts() throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(
                "%s/api/v3/account".formatted(API_URL)
        ));
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error fetching accounts: %d".formatted(response.statusCode()));
        }

        return Arrays.asList(OBJECT_MAPPER.readValue(response.body(), OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Account[].class)));



    }


    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(
                "%s/api/v3/order".formatted(API_URL)
        ));
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        requestBuilder.setHeader("Content-Type", "application/json");

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                tradePair.toString('-'),
                side,
                orderType,
                price,
                size,
                timestamp,
                stopLoss,
                takeProfit
        );

        requestBuilder.method("POST", HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(orderRequest)));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error creating order: %d".formatted(response.statusCode()));
        }
        logger.info("Order created: {}", response.body());
    }

    @Override
    public void cancelOrder(String orderId) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(
                API_URL + "/api/v3/order?orderId=" + orderId
        ));
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        requestBuilder.method("DELETE", HttpRequest.BodyPublishers.noBody());

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error cancelling order: %d".formatted(response.statusCode()));
        }
        logger.info("Order cancelled: {}", orderId);

    }


    @Override
    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        return null;
    }

    @Override
    public String getExchangeMessage() {
        return message;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new BinanceCandleDataSupplier(secondsPerCandle, tradePair); // Custom class to handle Binance candlestick data
    }


    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(Exchange exchange,TradePair tradePair, Instant stopAt, int secondsPerCandle,
                                                                 Consumer<List<Trade>> trades) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);

        CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            String uriStr = API_URL + "/api/v3/trades?symbol=" + tradePair.toString('-');

            try {
                HttpResponse<String> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(uriStr))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());

                JsonNode tradesResponse = new ObjectMapper().readTree(response.body());
                if (!tradesResponse.isArray() || tradesResponse.isEmpty()) {
                    futureResult.completeExceptionally(new RuntimeException("Binance trades response was empty or not an array"));
                } else {

                    for (JsonNode trade : tradesResponse) {
                        Instant time = Instant.ofEpochMilli(trade.get("time").asLong());
                        if (time.compareTo(stopAt) <= 0) {
                            futureResult.complete(Collections.emptyList());

                            break;
                        } else {
                            List<Trade> tr = new ArrayList<>();

                            OrderBook prices = new OrderBook();

                            Side side = Side.getSide(trade.get("isBuyerMaker").asBoolean() ? "SELL" : "BUY");
                            long qty;
                            if (side == Side.BUY) {
                                prices.getAskEntries().getLast().setPrice(trade.get("price").asDouble());
                                qty = trade.get("id").asLong();
                            } else {
                                prices.getBidEntries().getLast().setPrice(trade.get("price").asDouble());
                                qty = trade.get("qty").asLong();
                            }
                            Trade tradex = new Trade(exchange,
                                    tradePair,
                                 side,
                                    ENUM_ORDER_TYPE.LIMIT,
                                    BigDecimal.valueOf( trade.get("price").asDouble()),
                                    BigDecimal.valueOf( trade.get("qty").asDouble()),
                                    time,
                                    BigDecimal.valueOf(0),  BigDecimal.valueOf(0)
                            );
                            tr.add(tradex);
                            trades.accept(tr);
                        }
                    }
                }
            } catch (IOException | InterruptedException e) {
                futureResult.completeExceptionally(e);
            }
        });


        return futureResult;
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
                currentCandleStartedAt, ZoneOffset.UTC));

        return client.sendAsync(
                        requestBuilder.uri(URI.create(String.format(
                                        "%s/api/v3/klines?symbol=%s&interval=%s&startTime=%s", API_URL,
                                        tradePair.toString('-'),
                                        getBinanceGranularity(secondsPerCandle),
                                        startDateString
                                )))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    logger.info("Binance response: " + response);
                    JsonNode res;
                    try {
                        res = new ObjectMapper().readTree(response);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }

                    if (res.isEmpty()) {
                        return Optional.empty();
                    }

                    JsonNode currCandle = res.get(0);
                    // Instant openTime = Instant.ofEpochMilli(currCandle.get(0).asLong());

                    return Optional.of(new InProgressCandleData(
                            currCandle.get(6).asLong(),
                            currCandle.get(1).asDouble(),
                            currCandle.get(2).asDouble(),
                            currCandle.get(3).asDouble(),
                            Instant.now().toEpochMilli(),

                            currCandle.get(4).asDouble(),

                            currCandle.get(5).asLong()
                    ));
                });
    }

    @Override
    public List<Order> getPendingOrders() {
        return List.of(); // Binance doesn't have a specific endpoint for pending orders
    }


    @Override
    public List<Position> getPositions() {
        return null;
    }



    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        requestBuilder.uri(URI.create(API_URL + "/api/v3/openOrders?symbol=" + tradePair.toString('-')));
        HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("Binance response: %s".formatted(response.body()));
        ObjectMapper objectMapper = new ObjectMapper();
        return Arrays.asList(objectMapper.readValue(response.body(), Order[].class));
    }

    @Override
    public ObservableList<Order> getOrders() {
        return null; // Implementation depends on the use of ObservableList
    }

    @Override
    public List<TradePair> getTradePairs() {
        requestBuilder.uri(URI.create(API_URL + "/api/v3/exchangeInfo"));

        ArrayList<TradePair> tradePairs = new ArrayList<>();
        try {
            HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();
            JsonNode res = new ObjectMapper().readTree(response.body());
            logger.info("Binance response: %s".formatted(res));

            JsonNode symbols = res.get("symbols");
            for (JsonNode symbol : symbols) {
                String baseAsset = symbol.get("baseAsset").asText();
                String quoteAsset = symbol.get("quoteAsset").asText();
                TradePair tp = new TradePair(baseAsset, quoteAsset);
                tradePairs.add(tp);
                logger.info("Binance trade pair: %s".formatted(tp));
            }

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return tradePairs;
    }


    @Override
    public void cancelAllOrders() {
    }
    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return false; // WebSocket support for trades can be added if needed
    }

    @Override
    public List<Deposit> Deposit() {
        return null;
    }

    @Override
    public List<Withdrawal> Withdraw() {
        return null;
    }


    @Override
    public double fetchLivesBidAsk(TradePair tradePair) {
        return 0;
    }

    @Override
    public CustomWebSocketClient getWebsocketClient(Exchange exchange,TradePair tradePair, int secondsPerCandle) {
        return null;
    }

    @Override
    public List<Account> getAccountSummary() {
        return List.of();
    }

    @Override
    public List<Candle> getHistoricalCandles(String symbol, Instant startTime, Instant endTime, String interval) {
        return List.of();
    }

    // Binance supported granularity (intervals)

    /**
     * Returns the closest supported granularity (time interval) for Binance.
     *
     * @param secondsPerCandle the candle duration in seconds
     * @return the granularity in Binance format (e.g., "1m", "5m")
     */
    public String getBinanceGranularity(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "1m";
            case 300 -> "5m";
            case 900 -> "15m";
            case 3600 -> "1h";
            case 21600 -> "6h";
            case 86400 -> "1d";
            default -> throw new IllegalArgumentException("Unsupported granularity: " + secondsPerCandle);
        };
    }


    public static class BinanceCandleDataSupplier extends CandleDataSupplier {

        protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


        BinanceCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        }

        @Override
        public Set<Integer> getSupportedGranularities() {
            // Binance uses fixed time intervals (1m, 3m, 5m, etc.)
            // Here we map them to seconds
            return new TreeSet<>(Set.of(60, 180, 300, 900, 1800, 3600, 14400, 86400));
        }

        @Override
        public Future<List<CandleData>> get() {
            if (endTime.get() == -1) {
                endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
            }

            long endTimeMillis = (long) endTime.get() * 1000;
            long startTimeMillis = Math.max(endTimeMillis - (numCandles * secondsPerCandle * 1000L), 1422144000000L); // earliest timestamp

            // Binance uses string intervals for granularity like "1m", "5m", etc.
            String interval = getBinanceInterval(secondsPerCandle);

            // Construct the URL
            String url = String.format("%s/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                    API_URL, tradePair.toString('/'), interval, startTimeMillis, endTimeMillis, numCandles);
            logger.info("Fetching candle data for trade pair: {} from {} to {}", tradePair.toString('/'), startTimeMillis, endTimeMillis);

            @NotNull String signature;
            try {
                signature = HmacSHA256(String.valueOf(timestamp), apiSecret);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }

            requestBuilder.setHeader("X-MBX-APIKEY", apiSecret);
            HashMap<String, String> data0 = new HashMap<>();
            data0.put("timestamp", String.valueOf(timestamp));
            data0.put("signature", signature);
            requestBuilder.uri(URI.create(url));
            requestBuilder.method("POST", HttpRequest.BodyPublishers.ofString(String.valueOf(data0)));


            return client.sendAsync(
                            requestBuilder.uri(URI.create(url))

                                    .build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            logger.error("Failed to fetch candle data: %s for trade pair: %s".formatted(response.body(), tradePair.toString('/')));

                            new Messages(Alert.AlertType.ERROR, "Failed to fetch candle data\n%s".formatted(response));
                            throw new RuntimeException("Failed to fetch candle data: %s for trade pair: %s".formatted(response.body(), tradePair.toString('/')));
                        }

                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response.body());
                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (!res.isEmpty()) {
                            List<CandleData> candleData = new ArrayList<>();
                            for (JsonNode candle : res) {
                                candleData.add(new CandleData(
                                        candle.get(1).asDouble(),  // open price
                                        candle.get(4).asDouble(),  // close price
                                        candle.get(2).asDouble(),  // high price
                                        candle.get(3).asDouble(),  // low price
                                        candle.get(0).asInt(),  // open time (convert ms to seconds)
                                        0,
                                        candle.get(5).asLong()   // volume
                                ));
                            }
                            candleData.sort(Comparator.comparingLong(CandleData::getOpenTime));
                            endTime.set((int) (startTimeMillis / 1000));  // Update endTime for pagination
                            return candleData;
                        } else {
                            logger.info("No candle data found for trade pair: %s".formatted(tradePair));
                            return Collections.emptyList();
                        }
                    });
        }

        // Helper method to convert secondsPerCandle to Binance interval strings
        private @NotNull String getBinanceInterval(int secondsPerCandle) {
            return switch (secondsPerCandle) {
                case 60 -> "1m";
                case 180 -> "3m";
                case 300 -> "5m";
                case 900 -> "15m";
                case 1800 -> "30m";
                case 3600 -> "1h";
                case 14400 -> "4h";
                case 86400 -> "1d";
                default -> throw new IllegalArgumentException("Unsupported granularity: " + secondsPerCandle);
            };
        }


    }

}
