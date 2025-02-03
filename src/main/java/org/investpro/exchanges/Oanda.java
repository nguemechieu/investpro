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
import org.investpro.*;
import org.investpro.Currency;
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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;

@Getter
@Setter
public class Oanda extends Exchange {

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

    public static int numCandles = 500;
    private static final int MAX_CANDLES_PER_REQUEST = numCandles;
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

    @Override
    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        Objects.requireNonNull(tradePair);

        CompletableFuture<List<OrderBook>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            String uriStr = API_URL + "/accounts/" + account_id + "/pricing?instruments=" + tradePair.toString('_');

            requestBuilder.uri(URI.create(uriStr));

            try {
                HttpResponse<String> response = client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());

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
                    logger.info("Found order book entry: {}", priceEntry);

                    Instant time = Instant.parse(priceEntry.get("time").asText());
                    double bidPrice = priceEntry.get("bids").get(0).get("price").asDouble();
                    double askPrice = priceEntry.get("asks").get(0).get("price").asDouble();
                    double bidLiquidity = priceEntry.get("bids").get(0).get("liquidity").asDouble();
                    double askLiquidity = priceEntry.get("asks").get(0).get("liquidity").asDouble();

                    OrderBook orderBookEntry = new OrderBook(
                            tradePair,
                            bidPrice,
                            askPrice,
                            bidLiquidity,
                            askLiquidity,
                            time
                    );
                    orderBooks.add(orderBookEntry);
                }

                futureResult.complete(orderBooks);

            } catch (IOException | InterruptedException e) {
                logger.error("Error fetching order book from OANDA", e);
                futureResult.completeExceptionally(e);
            }
        });

        return futureResult;
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
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);

        CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            String uriStr = API_URL;
            uriStr += "/accounts/" + account_id + "/pricing?instruments=" + tradePair.toString('_');


            requestBuilder.uri(URI.create(uriStr));

            try {
                HttpResponse<String> response = client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());

                JsonNode tradesResponse = OBJECT_MAPPER.readValue(response.body(), JsonNode.class);

                    List<Trade> trades = new ArrayList<>();
                for (JsonNode trade : tradesResponse.get("prices")) {

                    logger.info("Found trade {}", trade);


                    logger.info("My Time :{}", trade.get("time").asText());


                    Instant time = Instant.parse(trade.get("time").asText());

                    double price = trade.get("price").asDouble();
                    double size = trade.get("liquidity").asDouble();
                    Trade tr = new Trade(
                                    tradePair,
                            price,
                            size,
                            Side.getSide("SELL"),
                            UUID.randomUUID().timestamp(),
                                    time
                    );
                    trades.add(tr);

                    logger.info("My Trade :{}", trades);
                }
                futureResult.complete(trades);






            } catch (IOException | InterruptedException e) {
                futureResult.completeExceptionally(e);
            }
        });

        return futureResult;
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
                .formatted(API_URL, tradePair.toString('_'), getOandaGranularity(secondsPerCandle), startDateString, MAX_CANDLES_PER_REQUEST);

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

        logger.info("OANDA response: %s".formatted(response.body()));

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
        return false; // WebSocket support for trades can be added if needed
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

        CustomWebSocketClient re = new CustomWebSocketClient(API_URL + "/v3/accounts/" + account_id + "/pricing/stream?instruments=" + tradePair) {

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

                            Trade trade = new Trade(tradePair, price, size, side, timestamp);
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
                return false;
            }

            @Override
            public void streamLiveTrades(TradePair tradePair, UpdateInProgressCandleTask updateInProgressCandleTask) {

            }
        };
        re.connect(headers);

        return re;
    }

    @Override
    public List<PriceData> fetchLivesBidAsk(@NotNull TradePair tradePair) {
        List<PriceData> priceDataList = new ArrayList<>();

        try {
            // Construct the OANDA API URL for fetching pricing data
            String url = String.format("%s/accounts/%s/pricing?instruments=%s",
                    API_URL, account_id, tradePair.toString('_')); // Convert trade pair format
            requestBuilder.uri(URI.create(url));


            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("❌ Failed to fetch live bid/ask prices from OANDA: {}", response.body());
                return Collections.emptyList();
            }

            // Parse JSON response
            JsonNode rootNode = new ObjectMapper().readTree(response.body());
            JsonNode pricesNode = rootNode.get("prices");

            if (pricesNode == null || !pricesNode.isArray()) {
                logger.error("❌ No valid price data received from OANDA.");
                return Collections.emptyList();
            }

            for (JsonNode priceNode : pricesNode) {
                String instrument = priceNode.get("instrument").asText();
                double bidPrice = priceNode.get("bids").get(0).get("price").asDouble();
                double askPrice = priceNode.get("asks").get(0).get("price").asDouble();
                long timestamp = Instant.parse(priceNode.get("time").asText()).getEpochSecond();

                // Convert OANDA instrument format (EUR_USD) to TradePair format (EUR/USD)
                if (instrument.equalsIgnoreCase(tradePair.toString().replace("/", "_"))) {
                    priceDataList.add(new PriceData(tradePair.toString('/'), bidPrice, askPrice, Instant.ofEpochMilli(timestamp)));
                    logger.info("\uD83D\uDCC8 Live Price for {} | Bid: {} | Ask: {}", tradePair, bidPrice, askPrice);
                }
            }
        } catch (IOException | InterruptedException e) {
            logger.error("❌ Error fetching live bid/ask prices from OANDA: {}", e.getMessage());
        }

        return priceDataList;
    }


    /**
     * Returns the closest supported granularity (time interval) for OANDA.
     *
     * @param secondsPerCandle the candle duration in seconds
     * @return the granularity in OANDA format (e.g., "M1", "M5")
     */
    public String getOandaGranularity(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "M1";
            case 300 -> "M5";
            case 900 -> "M15";
            case 3600 -> "H1";
            case 3600 * 2 -> "H2";
            case 3600 * 3 -> "H3";
            case 86400 -> "D";
            case 604800 -> "W";
            case 604800 * 4 -> "Mn";

            default -> throw new IllegalArgumentException("Unsupported granularity: %d".formatted(secondsPerCandle));
        };
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

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return null;
        }
        @Override
        public Future<List<CandleData>> get() {
            if (endTime.get() == -1) {
                endTime.set((int) (Instant.now().getEpochSecond())); // Fix: Use seconds instead of milliseconds
            }

            int startTime = Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA);
            String startDateString = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochSecond(startTime));

            String uriStr = "https://api-fxtrade.oanda.com/v3/instruments/"
                    + tradePair.toString('_')
                    + "/candles?count=" + numCandles
                    + "&price=M&to=" + startDateString
                    + "&granularity=" + granularityToString(secondsPerCandle);

            return client.sendAsync(
                            requestBuilder.uri(URI.create(uriStr)).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {
                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response);
                            logger.info("OANDA trade pair: {}", tradePair);
                            logger.info("OANDA res: {}", res);
                        } catch (JsonProcessingException ex) {
                            logger.error("Error parsing JSON response", ex);
                            throw new RuntimeException("Failed to parse JSON", ex);
                        }

                        List<CandleData> candleData = new ArrayList<>();

                        JsonNode candlesNode = res.get("candles");
                        if (candlesNode != null && candlesNode.isArray()) {
                            logger.info("Response {}", candlesNode);

                            for (JsonNode candle : candlesNode) {
                                if (!candle.has("time") || !candle.has("mid")) {
                                    logger.warn("Skipping invalid candle data: {}", candle);
                                    continue;
                                }

                                int time = (int) Instant.parse(candle.get("time").asText()).getEpochSecond();

                                // Skip the in-progress candle
                                if (time + secondsPerCandle > endTime.get()) {
                                    continue;
                                }

                                candleData.add(new CandleData(
                                        candle.get("mid").get("o").asDouble(),  // open price
                                        candle.get("mid").get("c").asDouble(),  // close price
                                        candle.get("mid").get("h").asDouble(),  // high price
                                        candle.get("mid").get("l").asDouble(),  // low price
                                        time, (int) Instant.now().getEpochSecond(),
                                        candle.get("volume").asLong()  // volume
                                ));
                            }

                            // Ensure candles are sorted before returning
                            candleData.sort(Comparator.comparingLong(CandleData::getOpenTime));

                            logger.info("Processed candles: {}", candleData);
                            endTime.set(startTime); // Update only once after processing all candles

                            return candleData;
                        }

                        return Collections.emptyList();
                    });
        }

        private @NotNull String granularityToString(int actualGranularity) {

            String x;
            String str;
            if (actualGranularity < 3600) {
                x = String.valueOf(actualGranularity / 60);
                str = "M";
            } else if (actualGranularity < 86400) {
                x = String.valueOf((actualGranularity / 3600));
                str = "H";
            } else if (actualGranularity < 604800) {
                x = "";//String.valueOf(secondsPerCandle / 86400);
                str = "D";
            } else if (actualGranularity < 2592000) {
                x = String.valueOf((actualGranularity / 604800));
                str = "W";
            } else {
                x = String.valueOf((actualGranularity * 7 / 2592000 / 7));
                str = "M";
            }
            return str + x;

        }
    }
}
