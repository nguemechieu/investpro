package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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

        logger.info("SECRET RECEIVED: %s".formatted(apiSecret));
        requestBuilder.header("Authorization", "Bearer " + apiSecret);
        requestBuilder.setHeader("Accept", "application/json");
        requestBuilder.setHeader("Content-Type", "application/json");
    }

    @Override
    public CompletableFuture<List<Fee>> getTradingFee() throws IOException, InterruptedException {
        // Build the URI for the request
        requestBuilder.uri(URI.create(
                "%s/accounts/%s/trades/fee".formatted(API_URL, account_id)
        ));

        // Send the GET request and capture the response
        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        // Handle non-200 status codes by showing an error message and throwing a RuntimeException
        if (response.statusCode() != 200) {
            new Messages(
                    "Error",  // Error type
                    "Failed to fetch trading fee: %s".formatted(response.body())  // Error message from the response
            );
            throw new RuntimeException("Error fetching trading fee: %d".formatted(response.statusCode()));
        }

        Fee fee = OBJECT_MAPPER.readValue(response.body(), Fee.class);
        // Ensure that the fee is not null before returning it
        if (fee == null) {
            throw new RuntimeException("Failed to parse trading fee from response.\n" + response.body());
        }
        return CompletableFuture.completedFuture(Collections.singletonList(fee));

    }


    @Override
    public CompletableFuture<List<Account>> getAccounts() throws IOException, InterruptedException {
        // Build the URI for the request
        requestBuilder.uri(URI.create("%s/accounts".formatted(API_URL)));

        // Send the GET request and capture the response
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        // Handle non-200 status codes by showing an error message and throwing a RuntimeException
        if (response.statusCode() != 200) {
            new Messages(
                    "Error",  // Error type
                    "Failed to fetch accounts: %s".formatted(response.body())  // Error message from the response
            );
            throw new RuntimeException("Error fetching accounts: %d".formatted(response.statusCode()));
        }
        List<Account> accounts = new ArrayList<>();
        logger.info("account "+response.body());
        JSONObject account = new JSONObject(response.body());
        if (account.has("accounts")) {
            JSONArray accountsArray = account.getJSONArray("accounts");
            for (int i = 0; i < accountsArray.length(); i++) {
                JSONObject accountObject = accountsArray.getJSONObject(i);
                Account account0 = new Account();
                account0.setAccountId(accountObject.getString("id"));

                if (account.has("balance")) {
                account0.setBalance(accountObject.getDouble("balance"));}
                if (account.has("marginRate")) {
                    account0.setMarginRate(accountObject.getDouble("marginRate"));
                }
                if (account.has("currency")) {
                    account0.setCurrency(accountObject.getString("currency"));
                }
                if (account.has("created")) {
                    account0.setCreated(String.valueOf(LocalDateTime.ofInstant(Instant.ofEpochMilli(accountObject.getLong("createdTime")), ZoneOffset.UTC)));
                }
                if (account.has("mt4AccountID")) {
                    account0.setMt4AccountID(accountObject.getInt("mt4AccountID"));
                }
                if (account.has("tags")){
                    JSONArray tagsArray = accountObject.getJSONArray("tags");
                    List<String> tags = new ArrayList<>();
                    for (int j = 0; j < tagsArray.length(); j++) {
                        tags.add(tagsArray.getString(j));
                    }
                    account0.setTags(tags);

                }

                accounts.add(account0);
            }
        } else {
            throw new RuntimeException("Failed to parse accounts from response.\n%s".formatted(response.body()));
        }

        return CompletableFuture.completedFuture(accounts);
    }



    // Extract and set other account details if necessary (e.g., margin rate, trading permissions, etc.)

        // Return the list of accounts


    @Override
    public String getSymbol() {
        return tradePair.toString('/');
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
            throw new RuntimeException("Error creating order: %d".formatted(response.statusCode()));
        }
        logger.info("Order created: {}", response.body());
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

        requestBuilder.uri(URI.create(
                API_URL + "/accounts/%s/orders/%s".formatted(account_id, orderId) // OANDA requires account ID and order ID
        ));
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
        return new OandaCandleDataSupplier(secondsPerCandle, tradePair); // Custom class to handle OANDA candlestick data
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);

        CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            String uriStr = API_URL + "/accounts/%s/trades".formatted(account_id);

            try {
                HttpResponse<String> response = client.send(
                        requestBuilder
                                .uri(URI.create(uriStr))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());

                JsonNode tradesResponse = new ObjectMapper().readTree(response.body());
                if (!tradesResponse.isArray() || tradesResponse.isEmpty()) {
                    futureResult.completeExceptionally(new RuntimeException("OANDA trades response was empty or not an array"));
                } else {
                    List<Trade> trades = new ArrayList<>();
                    for (JsonNode trade : tradesResponse) {
                        Instant time = Instant.parse(trade.get("openTime").asText());
                        if (time.compareTo(stopAt) <= 0) {
                            futureResult.complete(trades);
                            break;
                        } else {
                            trades.add(new Trade(
                                    tradePair,
                                    DefaultMoney.ofFiat(trade.get("price").asText(), tradePair.getCounterCurrency()),
                                    DefaultMoney.ofCrypto(trade.get("units").asText(), tradePair.getBaseCurrency()),
                                    Side.getSide(trade.get("side").asText()),
                                    trade.get("tradeID").asLong(),
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
                                        "%s/instruments/%s/candles?granularity=%s&from=%s", API_URL,
                                        tradePair.toString('_'),
                                        getOandaGranularity(secondsPerCandle),
                                        startDateString
                                )))

                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    logger.info("OANDA response: %s".formatted(response));
                    JsonNode res;
                    try {
                        res = new ObjectMapper().readTree(response);
                    } catch (JsonProcessingException ex) {

                        new  Messages(
                                "Warning", "Failed to parse OANDA candles response. The response was: %%s%s".formatted(ex.getMessage()));


                        throw new RuntimeException(ex);

                    }

                    if (res.isEmpty()) {
                        return Optional.empty();
                    }

                    JsonNode currCandle = res.get("candles").get(0);
                    Instant openTime = Instant.parse(currCandle.get("time").asText());

                    return Optional.of(new InProgressCandleData(
                            (int) openTime.getEpochSecond(),
                            currCandle.get("mid").get("o").asDouble(),
                            currCandle.get("mid").get("h").asDouble(),
                            currCandle.get("mid").get("l").asDouble(),
                            (int)currentCandleStartedAt.getEpochSecond(),
                            currCandle.get("mid").get("c").asDouble(),
                            currCandle.get("volume").asDouble()
                    ));
                });
    }

    @Override
    public List<Order> getPendingOrders() throws IOException, InterruptedException, ExecutionException {

        requestBuilder.uri(URI.create(API_URL + "/accounts/%s/pendingOrders".formatted(account_id)));
        HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();


        if ( response.statusCode()!=200){

            new Messages("Error", response.body());
            throw new RuntimeException("Error fetching pending orders: %d".formatted(response.statusCode()));
        }








        ObjectMapper objectMapper = new ObjectMapper();
        return objectMapper.readValue(response.body(), new TypeReference<>() {
        });
    }

    @Override
    public CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        requestBuilder.uri(URI.create("%s/instruments/%s/orderBook".formatted(API_URL, tradePair.toString('_'))));

        HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("OANDA response: " , response.body());
        ObjectMapper objectMapper = new ObjectMapper();
        OrderBook orderBook = objectMapper.readValue(response.body(), OrderBook.class);
        return CompletableFuture.completedFuture(orderBook);
    }

    @Override
    public Position getPositions() throws IOException, InterruptedException {
        // OANDA position book can be fetched if needed

        requestBuilder.uri(URI.create("%s/accounts/%s/positions".formatted(API_URL, account_id)));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
       if ( response.statusCode()!=200)
       {
           new Messages("Error", "Error fetching positions: %s".formatted(response.body()));
           throw new RuntimeException("Error fetching positions: %d".formatted(response.statusCode()));
       }

        logger.info("OANDA response: %s".formatted(response.body()));


        return OBJECT_MAPPER.readValue(response.body(), Position.class);




    }

    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        requestBuilder.uri(URI.create(API_URL + "/accounts/%s/orders".formatted(account_id)));

        HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("OANDA response: %s".formatted(response.body()));
        ObjectMapper objectMapper = new ObjectMapper();
        return Arrays.asList(objectMapper.readValue(response.body(), Order[].class));
    }

    @Override
    public ObservableList<Order> getOrders() throws IOException, InterruptedException {

        requestBuilder.uri(
                URI.create(API_URL + "/accounts/%s/orders".formatted(account_id))
        );
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode()!= 200) {

            new Messages("Error", "%d\n\n%s".formatted(response.statusCode(), response.body()));
            throw new RuntimeException("ORDER HTTP error response: %d".formatted(response.statusCode()));
        }


        Order orders = OBJECT_MAPPER.readValue(response.body(), Order.class);
        return FXCollections.observableArrayList((orders));


    }

    @Override
    public CompletableFuture<ArrayList<TradePair>> getTradePairs() throws Exception {

        String urls = "%s/accounts/%s/instruments".formatted(API_URL, account_id);
        requestBuilder.uri(URI.create(urls));
        ArrayList<TradePair> tradePairs = new ArrayList<>();

            HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode()!= 200) {

                new  Messages("Error", "%d\n\n%s".formatted(response.statusCode(), response.body()));
throw new RuntimeException("HTTP error response: %d".formatted(response.statusCode()));
            }


            JsonNode res = new ObjectMapper().readTree(response.body());
            logger.info("OANDA response: %s".formatted(res));

            JsonNode instruments = res.get("instruments");
            for (JsonNode instrument : instruments) {
                String baseCurrency = instrument.get("name").asText().split("_")[0];
                String counterCurrency = instrument.get("name").asText().split("_")[1];
                TradePair tp = new TradePair(baseCurrency, counterCurrency);
                tradePairs.add(tp);
                logger.info("OANDA trade pair: %s".formatted(tp));
            }


        return CompletableFuture.completedFuture(tradePairs);
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
    public List<OrderBook> streamOrderBook(@NotNull TradePair tradePair) {
        return List.of(); // WebSocket streaming for order book not implemented here
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        return null; // Implement if OANDA supports cancelling all orders at once
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

    // OANDA supported granularity (intervals)
    private static final Set<String> SUPPORTED_GRANULARITIES = Set.of(
            "S5", "M1", "M5", "M15", "H1", "D"
    );

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
            super(240, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));


        }



        @Override
        public Set<Integer> getSupportedGranularity() {
            // https://docs.pro.coinbase.com/#get-historic-rates
            return new TreeSet<>(Set.of(60, 300, 900, 3600, 21600, 86400));
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return null;
        }

        public static int getTimeFromString(@NotNull String timeString) {

            int tim = (int) Instant.parse(timeString).getEpochSecond();
            logger.info(STR."Created timestamp: \{timeString + tim}");
            return tim;
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
           // "https://api-fxtrade.oanda.com/v3/instruments/USD_CAD/candles?price=BA&from=2016-10-17T15%3A00%3A00.000000000Z&granularity=M1"
            String uriStr = STR."https://api-fxtrade.oanda.com/v3/instruments/\{tradePair.toString('_')}/candles?count=200&price=M&from="+startDateString+"&granularity="+granularityToString(secondsPerCandle);

            if (startTime == EARLIEST_DATA) {
                // signal more data is false
                return CompletableFuture.completedFuture(Collections.emptyList());
            }



            return client.sendAsync(
                            requestBuilder
                                    .uri(URI.create(uriStr)).build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {

                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response);
                            logger.info(STR."OANDA response: \{res}");
                        } catch (JsonProcessingException ex) {

                             logger.error(STR."Failed to parse OANDA response: \{response}");
                             new Messages(

                                     "Error",
                                     ex.getMessage()

                             );
                            throw new RuntimeException(ex);
                        }
                        List<CandleData> candleData = new ArrayList<>();

                        if (!res.get("candles").isEmpty()) {


                            logger.info(STR."Response\{res.get("candles")}");
                            // Remove the current in-progress candle

                            for (JsonNode candle : res.get("candles")) {

                                int time;
                                if (candle.has("time")) {

                                    time = getTimeFromString(candle.get("time").asText());

                                    if (time + secondsPerCandle > endTime.get()) {
                                        ((ArrayNode) candle.get("candles")).remove(0);
                                    }
                                    endTime.set(startTime);
                                    int closeTime = 0;

                                    if ((-time + (System.currentTimeMillis() / 1000)) == secondsPerCandle) {
                                        closeTime = (candle.get("time").asInt());
                                }
                                    candleData.add(new CandleData(
                                            candle.get("mid").get("o").asDouble(),  // open price
                                            candle.get("mid").get("c").asDouble(),  // close price
                                            candle.get("mid").get("h").asDouble(),  // high price
                                            candle.get("mid").get("l").asDouble(),  // low price
                                            candle.get("mid").get("o").asInt(),     // open time.
                                        closeTime,  // close time
                                            candle.get("volume").asDouble()   // volume
                                ));


                                }
                                candleData.sort(Comparator.comparingLong(CandleData::getOpenTime));

                                logger.info(candleData.toString());
                                return candleData;
                            }
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
