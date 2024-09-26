package org.investpro;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.ObservableList;
import javafx.scene.control.Alert;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.investpro.BinanceUSClient.binanceUsRequest;

public class BinanceUs extends Exchange {
    private static final Logger logger = LoggerFactory.getLogger(BinanceUs.class);
    private static final String API_URL = "https://api.binance.us";
    private static final String HMAC_SHA256 = "HmacSHA256";
    static HttpClient client = HttpClient.newHttpClient();
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    HashMap<String, String> data0 = new HashMap<>();
    private String apiKey;
    private String apiSecret;

    public BinanceUs(String apiKey, String apiSecret) throws NoSuchAlgorithmException, InvalidKeyException {
        super(apiKey, apiSecret);
        this.message = "BinanceUs";

        // Set headers for the request
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("X-MBX-APIKEY", apiKey);
        // Generate signature using API key and secret
        requestBuilder.header(
                "signature", signer(apiSecret, data0));
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;

    }

    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static @NotNull String bytesToHex(byte @NotNull [] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
    private String message;

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    private @NotNull String signer(@NotNull String apiSecret, @NotNull Map<String, String> data)
            throws NoSuchAlgorithmException, InvalidKeyException {

        Mac hmacSHA256 = Mac.getInstance(HMAC_SHA256);
        SecretKeySpec secretKeySpec = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
        hmacSHA256.init(secretKeySpec);
        data.put("timestamp", String.valueOf(System.currentTimeMillis() / 1000));

        byte[] hmacData = hmacSHA256.doFinal(data.getOrDefault(
                "timestamp", String.valueOf(System.currentTimeMillis() / 1000)
        ).getBytes(StandardCharsets.UTF_8));
        return bytesToHex(hmacData);
    }

    // Make Binance US request with signature (GET request)
//

    @Override
    public CompletableFuture<Account> getAccounts() {
        Map<String, String> data = Map.of("timestamp", String.valueOf(System.currentTimeMillis()));
        client.sendAsync(requestBuilder.uri(URI.create("%s/api/v3/account".formatted(API_URL))).build(), HttpResponse.BodyHandlers.ofString())

                .thenApply(response -> {

                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Failed to get account data: status code %d".formatted(response.statusCode()));
                    }
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                        objectMapper.registerModule(new JavaTimeModule());
                        return objectMapper.readValue(response.body(), Account.class);
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to parse account data: %s".formatted(e.getMessage()), e);
                    }
                }).exceptionallyComposeAsync(
                        ex -> {
                            logger.error("Error getting accounts: {}", ex.getMessage());
                            return
                                    new CompletableFuture<>();
                        }

                );

