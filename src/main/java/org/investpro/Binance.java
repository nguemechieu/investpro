package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
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

import static org.investpro.BinanceUS.timestamp;
import static org.investpro.BinanceUtils.HmacSHA256;

public class Binance extends Exchange {

    static HttpClient client = HttpClient.newHttpClient();
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static final Logger logger = LoggerFactory.getLogger(Binance.class);
    public static final String API_URL = "https://api.binance.com/api/v3";  // Use Binance.com API
    String apiKey;

    public Binance(String apikey, String apiSecret) {
        super(apikey, apiSecret);
        Exchange.apiSecret = apiSecret;
        this.apiKey = apikey;
    }

    @Override
    public CompletableFuture<List<Fee>> getTradingFee() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public CompletableFuture<List<Account>> getAccounts() throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(
                "%s/api/v3/account".formatted(API_URL)
        ));
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error fetching accounts: %d".formatted(response.statusCode()));
        }
        ObjectMapper objectMapper = new ObjectMapper();
        List<Account> accounts = objectMapper.readValue(response.body(), objectMapper.getTypeFactory().constructCollectionType(List.class, Account.class));
        return CompletableFuture.completedFuture(accounts);


    }

    @Override
    public String getSymbol() {
        return tradePair.toString('/');
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
    public CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

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
        return CompletableFuture.completedFuture(orderId);
    }

    @Override
    public String getExchangeMessage() {
        return message;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new BinanceCandleDataSupplier(secondsPerCandle, tradePair); // Custom class to handle Binance candlestick data
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
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
                    List<Trade> trades = new ArrayList<>();
                    for (JsonNode trade : tradesResponse) {
                        Instant time = Instant.ofEpochMilli(trade.get("time").asLong());
                        if (time.compareTo(stopAt) <= 0) {
                            futureResult.complete(trades);
                            break;
                        } else {
                            trades.add(new Trade(
                                    tradePair,
                                    DefaultMoney.ofFiat(trade.get("price").asText(), tradePair.getCounterCurrency()),
                                    DefaultMoney.ofCrypto(trade.get("qty").asText(), tradePair.getBaseCurrency()),
                                    Side.getSide(trade.get("isBuyerMaker").asBoolean() ? "SELL" : "BUY"),
                                    trade.get("id").asLong(),
                                    time
                            ));
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
                    Instant openTime = Instant.ofEpochMilli(currCandle.get(0).asLong());

                    return Optional.of(new InProgressCandleData(
                            (int) openTime.getEpochSecond(),
                            currCandle.get(1).asDouble(),
                            currCandle.get(2).asDouble(),
                            currCandle.get(3).asDouble(),
                            (int)currCandle.get(6).asLong(),
                            currCandle.get(4).asDouble(),
                            currCandle.get(5).asDouble()
                    ));
                });
    }

    @Override
    public List<Order> getPendingOrders() throws IOException, InterruptedException {
        return List.of(); // Binance doesn't have a specific endpoint for pending orders
    }

    @Override
    public CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        requestBuilder.uri(URI.create(API_URL + "/api/v3/depth?symbol=" + tradePair.toString('-')));
        HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("Binance response: " + response.body());
        ObjectMapper objectMapper = new ObjectMapper();
        OrderBook orderBook = objectMapper.readValue(response.body(), OrderBook.class);
        return CompletableFuture.completedFuture(orderBook);
    }

    @Override
    public Position getPositions() throws IOException, InterruptedException, ExecutionException {
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
    public ObservableList<Order> getOrders() throws IOException, InterruptedException {
        return null; // Implementation depends on the use of ObservableList
    }

    @Override
    public CompletableFuture<ArrayList<TradePair>> getTradePairs() {
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
        return CompletableFuture.completedFuture(tradePairs);
    }

    @Override
    public void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

    }

    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        // Implement WebSocket closing if needed for live trade streams
    }

    @Override
    public List<PriceData> streamLivePrices(@NotNull TradePair symbol) {
        return List.of(); // WebSocket streaming for live prices not implemented here
    }

    @Override
    public List<CandleData> streamLiveCandlestick(@NotNull TradePair symbol, int intervalSeconds) {
        return List.of(); // WebSocket streaming for candlestick not implemented here
    }

    @Override
    public List<OrderBook> streamOrderBook(@NotNull TradePair tradePair) {
        return List.of(); // WebSocket streaming for order book not implemented here
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        return null; // Implement if Binance supports cancelling all orders at once
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return false; // WebSocket support for trades can be added if needed
    }

    @Override
    public ArrayList<CryptoDeposit> getCryptosDeposit() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public ArrayList<CryptoWithdraw> getCryptosWithdraw() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public List<Trade> getLiveTrades(List<TradePair> tradePairs) {
        return List.of();
    }

    // Binance supported granularity (intervals)
    private static final Set<String> SUPPORTED_GRANULARITIES = Set.of(
            "1m", "5m", "15m", "1h", "6h", "1d"
    );

    /**
     * Returns the closest supported granularity (time interval) for Binance.
     *
     * @param secondsPerCandle the candle duration in seconds
     * @return the granularity in Binance format (e.g., "1m", "5m")
     */
    public String getBinanceGranularity(int secondsPerCandle) {
        switch (secondsPerCandle) {
            case 60:
                return "1m";
            case 300:
                return "5m";
            case 900:
                return "15m";
            case 3600:
                return "1h";
            case 21600:
                return "6h";
            case 86400:
                return "1d";
            default:
                throw new IllegalArgumentException("Unsupported granularity: " + secondsPerCandle);
        }
    }

 //   Get Crypto Deposit History
