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
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.java_websocket.drafts.Draft_6455;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.investpro.Coinbase.CoinbaseCandleDataSupplier.OBJECT_MAPPER;
import static org.investpro.Currency.db1;

public class Coinbase extends Exchange {

    static HttpClient client = HttpClient.newHttpClient();
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

    private static final Logger logger = LoggerFactory.getLogger(Coinbase.class);
    static String url = "https://api.coinbase.com/api/v3";
    //  Market Data Endpoint: wss://advanced-trade-ws.coinbase.com
    //  User Order Data Endpoint: wss://advanced-trade-ws-user.coinbase.com
    private String message;

    protected Coinbase(String apiKey, String apiSecret) throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        super(
                apiKey,
                apiSecret

                //  Arrays.asList("BTC-USD", "ETH-USD", "LTC-USD", "BCH-USD", "BSV-USD", "SOL-USD", "XRP-USD", "BTC-GBP", "ETH-GBP", "LTC-GBP", "BCH-GBP", "BSV-GBP", "SOL-GBP", "XRP-GBP", "BTC-EUR", "ETH-EUR", "LTC-EUR", "BCH-EUR", "BSV-EUR", "SOL-EUR", "XRP-EUR")
        );

        this.message = "Coinbase";
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("Access-Control", "cors");
        // Generate signature using API key and secret
        String signature = signer(apiKey, apiSecret);

        // Set headers for the initial request
        requestBuilder.header("CB-ACCESS-KEY", apiKey);

        requestBuilder.header("CB-ACCESS-SIGN", signature);
        requestBuilder.header("CB-ACCESS-TIMESTAMP", String.valueOf(System.currentTimeMillis() / 1000));