        return new CompletableFuture<>();

    }

    @Override
    public Boolean isConnected() {
        return webSocketClient.isConnected();
    }

    @Override
    public String getSymbol() {
        return tradePair.toString('-');
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) {

        Map<String, String> data = new HashMap<>();

        // Add the necessary parameters for creating an order
        data.put("symbol", tradePair.toString('-')); // BTC-USD, etc.
        data.put("side", side.name()); // BUY or SELL
        data.put("type", orderType.name()); // Order type: LIMIT, MARKET, etc.

        // Add additional parameters based on the type of order
        if (orderType == ENUM_ORDER_TYPE.LIMIT) {
            data.put("price", String.valueOf(price));
            data.put("timeInForce", "GTC"); // Good 'Til Canceled (example)
        }

        data.put("quantity", String.valueOf(size)); // Size of the trade
        data.put("timestamp", String.valueOf(timestamp.getTime()));

        // Add optional stop-loss and take-profit, if applicable
        if (stopLoss > 0) {
            data.put("stopPrice", String.valueOf(stopLoss));
        }

        if (takeProfit > 0) {
            data.put("takeProfitPrice", String.valueOf(takeProfit));
        }

        // Make the API request
        String apiPath = "/api/v3/order";

        try {
            // Use the helper method to make the signed request asynchronously
            String response = binanceUsRequest(apiPath, data, getApiKey(), getApiSecret());

            // Parse the response and handle it (e.g., log, notify user, etc.)
            logger.info("Order created successfully: {}", response);
        } catch (Exception e) {
            logger.error("Failed to create order: {}", e.getMessage());
            throw new RuntimeException("Order creation failed: %s".formatted(e.getMessage()), e);
        }
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {

        Map<String, String> data = new HashMap<>();
        data.put("orderId", orderId);
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));

        String apiPath = "/api/v3/order";

        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use the helper method to make the signed request for order cancellation
                String response = binanceUsRequest(apiPath, data, getApiKey(), getApiSecret());

                // Log the cancellation success
                logger.info("Order canceled successfully: {}", response);

                // Return the response as part of the CompletableFuture
                return response;
            } catch (Exception e) {
                logger.error("Failed to cancel order: {}", e.getMessage());

                // Throw runtime exception wrapped in CompletableFuture
                throw new RuntimeException("Order cancellation failed: %s".formatted(e.getMessage()), e);
            }
        });
    }


    @Override
    public String getExchangeMessage() {
        return message;
    }
    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        Objects.requireNonNull(tradePair, "TradePair must not be null");
        Objects.requireNonNull(stopAt, "StopAt time must not be null");

        // Ensure the stopAt time is in the past
        if (stopAt.isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return CompletableFuture.supplyAsync(() -> {
            List<Trade> tradesBeforeStopTime = new ArrayList<>();

            String apiPath = "/api/v3/trades";
            boolean moreTrades = true;

            while (moreTrades) {
                try {
                    // Construct request for trades
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("%s%s?symbol=%s&limit=1000".formatted(API_URL, apiPath, tradePair.toString('-'))))
                            .GET()
                            .build();

                    // Send the request asynchronously and wait for the response
                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() != 200) {
                        throw new RuntimeException("Error fetching trades: %d - %s".formatted(response.statusCode(), response.body()));
                    }

                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode tradesResponse = objectMapper.readTree(response.body());

                    // If the response is empty or invalid
                    if (!tradesResponse.isArray() || tradesResponse.isEmpty()) {
                        logger.info("No more trades for symbol: {}. Stop time: {}", tradePair, stopAt);
                        moreTrades = false;
                        continue;
                    }

                    for (JsonNode tradeNode : tradesResponse) {
                        Instant tradeTime = Instant.ofEpochMilli(tradeNode.get("time").asLong());

                        // Stop if we've reached the stopAt time
                        if (tradeTime.isBefore(stopAt)) {
                            moreTrades = false;
                            break;
                        }

                        // Create a new Trade object and add it to the list
                        Trade trade = new Trade(
                                tradePair,
                                DefaultMoney.ofFiat(tradeNode.get("price").asText(), tradePair.getCounterCurrency()),
                                DefaultMoney.ofCrypto(tradeNode.get("qty").asText(), tradePair.getBaseCurrency()),
                                Side.getSide(tradeNode.get("isBuyerMaker").asBoolean() ? "SELL" : "BUY"),
                                tradeNode.get("id").asLong(),
                                tradeTime
                        );
                        tradesBeforeStopTime.add(trade);
                    }

                } catch (IOException | InterruptedException e) {
                    throw new RuntimeException("Failed to fetch trades", e);
                }

                // Optionally, implement a delay to avoid rate limiting
            }

            return tradesBeforeStopTime;
        });
    }


    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public List<Order> getPendingOrders() throws IOException, InterruptedException {
        // Construct the request URI for pending orders
        String requestUrl = String.format("%s/openOrders", API_URL);

        // Make the HTTP GET request
        requestBuilder
                .uri(URI.create(requestUrl));


        // Send the request and get the response
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        // Check if the response is successful
        if (response.statusCode() != 200) {
            logger.error("Failed to get pending orders: {}", response.body());
            throw new RuntimeException("Failed to get pending orders: %s".formatted(response.body()));
        }

        // Parse the JSON response to a list of Order objects
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Order> orders = objectMapper.readValue(response.body(), new TypeReference<>() {
        });

        // Log and return the list of pending orders
        logger.info("Pending orders: {}", orders);
        return orders;
    }


    @Override
    public CompletableFuture<String> getOrderBook(@NotNull TradePair tradePair) {


        String apiPath = "/api/v3/depth";
        requestBuilder.uri(URI.create(API_URL + apiPath));


        return client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())

                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Failed to get order book for trade pair {}: {}", tradePair, response.body());
                        throw new RuntimeException("Failed to get order book: %s".formatted(response.body()));
                    }

                    ObjectMapper objectMapper = new ObjectMapper();
                    OrderBook orderBook;
                    try {
                        orderBook = objectMapper.readValue(response.body(), OrderBook.class);


                        // Log and return the order book
                        logger.info("Order book for {}: {}", tradePair, orderBook);

                        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(orderBook);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                });
    }


    @Override
    public JsonParser getUserAccountDetails() {
        return null;
    }

    @Override
    public void connect(String text, String text1, String userIdText) {

    }

    @Override
    public void getPositionBook(TradePair tradePair) {

    }

    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException {
        // Construct the request URI for open orders
        String requestUrl = String.format("%s/orders?symbol=%s", API_URL, tradePair.toString('-'));

        // Make the HTTP GET request
        requestBuilder
                .uri(URI.create(requestUrl))
                .GET();

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        // Check if the response is successful
        if (response.statusCode() != 200) {
            logger.error("Failed to get open orders for trade pair {}: {}", tradePair, response.body());
            throw new RuntimeException("Failed to get open orders: %s".formatted(response.body()));
        }

        // Parse the JSON response
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        List<Order> orders = objectMapper.readValue(response.body(), new TypeReference<>() {
        });

        // Log and return the list of orders
        logger.info("Open orders for trade pair {}: {}", tradePair, orders);
        return orders;
    }


    @Override
    public ObservableList<Order> getOrders() {
        return null;
    }

    @Override
    public CompletableFuture<ArrayList<TradePair>> getTradePairs() {

        requestBuilder.uri(URI.create("%s/api/v3/exchangeInfo".formatted(API_URL)));
        return client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {

                    if (response.statusCode() != 200) {

                        Alert alert = new Alert(
                                Alert.AlertType.ERROR,

                                "Failed to retrieve trade pairs: %s".formatted(response.body())
                        );
                        alert.showAndWait();

                        throw new RuntimeException("Failed to get trade pairs: %s".formatted(response.body()));
                    }

                    ArrayList<TradePair> tradePairs = new ArrayList<>();
                    try {
                        ObjectMapper objectMapper = new ObjectMapper();
                        JsonNode jsonNode = objectMapper.readTree(response.body());
                        JsonNode symbols = jsonNode.get("symbols");

                        for (JsonNode symbol : symbols) {
                            TradePair tradePair = new TradePair(
                                    symbol.get("baseAsset").asText(),
                                    symbol.get("quoteAsset").asText()
                            );
                            tradePairs.add(tradePair);
                        }
                        return tradePairs;
                    } catch (IOException | SQLException | ClassNotFoundException e) {
                        throw new RuntimeException(e);
                    }
                });

    }


    public static class BinanceUsCandleDataSupplier extends CandleDataSupplier {
        protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first available data in 2015

        BinanceUsCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        }

        @Override
        public Set<Integer> getSupportedGranularity() {

            return new TreeSet<>(Set.of(60, 300, 900, 3600,

                    4 * 3600, 24 * 3600, 24 * 3600 * 7, 24 * 3600 * 7 * 4));  // 1m, 5m, 15m, 1h, 4h, 1d
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public Future<List<CandleData>> get() {
            if (endTime.get() == -1) {
                endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
            }

            String endDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    .format(LocalDateTime.ofEpochSecond(endTime.get(), 0, ZoneOffset.UTC));

            int startTime = Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA);
            String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    .format(LocalDateTime.ofEpochSecond(startTime, 0, ZoneOffset.UTC));

            if (startTime == EARLIEST_DATA) {
                // signal no more data available
                return CompletableFuture.completedFuture(Collections.emptyList());
            }


            // Build the Binance API request URL
            String requestUrl = "%s/products/%s/candles?granularity=%d&start=%s&end=%s".formatted(
                    API_URL,
                    tradePair.toString('-'),
                    secondsPerCandle,
                    startDateString,
                    endDateString
            );

            // Asynchronous HTTP request
            return client.sendAsync(
                    requestBuilder.uri(URI.create(requestUrl)).GET().build(),
                    HttpResponse.BodyHandlers.ofString()
            ).thenApply(response -> {
                if (response.statusCode() != 200) {
                    Alert alert = new Alert(
                            Alert.AlertType.ERROR,
                            "Failed to retrieve candle data: %s".formatted(response.body())
                    );
                    alert.showAndWait();
                    return Collections.emptyList();
                } else {

                    logger.info("Fetched candle data", response);

                    // Parse the JSON response
                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response.body());
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }

                    if (!res.isEmpty()) {
                        // Remove the current in-progress candle if it exceeds the end time
                        if (res.get(0).get(0).asInt() + secondsPerCandle > endTime.get()) {
                            ((ArrayNode) res).remove(0);
                        }
                        endTime.set(startTime);

                        // Parse the candle data from the response
                        List<CandleData> candleData = new ArrayList<>();
                        for (JsonNode candle : res) {
                            candleData.add(new CandleData(
                                    candle.get(3).asDouble(),  // open price
                                    candle.get(4).asDouble(),  // close price
                                    candle.get(2).asDouble(),  // high price
                                    candle.get(1).asDouble(),  // low price
                                    candle.get(0).asInt(),     // open time
                                    candle.get(5).asDouble()   // volume
                            ));
                        }
                        // Sort candles by open time
                        candleData.sort(Comparator.comparingInt(CandleData::getOpenTime));
                        return candleData;
                    } else {
                        // No candle data found
                        logger.info("No candle data found for trade pair: %s from %s".formatted(tradePair, startDateString));
                        return Collections.emptyList();
                    }
                }
            });
        }
    }

}