//    Example
//
//# Get HMAC SHA256 signature
//
//    timestamp=`date +%s000`
//
//    api_key=<your_api_key>
//    secret_key=<your_secret_key>
//    coin=<coin>
//
//    api_url="https://api.binance.us"
//
//    signature=`echo -n "coin=$coin&timestamp=$timestamp" | openssl dgst -sha256 -hmac $secret_key`
//
//    curl -X "GET" "$api_url/sapi/v1/capital/deposit/hisrec?coin=$coin&timestamp=$timestamp&signature=$signature" \
//            -H "X-MBX-APIKEY: $api_key"
//    Response
//
//[
//    {
//        "amount": "8.73234",
//            "coin": "BNB",
//            "network": "BSC",
//            "status": 1,
//            "address": "0xd709f9d0bbc6b0e746a13142dfe353086edf87c2",
//            "addressTag": "",
//            "txId": "0xa9ebf3f4f60bc18bd6bdf4616ff8ffa14ef93a08fe79cad40519b31ea1044290",
//            "insertTime": 1638342348000,
//            "transferType": 0,
//            "confirmTimes": "0/0"
//    }

   // GET /sapi/v1/capital/deposit/hisrec (HMAC SHA256)
    // coin: The asset to get deposit history for (e.g., "BTC", "ETH", "BNB")
    // timestamp: UTC timestamp in milliseconds
    // signature: HMAC SHA256 signature of the parameters (coin, timestamp)
    // X-MBX-APIKEY: Binance API key


    private static class BinanceCandleDataSupplier extends CandleDataSupplier {

        protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


        BinanceCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        }

        @Override
        public Set<Integer> getSupportedGranularity() {
            // Binance uses fixed time intervals (1m, 3m, 5m, etc.)
            // Here we map them to seconds
            return new TreeSet<>(Set.of(60, 180, 300, 900, 1800, 3600, 14400, 86400));
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new BinanceCandleDataSupplier(secondsPerCandle, tradePair);
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

                            new Messages("Error", "Failed to fetch candle data\n%s".formatted(response));
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
                                        candle.get(0).asInt() / 1000,  // open time (convert ms to seconds)
                                        candle.get(5).asDouble()   // volume
                                ));
                            }
                            candleData.sort(Comparator.comparingInt(CandleData::getOpenTime));
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