        // Set headers for WebSocket client
        getWebSocketClient().addHeader("CB-ACCESS-KEY", apiKey);
        getWebSocketClient().addHeader("CB-ACCESS-SIGN", signature);
        getWebSocketClient().addHeader("CB-ACCESS-TIMESTAMP", String.valueOf(System.currentTimeMillis() / 1000));


    }

    private @NotNull String signer(String apiKey, String apiSecret) throws UnsupportedEncodingException, NoSuchAlgorithmException, InvalidKeyException {
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String message = STR."\{timestamp}/GET//users/self/accounts";
        return hmacSHA256(apiSecret, message);
    }

    private @NotNull String hmacSHA256(@NotNull String apiSecret, @NotNull String message) throws NoSuchAlgorithmException, UnsupportedEncodingException, InvalidKeyException {
        Mac hmacSHA256 = Mac.getInstance("HmacSHA256");
        hmacSHA256.init(
                new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256")
        );
        byte[] rawHmac = hmacSHA256.doFinal(message.getBytes(StandardCharsets.UTF_8));
        StringBuilder hexString = new StringBuilder();
        for (byte b : rawHmac) {
            String hex = String.format("%02x", b);
            hexString.append(hex);
        }
        return hexString.toString();
    }


    @Override
    public Account getAccounts() throws IOException, InterruptedException {

        // Here you should implement the logic to fetch account details from Coinbase API
        requestBuilder.uri(URI.create(STR."\{url}/accounts"));
        requestBuilder.GET();

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            objectMapper.registerModule(new JavaTimeModule());
            return objectMapper.readValue(response.body(), Account.class);
        }
        throw new RuntimeException(STR."Failed to fetch account details: \{response.body()}");
    }

    @Override
    public Boolean isConnected() {
        // Check if the WebSocket client is connected
        return this.getWebSocketClient().connectionEstablished.get();

    }

    private ExchangeWebSocketClient getWebSocketClient() {

        this.webSocketClient = new ExchangeWebSocketClient(URI.create("wss://ws-feed.pro.coinbase.com"), new Draft_6455()) {
            @Override
            public CountDownLatch getInitializationLatch() {
                return Coinbase.this.webSocketClient.getInitializationLatch();
            }

            @Override
            public void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

                Coinbase.this.webSocketClient.send(OBJECT_MAPPER.createObjectNode().put("type", "subscribe")
                        .put("product_id", tradePair.toString('-')).toPrettyString());

            }

            @Override
            public void stopStreamLiveTrades(TradePair tradePair) {
                Coinbase.this.webSocketClient.send(OBJECT_MAPPER.createObjectNode().put("type", "unsubscribe")
                        .put("product_id", tradePair.toString('-')).toPrettyString());

            }

            @Override
            public boolean supportsStreamingTrades(TradePair tradePair) {

                return true;


            }

            @Override
            public void onError(Exception exception) {

                logger.info(
                        "WebSocket connection failed, retrying in 5 seconds. Error: {}",
                        exception.getMessage()

                );
            }

            @Override
            public void onOpen(ServerHandshake handshake) {
                logger.info("WebSocket connection established");
                Coinbase.this.webSocketClient.getInitializationLatch().countDown();

            }

            @Override
            public void onMessage(String message) {
                try {
                    ObjectMapper objectMapper = new ObjectMapper();
                    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
                    objectMapper.registerModule(new JavaTimeModule());
                    Trade liveTrade = objectMapper.readValue(message, Trade.class);
                    liveTradesConsumer.accept(liveTrade);
                } catch (JsonProcessingException e) {
                    logger.error("Failed to parse WebSocket message: {}", message, e);
                }

            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.info("WebSocket connection closed ({}, {})", code, reason);
                Coinbase.this.webSocketClient.getInitializationLatch().countDown();

            }

            @Override
            public boolean connectBlocking() {
                return false;
            }
        };
        return this.webSocketClient;


    }


    @Override
    public String getSymbol() {
        return tradePair.toString('-');
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {
        this.tradePair = tradePair;

        requestBuilder.uri(URI.create(STR."\{url}/api/v3/orders"));
        requestBuilder.method("POST", HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(new CreateOrderRequest(
                tradePair.toString('-'),
                side,
                orderType,
                price,
                size,
                timestamp,
                stopLoss,
                takeProfit
        ))));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            logger.info("Order created: {}", response.body());
            return;
        }
        throw new RuntimeException(STR."Failed to create order: \{response.body()}");
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(url + STR."/orders/cancel\{orderId}"));
        requestBuilder.method("DELETE", HttpRequest.BodyPublishers.noBody());
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            return CompletableFuture.completedFuture(orderId);
        }
        throw new RuntimeException(STR."Failed to cancel order: \{response.body()}");
    }

    @Override
    public String getExchangeMessage() {
        return message;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
    }


    public @NotNull TradePair getTradePair() {
        return tradePair;
    }

    public void setTradePair(@NotNull TradePair tradePair) {
        this.tradePair = tradePair;
    }

    /**
     * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
     * <p>
     * This method only needs to be implemented to support live syncing.
     */
    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);
        if (stopAt.isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();

        // It is not easy to fetch trades concurrently because we need to get the "cb-after" header after each request.
        CompletableFuture.runAsync(() -> {
            IntegerProperty afterCursor = new SimpleIntegerProperty(0);
            List<Trade> tradesBeforeStopTime = new ArrayList<>();

            // For Public Endpoints, our rate limit is 3 requests per second, up to 6 requests per second in
            // burst.
            // We will know if we get rate limited if we get a 429 response code.

            for (int i = 0; !futureResult.isDone(); i++) {
                String uriStr = "https://api.pro.coinbase.com/";
                uriStr += STR."products/\{tradePair.toString('-')}/trades";

                if (i != 0) {
                    uriStr += STR."?after=\{afterCursor.get()}";
                }

                try {
                    HttpResponse<String> response = client.send(
                            requestBuilder
                                    .uri(URI.create(uriStr))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofString());

                    logger.info(STR."response headers: \{response.headers()}");

                    if (response.statusCode() != 200) {
                        logger.error(STR."Failed to fetch trades: \{response.body()} for trade pair: \{tradePair}");

                        message = response.body();
                        futureResult.completeExceptionally(new RuntimeException(
                                STR."HTTP error response: \{response.statusCode()}"));
                        return;
                    }

                    message = response.body();
                    if (response.headers().firstValue("CB-AFTER").isEmpty()) {
                        futureResult.completeExceptionally(new RuntimeException(
                                STR."coinbase trades response did not contain header \"cb-after\": \{response}"));
                        return;
                    }

                    afterCursor.setValue(Integer.valueOf((response.headers().firstValue("CB-AFTER").get())));

                    JsonNode tradesResponse = OBJECT_MAPPER.readTree(response.body());

                    if (!tradesResponse.isArray()) {
                        futureResult.completeExceptionally(new RuntimeException(
                                "coinbase trades response was not an array!"));
                    }
                    if (tradesResponse.isEmpty()) {
                        futureResult.completeExceptionally(new IllegalArgumentException("tradesResponse was empty"));
                    } else {
                        for (int j = 0; j < tradesResponse.size(); j++) {
                            JsonNode trade = tradesResponse.get(j);
                            Instant time = Instant.from(ISO_INSTANT.parse(trade.get("time").asText()));
                            if (time.compareTo(stopAt) <= 0) {
                                futureResult.complete(tradesBeforeStopTime);
                                break;
                            } else {
                                tradesBeforeStopTime.add(new Trade(tradePair,
                                        DefaultMoney.ofFiat(trade.get("price").asText(), tradePair.getCounterCurrency()),
                                        DefaultMoney.ofCrypto(trade.get("size").asText(), tradePair.getBaseCurrency()),
                                        Side.getSide(trade.get("side").asText()), trade.get("trade_id").asLong(), time));
                            }
                        }
                    }
                } catch (IOException | InterruptedException ex) {
                    logger.error("ex: ", ex);
                    message = ex.getMessage();
                    futureResult.completeExceptionally(ex);
                    return;
                }
            }
        });

        return futureResult;
    }

    /**
     * This method fetches candle data for an in-progress candle to support live syncing.
     */
    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStart, long secondsIntoCurrentCandle, int secondsPerCandle) {

        // Format the start date for the request
        String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(
                LocalDateTime.ofInstant(currentCandleStart, ZoneOffset.UTC));

        // Determine the ideal granularity and adjust to the closest supported granularity
        long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
        int actualGranularity = getCandleDataSupplier(secondsPerCandle, tradePair)
                .getSupportedGranularity().stream()
                .min(Comparator.comparingInt(granularity -> (int) Math.abs(granularity - idealGranularity)))
                .orElseThrow(() -> new NoSuchElementException("No supported granularity found"));

        // Build and send the HTTP request asynchronously
        String requestUrl = String.format(
                "%s/products/%s/candles?granularity=%d&start=%s", url
                , tradePair.toString('-'), actualGranularity, startDateString);

        return client.sendAsync(
                        requestBuilder.uri(URI.create(requestUrl)).GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(this::parseCandleData)
                .thenApply(optionalData -> optionalData.or(Optional::empty));
    }

    /**
     * Parses the response string into an Optional containing InProgressCandleData.
     *
     * @param response The JSON response string from the API.
     * @return An Optional containing InProgressCandleData if data is available, otherwise empty.
     */
    private Optional<InProgressCandleData> parseCandleData(String response) {
        logger.info("Coinbase response: {}", response);

        JsonNode responseNode;
        try {
            responseNode = OBJECT_MAPPER.readTree(response);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException("Error parsing JSON response", ex);
        }

        if (responseNode.isEmpty()) {
            return Optional.empty();
        }

        double openPrice = -1;
        double highSoFar = Double.NEGATIVE_INFINITY;
        double lowSoFar = Double.POSITIVE_INFINITY;
        double volumeSoFar = 0;
        double lastTradePrice = -1;
        int currentTill = -1;
        boolean foundFirstCandle = false;

        Instant currentCandleStart = Instant.ofEpochSecond(responseNode.get(0).get(0).asInt());
        for (JsonNode candle : responseNode) {
            int candleStartTime = candle.get(0).asInt();
            int secondsPerCandle = (int) (0.000001 * candle.get(6).asDouble()); // Adjust for
            int candleEndTime = candleStartTime + secondsPerCandle;

            // Skip candles outside the current candle's timeframe
            if (candleStartTime < currentCandleStart.getEpochSecond() || candleStartTime >= candleEndTime) {
                continue;
            }

            if (!foundFirstCandle) {
                currentTill = candleStartTime;
                lastTradePrice = candle.get(4).asDouble();
                foundFirstCandle = true;
            }

            openPrice = candle.get(3).asDouble();
            highSoFar = Math.max(highSoFar, candle.get(2).asDouble());
            lowSoFar = Math.min(lowSoFar, candle.get(1).asDouble());
            volumeSoFar += candle.get(5).asDouble();
        }

        int openTimeInSeconds = (int) (currentCandleStart.toEpochMilli() / 1000L);

        return Optional.of(new InProgressCandleData(openTimeInSeconds, openPrice, highSoFar, lowSoFar, currentTill, lastTradePrice, volumeSoFar));
    }

    @Override
    public List<Order> getPendingOrders() throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(STR."\{url}/orders"));
        HttpResponse<String> res = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException(String.format("Failed to get pending orders: %s", res.body()));
        }

        return OBJECT_MAPPER.readValue(res.body(), new TypeReference<>() {
        });
    }

    @Override
    public CompletableFuture<String> getOrderBook(@NotNull TradePair tradePair) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(String.format(STR."\{url}/products", tradePair.toString('-'))));
        HttpResponse<String> res = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException(String.format("Failed to get order book for %s: %s", tradePair, res.body()));
        }

        OrderBook orderBook = OBJECT_MAPPER.readValue(res.body(), OrderBook.class);
        logger.info(STR."coinbase order book: \{orderBook}");
        return CompletableFuture.completedFuture(res.body());


    }

    @Override
    public JsonParser getUserAccountDetails() {
        return null;
    }

    @Override
    public void connect(String text, String text1, String userIdText) throws IOException, InterruptedException {
        if (text == null || text1 == null || userIdText == null) {
            throw new IllegalArgumentException("All parameters must be non-null");
        }
        requestBuilder.uri(URI.create(String.format(STR."\{url}/accounts/%s")));
        HttpResponse<String> res = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException(String.format("Failed to get user account details for %s: %s", userIdText, res.body()));
        }

        Account userAccount = OBJECT_MAPPER.readValue(res.body(), Account.class);
        logger.info(STR."coinbase user account: \{userAccount}");


    }

    @Override
    public void getPositionBook(@NotNull TradePair tradePair) throws IOException, InterruptedException {

        requestBuilder.uri(
                URI.create(String.format(STR."\{url}/products/%s/order_book", tradePair.toString('-')))
        );
        HttpResponse<String> res = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException(String.format("Failed to get position book for %s: %s", tradePair, res.body()));
        }

        OrderBook positionBook = OBJECT_MAPPER.readValue(res.body(), OrderBook.class);
        logger.info(STR."coinbase position book: \{positionBook}");

    }

    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(String.format("https://api.pro.coinbase.com/orders?product_id=%s", tradePair.toString('-'))));
        HttpResponse<String> res = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            throw new RuntimeException(String.format("Failed to get open orders for %s: %s", tradePair, res.body()));
        }

        List<Order> orders = OBJECT_MAPPER.readValue(res.body(), new TypeReference<>() {
        });
        logger.info(STR."coinbase open orders: \{orders}");
        return orders;


    }

    @Override
    public ObservableList<Order> getOrders() throws IOException, InterruptedException {

        //Get all orders from Coinbase API

        ObservableList<Order> orders = FXCollections.observableArrayList();

        requestBuilder.uri(URI.create(STR."\{url}/orders"));
        HttpResponse<String> res = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if (res.statusCode() != 200) {
            logger.error("Failed to get all orders: {}", res.body());
            return orders;
        }

        List<Order> allOrders = OBJECT_MAPPER.readValue(res.body(), new TypeReference<>() {
        });
        logger.info(STR."coinbase all orders: \{allOrders}");
        orders.addAll(allOrders);
        return orders;
    }

    @Override
    public List<TradePair> getTradePairs() {

        requestBuilder.uri(URI.create("https://api.pro.coinbase.com/products"));

        ArrayList<TradePair> tradePairs = new ArrayList<>();
        try {

            HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();


            JsonNode res = OBJECT_MAPPER.readTree(response.body());
            logger.info(STR."coinbase response: \{res}");
            @NotNull ArrayList<Currency> cryptoCurrencyArrayList = new ArrayList<>();
            //coinbase response: [{"id":"DOGE-BTC","base_currency":"DOGE","quote_currency":"BTC","quote_increment":"0.00000001","base_increment":"0.1","display_name":"DOGE-BTC","min_market_funds":"0.000016","margin_enabled":false,"post_only":false,"limit_only":false,"cancel_only":false,"status":"online","status_message":"","trading_disabled":false,"fx_stablecoin":false,"max_slippage_percentage":"0.03000000","auction_mode":false,
            for (JsonNode rate : res) {
                CryptoCurrency baseCurrency, counterCurrency;


                String fullDisplayName = rate.get("base_currency").asText();


                String shortDisplayName = rate.get("base_currency").asText();
                String code = rate.get("base_currency").asText();
                int fractionalDigits = 8;
                String symbol = rate.get("base_currency").asText();
                baseCurrency = new CryptoCurrency(fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, symbol);
                String fullDisplayName2 = rate.get("quote_currency").asText();
                String shortDisplayName2 = rate.get("quote_currency").asText();
                String code2 = rate.get("quote_currency").asText();
                int fractionalDigits2 = 8;
                String symbol2 = rate.get("quote_currency").asText();

                counterCurrency = new CryptoCurrency(
                        fullDisplayName2, shortDisplayName2, code2, fractionalDigits2, symbol2

                        , symbol);
                cryptoCurrencyArrayList.add(baseCurrency);
                cryptoCurrencyArrayList.add(counterCurrency);


                TradePair tp = new TradePair(
                        baseCurrency, counterCurrency
                );
                tradePairs.add(tp);
                logger.info(STR."coinbase trade pair: \{tp}");
                //  TradePair tp = TradePair.of(baseCurrency, counterCurrency);


            }


        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return tradePairs;

    }

    public static class CoinbaseCandleDataSupplier extends CandleDataSupplier {
        protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first tra

        CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));

        }

        @Override
        public Set<Integer> getSupportedGranularity() {
            // https://docs.pro.coinbase.com/#get-historic-rates
            return new TreeSet<>(Set.of(60, 300, 900, 3600, 21600, 86400));

        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
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
                // signal more data is false
                return CompletableFuture.completedFuture(Collections.emptyList());
            }


            return client.sendAsync(
                            requestBuilder
                                    .uri(URI.create(
                                            STR."https://api.pro.coinbase.com/products/\{tradePair.toString('-')}/candles?granularity=\{secondsPerCandle}&start=\{startDateString}&end=\{endDateString}"))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {

                        if (response.statusCode() != 200) {

                            logger.error(STR."Failed to fetch candle data: \{response.body()} for trade pair: \{tradePair}");
                            throw new RuntimeException(STR."HTTP error response: \{response.statusCode()}");
                        }


                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response.body());
                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (!res.isEmpty()) {
                            // Remove the current in-progress candle
                            if (res.get(0).get(0).asInt() + secondsPerCandle > endTime.get()) {
                                ((ArrayNode) res).remove(0);
                            }
                            endTime.set(startTime);

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
                            candleData.sort(Comparator.comparingInt(CandleData::getOpenTime));
                            return candleData;
                        } else {

                            logger.info(
                                    STR."No candle data found for trade pair: \{tradePair} from \{startDateString} to display"
                            );
                            return Collections.emptyList();
                        }
                    });
        }
    }
}
