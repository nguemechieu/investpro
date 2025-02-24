package org.investpro.exchanges;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.dockerjava.api.async.ResultCallback;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Alert;
import lombok.Getter;
import lombok.Setter;
import org.investpro.Currency;
import org.investpro.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;

@Getter
@Setter
public class Oanda extends Exchange {

    private static final int MAX_RETRIES = 5;
    private static final int MAX_CANDLES_PER_REQUEST = 1000;

    static HttpClient client = HttpClient.newHttpClient();
    private static final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static final Logger logger = LoggerFactory.getLogger(Oanda.class);
    public static final String API_URL = "https://api-fxtrade.oanda.com/v3";  // OANDA API URL
    private String account_id;
    private ResultCallback.Adapter<PriceData> livePriceUpdates = new ResultCallback.Adapter<>();
    private ResultCallback.Adapter<Trade> liveTradeUpdates = new ResultCallback.Adapter<>();
    private @NotNull Map<String, String> headers = new HashMap<>();
    private TradePair tradePair;
    private Currency baseCurr;
    private Currency counterCurr;



    public Oanda(String accountId, String apiSecret) {
        super(accountId, apiSecret); // OANDA uses only an API key for authentication, no secret required
        this.account_id = accountId;

        logger.info("SECRET RECEIVED: {}", apiSecret);
        requestBuilder.header("Authorization", "Bearer " + apiSecret);
        requestBuilder.setHeader("Accept", "application/json");
        requestBuilder.setHeader("Content-Type", "application/json");
        headers.put("Accept", "application/json");
        headers.put("Content-Type", "application/json");
        headers.put("Authorization", "Bearer " + apiSecret);

    }

    @Override
    public List<Fee> getTradingFee() throws IOException, InterruptedException {
        // Build the URI for the request
        requestBuilder.uri(URI.create(
                "%s/accounts/%s/trades/fee".formatted(API_URL, account_id)
        ));

        // Send the GET request and capture the response
        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        // Handle non-200 status codes by showing an error message and throwing a RuntimeException
        if (response.statusCode() != 200) {

            throw new RuntimeException("Error fetching trading fee: %d".formatted(response.statusCode()));
        }

        Fee fee = OBJECT_MAPPER.readValue(response.body(), Fee.class);
        // Ensure that the fee is not null before returning it
        if (fee == null) {
            throw new RuntimeException("Failed to parse trading fee from response.\n" + response.body());
        }
        return Collections.singletonList(fee);

    }
    private static final long INITIAL_DELAY_MS = 500; // 500ms initial delay



    @Override
    public List<Account> getAccounts() throws IOException, InterruptedException {
        // Build the URI for the request
        requestBuilder.uri(URI.create("%s/accounts".formatted(API_URL)));

        // Send the GET request and capture the response
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            // Display error message and throw an exception
            throw new RuntimeException("Error fetching accounts: %s".formatted(response.body()));
        }

        // Parse the response body as a JSON object
        JsonNode rootNode = OBJECT_MAPPER.readTree(response.body());

        // Extract the "accounts" array from the JSON object
        JsonNode accountsNode = rootNode.get("accounts");
        if (accountsNode == null || !accountsNode.isArray()) {
            throw new RuntimeException("Invalid JSON response: 'accounts' field is missing or not an array");
        }

        // Convert the accounts array into a list of Account objects
        List<Account> accounts = new ArrayList<>();
        for (JsonNode accountNode : accountsNode) {
            Account account = OBJECT_MAPPER.treeToValue(accountNode, Account.class); // Corrected method call
            accounts.add(account);
        }
        logger.info("Found {} accounts", accounts);
        return accounts;
    }




    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException{

        requestBuilder.uri(URI.create(
                "%s/accounts/%s/orders".formatted(API_URL, account_id) // OANDA requires account ID in the request path
        ));

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                tradePair.toString('_'),
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
        if (response.statusCode() != 201) { // OANDA uses 201 for successful order creation
            throw new RuntimeException("Error creating order: %s".formatted(response.body()));
        }
        logger.info("Order created: {}", response.body());
    }

    @Override
    public void cancelOrder(String orderId) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(
                API_URL + "/accounts/%s/orders/%s".formatted(account_id, orderId) // OANDA requires account ID and order ID
        ));
            requestBuilder.method("DELETE", HttpRequest.BodyPublishers.noBody());

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            new Messages(Alert.AlertType.ERROR, "Error cancelling order: %d".formatted(response.statusCode()));
            throw new RuntimeException("Error cancelling order: %d".formatted(response.statusCode()));
        }
        logger.info("Order cancelled: {}", orderId);

    }
    private static final long RATE_LIMIT_DELAY_MS = 1000; // 1 second between requests
    /**
     * Oanda doesn't support wss (websocket )
     */

