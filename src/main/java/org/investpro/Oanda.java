package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.control.Alert;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;


public class Oanda extends Exchange {

    static HttpClient client = HttpClient.newHttpClient();
    private static final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static final Logger logger = LoggerFactory.getLogger(Oanda.class);
    public static final String API_URL = "https://api-fxtrade.oanda.com/v3";  // OANDA API URL
    private final String account_id;


    public Oanda(String accountId, String apiSecret) {
        super(accountId, apiSecret); // OANDA uses only an API key for authentication, no secret required
        this.account_id = accountId;

        logger.info("SECRET RECEIVED: {}", apiSecret);
        requestBuilder.header("Authorization", "Bearer " + apiSecret);
        requestBuilder.setHeader("Accept", "application/json");
        requestBuilder.setHeader("Content-Type", "application/json");
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
    public String getExchangeMessage() {
        return message;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
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
                        requestBuilder

                                .build(),
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

        return fetchCandlesRecursive(tradePair, startDateString, secondsPerCandle, secondsPerCandle, allCandles)
                .thenApply(candles -> candles.isEmpty() ? Optional.empty() : Optional.of(candles));
    }

    private CompletableFuture<List<InProgressCandleData>> fetchCandlesRecursive(
            TradePair tradePair, String startDateString, int secondsPerCandle,
            int limit, List<InProgressCandleData> allCandles) {

        URI uri = URI.create(String.format(
                "%s/instruments/%s/candles?granularity=%s&to=%s&count=%d",
                API_URL, tradePair.toString('_'), getOandaGranularity(secondsPerCandle),
                startDateString, limit));

        return client.sendAsync(requestBuilder.uri(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenCompose(response -> {
                    logger.info("OANDA response: {}", response);

                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                    } catch (JsonProcessingException ex) {
                        logger.error("Failed to parse OANDA response", ex);
                        return CompletableFuture.completedFuture(allCandles);
                    }

                    JsonNode candles = res.get("candles");
                    if (candles == null || !candles.isArray() || candles.isEmpty()) {
                        logger.warn("No candles data found in the response");
                        return CompletableFuture.completedFuture(allCandles);
                    }

                    // Parse and store candles
                    for (JsonNode candleNode : candles) {
                        parseCandleData(candleNode).ifPresent(allCandles::add);
                    }

                    // Determine if pagination is needed (fetch next batch)
                    if (candles.size() == limit) {
                        String lastCandleTime = candles.get(candles.size() - 1).get("time").asText();
                        return fetchCandlesRecursive(tradePair, lastCandleTime, secondsPerCandle, limit, allCandles);
                    } else {
                        return CompletableFuture.completedFuture(allCandles);
                    }
                })
                .exceptionally(ex -> {
                    logger.error("Error fetching or processing candle data", ex);
                    return allCandles;
                });
    }

    private Optional<InProgressCandleData> parseCandleData(JsonNode candleNode) {
        if (candleNode == null) return Optional.empty();

        long openTime = Instant.parse(candleNode.get("time").asText()).getEpochSecond();
        return Optional.of(new InProgressCandleData(

                candleNode.get("mid").get("o").asDouble(),
                candleNode.get("mid").get("h").asDouble(),
                candleNode.get("mid").get("l").asDouble(),
                candleNode.get("mid").get("c").asDouble(),
                (int) openTime,
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
    public List<OrderBook> getOrderBook(TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        requestBuilder.uri(URI.create("%s/instruments/%s/orderBook".formatted(API_URL, tradePair.toString('_'))));

        HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("OANDA response: {}", response.body());

        List<OrderBook> orderBooks = new ArrayList<>();
        JsonNode orderBooksJson = OBJECT_MAPPER.readTree(response.body());
        if (!orderBooksJson.isArray() || orderBooksJson.isEmpty()) {

            for (JsonNode orderBook : orderBooksJson) {

                if (orderBook.has("lastTransactionID")) {

                    orderBooks.add(OBJECT_MAPPER.convertValue(orderBook.get("lastTransactionID"), OrderBook.class));
                }
            }
        }
        return orderBooks;
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

        String urls = "%s/accounts/%s/instruments".formatted(API_URL, account_id);
        requestBuilder.uri(URI.create(urls));
        List<TradePair> tradePairs = new ArrayList<>();

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!= 200) {
                new Messages(Alert.AlertType.ERROR, "%d\n\n%s".formatted(response.statusCode(), response.body()));
                throw new RuntimeException("HTTP error response: %d".formatted(response.statusCode()));
            }


            JsonNode res = new ObjectMapper().readTree(response.body());
            logger.info("OANDA response: %s".formatted(res));

            JsonNode instruments = res.get("instruments");
            for (JsonNode instrument : instruments) {
                String baseCurrency = instrument.get("name").asText().split("_")[0];
                String counterCurrency = instrument.get("name").asText().split("_")[1];
                TradePair tp = new TradePair(baseCurrency, counterCurrency);
                tp.getBaseCurrency().setCurrencyType(CurrencyType.FIAT.name());
                tp.getCounterCurrency().setCurrencyType(CurrencyType.FIAT.name());
                tradePairs.add(tp);
                Currency.save((ArrayList<Currency>) tradePairs.stream().map(
                TradePair::getCounterCurrency
        ).collect(Collectors.toList()));
                logger.info("OANDA trade pair: %s".formatted(tp));
            }


        Currency.save((ArrayList<Currency>) tradePairs.stream().map(
                TradePair::getBaseCurrency
        ).collect(Collectors.toList()));


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


    /**
     * Returns the closest supported granularity (time interval) for OANDA.
     *
     * @param secondsPerCandle the candle duration in seconds
     * @return the granularity in OANDA format (e.g., "M1", "M5")
     */
    public String getOandaGranularity(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 5 -> "S5";
            case 60 -> "M1";
            case 300 -> "M5";
            case 900 -> "M15";
            case 3600 -> "H1";

            case 86400 -> "D";
            // Add more granularity as needed
             case 28800 -> "H4";
            // Example: case 604800 -> "1D"
             case 2592000 -> "1W";
            // Example: case 31536000 -> "1M";
             case 315360000 -> "1Y";

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
            super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));


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
                    + "&price=M&from=" + startDateString
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
                                        time,
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