//    🎯 Recommended Default Number of Candles
//    Use Case	Recommended Candles
//    Scalping (Short-Term Trading, 1-5 min charts)	300-500
//    Intraday Trading (5m - 1h charts)	500-1000
//    Swing Trading (Daily/Weekly Charts)	1000-2000
//    Long-Term Historical Analysis (Months/Years of Data)	2000-5000 (Chunked Loading Required)
//

    public static int numCandles = 1000;
    private final Map<String, OrderBook> orderBookCache = new ConcurrentHashMap<>();
    private final Semaphore rateLimiter = new Semaphore(1); // Controls request flow

    public static @NotNull String granularityToString(int actualGranularity) {

        if (actualGranularity < 60) {
            return "s" + actualGranularity;  // Seconds
        } else if (actualGranularity < 3600) {
            return "M" + (actualGranularity / 60);  // Minutes
        } else if (actualGranularity < 86400) {
            return "H" + (actualGranularity / 3600);  // Hours
        } else if (actualGranularity < 604800) {
            return "D";  // Days
        } else if (actualGranularity < 2592000) {

            return "W";  // Weeks (W1, W2, etc.)
        } else {
            return "Mo";
        }
    }

    @Override
    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        Objects.requireNonNull(tradePair);

        CompletableFuture<List<OrderBook>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            String cacheKey = tradePair.toString('_');

            // Check Cache First to Reduce Requests
            if (orderBookCache.containsKey(cacheKey)) {
                logger.info("Using cached order book for {}", tradePair);
                futureResult.complete(List.of(orderBookCache.get(cacheKey)));
                return;
            }

            String uriStr = API_URL + "/accounts/" + account_id + "/pricing?instruments=" + cacheKey;
            requestBuilder.uri(URI.create(uriStr));

            // Ensure Rate-Limiting
            try {
                rateLimiter.acquire();
                Thread.sleep(RATE_LIMIT_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            try {
                fetchWithRetries(tradePair, 0, futureResult);
            } finally {
                rateLimiter.release(); // Allow next request
            }
        });

        return futureResult;
    }

    private void fetchWithRetries(TradePair tradePair, int retryCount, CompletableFuture<List<OrderBook>> futureResult) {
        try {
            HttpResponse<String> response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                // 429: Too Many Requests (Rate Limited) → Apply Exponential Backoff
                if (retryCount < MAX_RETRIES) {
                    long backoffTime = INITIAL_DELAY_MS * (1L << retryCount);
                    logger.warn("Rate-limited. Retrying in {} ms...", backoffTime);
                    Thread.sleep(backoffTime);
                    fetchWithRetries(tradePair, retryCount + 1, futureResult);
                } else {
                    logger.error("Max retries reached for fetching order book: {}", response.body());
                    futureResult.completeExceptionally(new RuntimeException("Rate limit exceeded"));
                }
                return;
            }

            if (response.statusCode() != 200) {
                logger.error("Error fetching order book: {}", response.body());
                futureResult.completeExceptionally(new RuntimeException("Failed to fetch order book"));
                return;
            }

            JsonNode orderBookResponse = OBJECT_MAPPER.readTree(response.body());
            List<OrderBook> orderBooks = new ArrayList<>();
            JsonNode pricesNode = orderBookResponse.get("prices");

            if (pricesNode == null || !pricesNode.isArray()) {
                logger.warn("Invalid order book data received from OANDA");
                futureResult.complete(Collections.emptyList());
                return;
            }

            for (JsonNode priceEntry : pricesNode) {
                Instant time = Instant.parse(priceEntry.get("time").asText());
                double bidPrice = priceEntry.get("bids").get(0).get("price").asDouble();
                double askPrice = priceEntry.get("asks").get(0).get("price").asDouble();
                double bidLiquidity = priceEntry.get("bids").get(0).get("liquidity").asDouble();
                double askLiquidity = priceEntry.get("asks").get(0).get("liquidity").asDouble();

                OrderBook orderBookEntry = new OrderBook(tradePair, bidPrice, askPrice, bidLiquidity, askLiquidity, time);
                orderBooks.add(orderBookEntry);

                // Cache the latest order book data
                orderBookCache.put(tradePair.toString('_'), orderBookEntry);
            }

            futureResult.complete(orderBooks);

        } catch (IOException | InterruptedException e) {
            logger.error("Error fetching order book from OANDA", e);
            futureResult.completeExceptionally(e);
        }
    }

    @Override
    public String getExchangeMessage() {
        return message;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        setTradePair(tradePair);
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair); // Custom class to handle OANDA candlestick data
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt, int secondsPerCandle, Consumer<List<Trade>> tradeConsumer) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);
        Objects.requireNonNull(tradeConsumer);

        return CompletableFuture.supplyAsync(() -> {
            String uriStr = API_URL + "/accounts/" + account_id + "/pricing?instruments=" + tradePair.toString('_');
            requestBuilder.uri(URI.create(uriStr));

            int retryCount = 0;
            int maxRetries = 5;
            long delayMillis = 500; // Initial delay (0.5 sec)

            List<Trade> trades = new ArrayList<>();
            List<CandleData> completedCandles = new ArrayList<>();

            InProgressCandle currentCandle = null;

            while (retryCount < maxRetries) {
                try {
                    HttpResponse<String> response = client.send(
                            requestBuilder.build(),
                            HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 429) { // Rate limit hit
                        logger.warn("Rate limit hit. Retrying in {} ms", delayMillis);
                        Thread.sleep(delayMillis);
                        retryCount++;
                        delayMillis *= 2; // Exponential backoff
                        continue;
                    }

                    JsonNode tradesResponse = OBJECT_MAPPER.readValue(response.body(), JsonNode.class);

                    for (JsonNode trade : tradesResponse.get("prices")) {
                        logger.info("Found trade {}", trade);
                        Instant tradeTime = Instant.parse(trade.get("time").asText());
                        double tradePrice = trade.get("price").asDouble();
                        double tradeSize = trade.get("liquidity").asDouble();
                        Side side = Side.getSide(trade.get("side").asText());

                        OrderBook prices = new OrderBook();
                        if (side == Side.BUY) {
                            prices.getAskEntries().add(new OrderBookEntry(tradePrice, tradeSize));
                        } else {
                            prices.getBidEntries().add(new OrderBookEntry(tradePrice, tradeSize));
                        }

                        Trade tr = new Trade(tradePair, prices.getAskEntries().stream().findFirst().get().getPrice(), tradeSize, side, tradeTime);
                        trades.add(tr);

                        // ---- CANDLE MANAGEMENT ----

                        if (currentCandle == null || tradeTime.getEpochSecond() > currentCandle.getOpenTime() + secondsPerCandle) {
                            // If a new candle is required, close the previous one
                            if (currentCandle != null) {
                                currentCandle.closeCandle(tradeTime.getEpochSecond(), currentCandle.getClosePriceSoFar());
                                completedCandles.add(currentCandle.snapshot());
                            }

                            // Start a new candle
                            currentCandle = new InProgressCandle();
                            currentCandle.setOpenTime(tradeTime.getEpochSecond());
                            currentCandle.setOpenPrice(tradePrice);
                            currentCandle.setHighPriceSoFar(tradePrice);
                            currentCandle.setLowPriceSoFar(tradePrice);
                            currentCandle.setVolumeSoFar(tradeSize);
                        } else {
                            // Update ongoing candle
                            currentCandle.setHighPriceSoFar(Math.max(currentCandle.getHighPriceSoFar(), tradePrice));
                            currentCandle.setLowPriceSoFar(Math.min(currentCandle.getLowPriceSoFar(), tradePrice));
                            currentCandle.setVolumeSoFar(currentCandle.getVolumeSoFar() + tradeSize);
                            currentCandle.setClosePriceSoFar(tradePrice);
                        }
                    }

                    // Process trades using the provided consumer
                    tradeConsumer.accept(trades);
                    return trades;

                } catch (IOException | InterruptedException e) {
                    logger.error("Error fetching trades", e);
                    return trades;
                }
            }

            logger.error("Max retries exceeded due to rate limiting.");
            return trades;
        });
    }



    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle,
            int secondsPerCandle) {

        String startDateString = DateTimeFormatter.ISO_INSTANT.format(currentCandleStartedAt);
        List<InProgressCandleData> allCandles = new ArrayList<>();

        return fetchCandlesPaginated(tradePair, startDateString, secondsPerCandle, allCandles)
                .thenApply(candles -> candles.isEmpty() ? Optional.empty() : Optional.of(candles));
    }

    /**
     * **Fetch Candle Data Efficiently Using Pagination**
     */
    private @NotNull CompletableFuture<List<InProgressCandleData>> fetchCandlesPaginated(
            @NotNull TradePair tradePair, String startDateString, int secondsPerCandle,
            List<InProgressCandleData> allCandles) {

        String uri = "%s/instruments/%s/candles?granularity=%s&to=%s&count=%d"
                .formatted(API_URL, tradePair.toString('_'), granularityToString(secondsPerCandle), startDateString, MAX_CANDLES_PER_REQUEST);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .header("Authorization", "Bearer " + apiSecret)
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenCompose(response -> {
                    try {
                        JsonNode res = new ObjectMapper().readValue(response, JsonNode.class);

                        if (!res.has("candles")) {
                            logger.error(res.asText());
                            return
                                    CompletableFuture.completedFuture(
                                            new ArrayList<>()
                                    );
                        }

                        JsonNode candles = res.get("candles");

                        if (candles == null || !candles.isArray() || candles.isEmpty()) {
                            logger.warn("No candles data found");
                            return CompletableFuture.completedFuture(new ArrayList<>());
                        }

                        for (JsonNode candleNode : candles) {
                            parseCandleData(candleNode).ifPresent(allCandles::add);
                        }

                        if (candles.size() == MAX_CANDLES_PER_REQUEST) {
                            String lastCandleTime = candles.get(candles.size() - 1).get("time").asText();
                            return fetchCandlesPaginated(tradePair, lastCandleTime, secondsPerCandle, allCandles);
                        } else {
                            return CompletableFuture.completedFuture(allCandles);
                        }
                    } catch (JsonProcessingException ex) {
                        logger.error("Failed to parse OANDA response", ex);
                        return CompletableFuture.completedFuture(new ArrayList<>());
                    }
                });
    }

    private Optional<InProgressCandleData> parseCandleData(JsonNode candleNode) {
        if (candleNode == null) return Optional.empty();

        long openTime = Instant.parse(candleNode.get("time").asText()).getEpochSecond();
        return Optional.of(new InProgressCandleData(
                openTime,
                candleNode.get("mid").get("o").asDouble(),
                candleNode.get("mid").get("h").asDouble(),
                candleNode.get("mid").get("l").asDouble()
                , Instant.now().toEpochMilli(),
                candleNode.get("mid").get("c").asDouble(),

                candleNode.get("volume").asLong()
        ));
    }

    @Override
    public List<Order> getPendingOrders() throws IOException, InterruptedException, ExecutionException {

        requestBuilder.uri(URI.create(API_URL + "/accounts/%s/pendingOrders".formatted(account_id)));
        HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();


        if (response.statusCode() != 200) {


            throw new RuntimeException("Error fetching pending orders: %s".formatted(response.body()));
        }

        List<Order> orders = new ArrayList<>();

        JsonNode ordersJson = OBJECT_MAPPER.readTree(response.body());
        if (!ordersJson.isArray() || ordersJson.isEmpty()) {

            for (JsonNode order : ordersJson) {

                Order orderObj = new Order();
                if (order.has("lastTransactionID")) {
                    orderObj.setLastTransactionID(order.get("lastTransactionID").asText());

                }
                if (order.has("orders")) {
                    orderObj = OBJECT_MAPPER.convertValue(order.get("orders"), Order.class);
                    orders.add(orderObj);
                }

            }
        }
        return orders;


    }

    @Override
    public List<Position> getPositions() throws IOException, InterruptedException {
        // OANDA position book can be fetched if needed

        requestBuilder.uri(URI.create("%s/accounts/%s/positions".formatted(API_URL, account_id)));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
       if ( response.statusCode()!=200)
       {
           throw new RuntimeException("Error fetching positions: %d".formatted(response.statusCode()));
       }

        logger.info("OANDA response2: {}", response.body());

        List<Position> positions = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode res = mapper.readTree(response.body());

        for (JsonNode position : res.get("positions")) {

            Position p = mapper.readValue(position.toString(), Position.class);
            positions.add(p);
        }
        return positions;
    }

    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        requestBuilder.uri(URI.create(API_URL + "/accounts/%s/orders".formatted(account_id)));

        HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("OANDA response: %s".formatted(response.body()));
        ObjectMapper objectMapper = new ObjectMapper();

        JsonNode res = objectMapper.readTree(response.body());
        List<Order> orders = new ArrayList<>();
        for (JsonNode order : res.get("orders")) {
            Order o = objectMapper.readValue(order.toString(), Order.class);
            orders.add(o);
        }
        return orders;
    }

    @Override
    public List<Order> getOrders() throws IOException, InterruptedException {

        requestBuilder.uri(
                URI.create(API_URL + "/accounts/%s/orders".formatted(account_id))
        );

        // Send the request
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        // Handle non-200 responses
        if (response.statusCode() != 200) {

            throw new RuntimeException(String.format("ORDER HTTP error response: %s", response));
        }

        // Deserialize the JSON response into a list of orders
        JsonNode or = OBJECT_MAPPER.readValue(response.body(), JsonNode.class);
        List<Order> orders = new ArrayList<>();
        if (or.has("orders")) {
            for (JsonNode order : or.get("orders")) {
                Order orderObj = OBJECT_MAPPER.readValue(order.toString(), Order.class);

                orderObj.setLastTransactionID(or.get("lastTransactionID").asText());
                orders.add(orderObj);

            }
        }

        return orders;

        // Convert the List<Order> to an ObservableList<Order>

        // Return the ObservableList

    }


    @Override
    public List<TradePair> getTradePairs() throws Exception {
        String url = String.format("%s/accounts/%s/instruments", API_URL, account_id);
        requestBuilder.uri(URI.create(url));

        List<TradePair> tradePairs = new ArrayList<>();

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            new Messages(Alert.AlertType.ERROR, String.format("%d\n\n%s", response.statusCode(), response.body()));
            return tradePairs; // Return an empty list if the request fails
        }

        JsonNode res = new ObjectMapper().readTree(response.body());
        logger.info("OANDA response: {}", res);

        JsonNode instruments = res.get("instruments");
        if (instruments == null) {
            logger.warn("No instruments found in OANDA response.");
            return tradePairs;
        }

        for (JsonNode instrument : instruments) {
            String[] currencyPair = instrument.get("name").asText().split("_");
            if (currencyPair.length != 2) {
                logger.warn("Invalid currency pair format: {}", instrument.get("name").asText());
                continue;
            }

            String baseCurrencyCode = currencyPair[0];
            String counterCurrencyCode = currencyPair[1];

            // Fetch or create base currency
            Currency baseCurrency;

            baseCurrency = new Currency(CurrencyType.FIAT, baseCurrencyCode, baseCurrencyCode, baseCurrencyCode, 4, baseCurrencyCode, baseCurrencyCode) {
                @Override
                public int compareTo(@NotNull java.util.Currency o) {
                    return 0;
                }
            };
            Currency.save(baseCurrency);


            // Fetch or create counter currency
            Currency counterCurrency;// = Currency.of(counterCurrencyCode);

            counterCurrency = new Currency(CurrencyType.FIAT, counterCurrencyCode, counterCurrencyCode, counterCurrencyCode, 4, counterCurrencyCode, counterCurrencyCode) {
                @Override
                public int compareTo(@NotNull java.util.Currency o) {
                    return 0;
                }
            };

            Currency.save(counterCurrency);


            // Ensure currencies are different before creating a trade pair
            TradePair tradePair = new TradePair(baseCurrency, counterCurrency);
            tradePair.getBaseCurrency().setCurrencyType(CurrencyType.FIAT.name());
            tradePair.getCounterCurrency().setCurrencyType(CurrencyType.FIAT.name());

            tradePairs.add(tradePair);
            logger.info("✅ OANDA trade pair created: {}", tradePair);
        }


        return tradePairs;
    }

    @Override
    public void streamLiveTrades(TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

        // Implement WebSocket connection if needed for live trade streams
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
    public void cancelAllOrders() {



    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return true;
    }

    @Override
    public ArrayList<Deposit> Deposit() {
        return null;
    }

    @Override
    public ArrayList<Withdrawal> Withdraw() {
        return null;
    }

    @Override
    public List<Trade> getLiveTrades(List<TradePair> tradePairs) {
        return List.of();
    }


    @Override

    public CustomWebSocketClient getWebsocketClient() {

        CustomWebSocketClient re = new CustomWebSocketClient(API_URL + "/accounts/" + account_id + "/pricing?instruments=" + tradePair.toString('_')) {

            @Override
            public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                return super.onPing(webSocket, message);
            }

            @Override
            public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                return super.onPong(webSocket, message);
            }

            @Override
            public void onMessage(String message) {
                logger.info("Received WebSocket Message: {}", message);
                try {
                    JsonNode jsonNode = OBJECT_MAPPER.readTree(message);

                    // Process price updates
                    if (jsonNode.has("prices")) {
                        for (JsonNode priceNode : jsonNode.get("prices")) {
                            String instrument = priceNode.get("instrument").asText();
                            double bid = priceNode.get("bids").get(0).get("price").asDouble();
                            double ask = priceNode.get("asks").get(0).get("price").asDouble();
                            Instant time = Instant.parse(priceNode.get("time").asText());

                            // Log price update
                            logger.info("Live Update - {} | Bid: {} | Ask: {}", instrument, bid, ask);

                            // Notify any listeners in the app
                            PriceData priceData = new PriceData(instrument, bid, ask, time);
                            livePriceUpdates.onNext(priceData);
                        }
                    }

                    // Process trade updates
                    if (jsonNode.has("trades")) {
                        for (JsonNode tradeNode : jsonNode.get("trades")) {

                            String sym = tradeNode.get("instrument").asText();
                            TradePair tradePair = new TradePair(sym.split("_")[0],
                                    sym.split("_")[1]
                            );
                            double price = tradeNode.get("price").asDouble();
                            double size = tradeNode.get("units").asDouble();
                            Instant timestamp = Instant.parse(tradeNode.get("time").asText());
                            Side side = tradeNode.get("side").asText().equalsIgnoreCase("buy") ? Side.BUY : Side.SELL;

                            OrderBook prices = new OrderBook();
                            if (side == Side.BUY) {
                                prices.getAskEntries().add(new OrderBookEntry(price, size));
                            } else {
                                prices.getBidEntries().add(new OrderBookEntry(price, size));
                            }

                            // Notify any listeners in the app
                            Trade trade = new Trade(tradePair, prices.getAskEntries().stream().findFirst().get().getPrice(), size, side, timestamp);
                            logger.info("Live Trade: {}", trade);

                            liveTradeUpdates.onNext(trade);
                        }
                    }

                } catch (IOException e) {
                    logger.error("Error processing WebSocket message: {}", message, e);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            @Override
            public void onOpen() {
                logger.info("Connected to OANDA WebSocket for live pricing.");
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                logger.warn("WebSocket closed: {} - {}", code, reason);
            }

            @Override
            public void onError(Exception ex) {
                logger.error("WebSocket error: ", ex);
            }

            @Override
            public boolean supportsStreamingTrades(TradePair tradePair) {
                return true;
            }
            @Override
            public void streamLiveTrades(TradePair tradePair, CandleStickChart.UpdateInProgressCandleTask updateInProgressCandleTask) {


            }
        };
        re.connect(headers);

        return re;
    }

    @Override
    public List<Account> getAccountSummary() {
        List<Account> accounts = new ArrayList<>();

        requestBuilder.uri(URI.create(API_URL + "/accounts/" + getAccount_id() + "/summary"));
        try {
            HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

            // Check for API errors
            if (response.statusCode() == 429) {
                logger.warn("⚠ Rate limit exceeded. Retrying after 5 seconds...");
                Thread.sleep(5000);
                return getAccountSummary();
            }
            if (response.statusCode() != 200) {
                logger.error("🚨 Failed to fetch account summary from OANDA: {}", response.body());
                throw new RuntimeException("Failed to fetch account summary: " + response.body());
            }

            // Log raw API response for debugging
            logger.info("Raw API Response: {}", response.body());

            // Parse JSON response
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode accountsJson = objectMapper.readTree(response.body());

            // Ensure "account" field exists
            JsonNode accountJson = accountsJson.get("account");
            if (accountJson != null) {
                Account account = objectMapper.readValue(accountJson.traverse(), Account.class);
                accounts.add(account);
            } else {
                logger.error("⚠ No 'account' field found in API response: {}", response.body());
            }

        } catch (IOException | InterruptedException e) {
            logger.error("❌ Exception while fetching account summary: ", e);
            throw new RuntimeException(e);
        }

        logger.info("✅ Account summary: {}", accounts);
        return accounts;
    }

    @Override
    public Set<Integer> getSupportedGranularity() {
        return Set.of(
                CandlestickInterval.ONE_MINUTE.getSeconds(),
                CandlestickInterval.FIVE_MINUTES.getSeconds(),
                CandlestickInterval.THIRTY_MINUTES.getSeconds(),
                CandlestickInterval.ONE_HOUR.getSeconds(),
                CandlestickInterval.FOUR_HOURS.getSeconds(),
                CandlestickInterval.SIX_HOURS.getSeconds(),
                CandlestickInterval.DAY.getSeconds(),
                CandlestickInterval.WEEK.getSeconds(),
                CandlestickInterval.MONTH.getSeconds()
        );
    }


    @Override
    public double fetchLivesBidAsk(@NotNull TradePair tradePair) {
        try {
            // Construct OANDA API URL for fetching pricing data
            String url = String.format("%s/accounts/%s/pricing?instruments=%s",
                    API_URL, account_id, tradePair.toString('_')); // Convert trade pair format
            requestBuilder.uri(URI.create(url));

            HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 429) {
                logger.warn("⚠️ Rate limit hit while fetching bid/ask prices. Consider implementing backoff.");
                return 0;
            }

            if (response.statusCode() != 200) {
                logger.error("❌ Failed to fetch live bid/ask prices from OANDA: {}", response.body());
                return 0;
            }

            // Parse JSON response
            JsonNode rootNode = OBJECT_MAPPER.readTree(response.body());

            if (!rootNode.has("prices") || !rootNode.get("prices").isArray()) {
                logger.error("❌ No valid price data received from OANDA.");
                return 0;
            }

            JsonNode pricesNode = rootNode.get("prices");
            for (JsonNode priceNode : pricesNode) {
                String instrument = priceNode.path("instrument").asText();
                double bidPrice = priceNode.path("bids").get(0).path("price").asDouble(0);
                double askPrice = priceNode.path("asks").get(0).path("price").asDouble(0);

                // Validate retrieved values
                if (bidPrice == 0 || askPrice == 0) {
                    logger.warn("⚠️ Skipping invalid price data for {}: bid={}, ask={}", instrument, bidPrice, askPrice);
                    continue;
                }

                // Convert OANDA instrument format (EUR_USD) to TradePair format (EUR/USD)
                if (instrument.equalsIgnoreCase(tradePair.toString().replace("/", "_"))) {
                    logger.info("📈 Live Price for {} | Bid: {} | Ask: {}", tradePair, bidPrice, askPrice);
                    return (askPrice + bidPrice) / 2;
                }
            }

            logger.error("❌ No matching trade pair data found.");
        } catch (IOException | InterruptedException e) {
            logger.error("❌ Error fetching live bid/ask prices from OANDA: {}", e.getMessage());
        }

        return 0;
    }


    public static class OandaCandleDataSupplier extends CandleDataSupplier {

        private static final Logger logger = LoggerFactory.getLogger(OandaCandleDataSupplier.class);
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first trade

        OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));


        }



        @Override
        public Set<Integer> getSupportedGranularity() {
            // https://docs.pro.coinbase.com/#get-historic-rates
            return new TreeSet<>(Set.of(60,
                    60 * 5, 60 * 15, 60 * 30, 3600,
                    3600 * 2, 3600 * 4, 3600 * 6, 3600 * 24, 3600 * 24 * 7, 3600 * 24 * 7 * 4

            ));
        }

        private static final int MAX_RETRIES = 5;
        private static final long INITIAL_DELAY_MS = 500; // 500ms initial delay
        private static final long RATE_LIMIT_DELAY_MS = 1000; // 1 sec between requests
        private static final Map<Integer, List<CandleData>> candleCache = new ConcurrentHashMap<>();
        private final Semaphore rateLimiter = new Semaphore(1); // Prevents exceeding API limits

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return
                    new OandaCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public Future<List<CandleData>> get() {
            if (endTime.get() == -1) {
                endTime.set((int) Instant.now().getEpochSecond()); // Ensures correct timestamp format
            }

            int startTime = Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA);
            String startDateString = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(startTime));

            // 🔍 Check Cache First to Reduce API Calls
            if (candleCache.containsKey(startTime)) {
                logger.info("✅ Using cached candles for {}", tradePair);
                return CompletableFuture.completedFuture(candleCache.get(startTime));
            }

            String uriStr = "https://api-fxtrade.oanda.com/v3/instruments/"
                    + tradePair.toString('_')
                    + "/candles?count=" + numCandles
                    + "&price=M&to=" + startDateString
                    + "&granularity=" + granularityToString(secondsPerCandle);

            return fetchWithRetries(uriStr, startTime, tradePair, 0);
        }

        /**
         * Handles fetching candle data with retries on OANDA rate-limiting (429 errors).
         */
        private CompletableFuture<List<CandleData>> fetchWithRetries(String uriStr, int cacheKey, TradePair tradePair, int retryCount) {
            return CompletableFuture.runAsync(() -> {
                try {
                    // Apply OANDA Rate-Limit Throttling
                    rateLimiter.acquire();
                    Thread.sleep(RATE_LIMIT_DELAY_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).thenCompose(_ -> client.sendAsync(
                    requestBuilder.uri(URI.create(uriStr)).build(),
                    HttpResponse.BodyHandlers.ofString())
            ).thenCompose(response -> {
                if (response.statusCode() == 429) { // Too Many Requests (Rate-Limited)
                    if (retryCount < MAX_RETRIES) {
                        long backoffTime = INITIAL_DELAY_MS * (1L << retryCount); // Exponential Backoff
                        logger.warn("⚠️ Rate-limited! Retrying in {} ms...", backoffTime);

                        return CompletableFuture.supplyAsync(() -> null,
                                        CompletableFuture.delayedExecutor(backoffTime, TimeUnit.MILLISECONDS))
                                .thenCompose(_ -> fetchWithRetries(uriStr, cacheKey, tradePair, retryCount + 1));

                    } else {
                        logger.error("🚨 Max retries reached for fetching candles.");
                        return CompletableFuture.failedFuture(new RuntimeException("Rate limit exceeded"));
                    }
                }

                if (response.statusCode() != 200) {
                    logger.error("❌ Error fetching candles: {}", response.body());
                    return CompletableFuture.failedFuture(new RuntimeException("Failed to fetch candles"));
                }

                return processCandleResponse(response.body(), cacheKey, tradePair);
            }).whenComplete((_, _) -> rateLimiter.release()); // 🔓 Release the semaphore
        }

        /**
         * Processes the OANDA API response, filters valid candles, and caches data.
         */
        private CompletableFuture<List<CandleData>> processCandleResponse(String responseBody, int cacheKey, TradePair tradePair) {
            try {
                JsonNode res = OBJECT_MAPPER.readTree(responseBody);
                logger.info("📊 OANDA trade pair: {}", tradePair.toString('/'));
                logger.info("📥 OANDA response: {}", res);

                List<CandleData> candleData = new ArrayList<>();
                JsonNode candlesNode = res.get("candles");

                if (candlesNode != null && candlesNode.isArray()) {
                    for (JsonNode candle : candlesNode) {
                        if (!candle.has("time") || !candle.has("mid")) {
                            logger.warn("⚠️ Skipping invalid candle data: {}", candle);
                            continue;
                        }

                        int time = (int) Instant.parse(candle.get("time").asText()).getEpochSecond();

                        // Skip in-progress candles
                        if (time + secondsPerCandle > endTime.get()) {
                            continue;
                        }

                        candleData.add(new CandleData(
                                candle.get("mid").get("o").asDouble(),  // Open price
                                candle.get("mid").get("c").asDouble(),  // Close price
                                candle.get("mid").get("h").asDouble(),  // High price
                                candle.get("mid").get("l").asDouble(),  // Low price
                                time, (int) System.currentTimeMillis(),
                                candle.get("volume").asLong()  // Volume
                        ));
                    }

                    // Sort candles before returning
                    candleData.sort(Comparator.comparingLong(CandleData::getOpenTime));
                    logger.info("��� Received {} candles for {}.", candleData.size(),
                            tradePair.toString('/'));

                    // 📌 Cache the latest candle data
                    candleCache.put(candleCache.size(), candleData);
                    endTime.set(candleData.isEmpty() ? cacheKey : candleData.getFirst().getOpenTime()); // ✅ Update endTime

                    return CompletableFuture.completedFuture(candleData);
                }

                return CompletableFuture.completedFuture(Collections.emptyList());

            } catch (JsonProcessingException ex) {
                logger.error("🚨 JSON Parsing Error", ex);
                return CompletableFuture.failedFuture(new RuntimeException("Failed to parse JSON", ex));
            }
        }



    }
}
