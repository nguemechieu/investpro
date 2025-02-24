package org.investpro.exchanges;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
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
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;


public  class Coinbase extends Exchange {


    String websocketURL="wss://advanced-trade-ws.coinbase.com";
    public static HttpClient client = HttpClient.newHttpClient();
    public static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static final Logger logger = LoggerFactory.getLogger(Coinbase.class);
    public static final String API_URL = "https://api.coinbase.com/api/v3/brokerage";
    String apiKey;


    public Coinbase(String apikey, String apiSecret){
        super(apikey, apiSecret);
        Exchange.apiSecret = apiSecret;
        this.apiKey = apikey;

        requestBuilder.setHeader(
                "Accept", "application/json"
        );

        requestBuilder.setHeader(
                "Authorization", "Bearer " + apiSecret
        );

        requestBuilder.setHeader(
                "Content-Type", "application/json"
        );
    }

    // Set of supported granularity in seconds for Coinbase
    private static final Set<Integer> SUPPORTED_GRANULARITY = new TreeSet<>(Set.of(
            60,     // 1 minute
            60 * 5,    // 5 minutes
            60 * 15,    // 15 minutes,

            60 * 30,   // 30 minutes,
            3600,   // 1 hour
            21600,  // 6 hours
            86400,  // 1 day,
            86400 * 7
    ));

    @Override
    public List<Fee> getTradingFee() {
        return null;
    }



    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(
                "%s/orders".formatted(API_URL)
        ));

        requestBuilder.method("POST", HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(new CreateOrderRequest(
                tradePair.toString('/'),
                side,
                orderType,
                price,
                size,timestamp
                ,
                stopLoss,
                takeProfit))));


        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode()!= 200) {
            throw new RuntimeException("Error creating order: %d".formatted(response.statusCode()));
        }
        logger.info("Order created: {}", response.body());
    }

    @Override
    public List<Account> getAccounts() throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(
                "%s/accounts".formatted(API_URL)
        )) ;
        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString())
                ;
        if (response.statusCode()!= 200) {
            throw new RuntimeException("Error fetching accounts: %d".formatted(response.statusCode()));
        }
        return Arrays.asList(OBJECT_MAPPER.readValue(response.body(), OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Account[].class)));


    }

    @Override
    public String getExchangeMessage() {
        return message;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
    }



    /**
     * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
     * <p>
     * This method only needs to be implemented to support live syncing.
     *
     * @return
     */
    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt, int secondsPerCandle,
                                                                 Consumer<List<Trade>> tradeConsumer) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);

        if (stopAt.isAfter(Instant.now())) {
            logger.warn("fetchRecentTradesUntil: stopAt is in the future, ignoring");
            return null;
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
                String uriStr = API_URL;
                uriStr += "/products/%s/trades".formatted(tradePair.toString('-'));

                if (i != 0) {
                    uriStr += "?after=%d".formatted(afterCursor.get());
                }

                try {
                    HttpResponse<String> response = HttpClient.newHttpClient().send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(uriStr))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofString());

                    logger.info("response headers: %s".formatted(response.headers()));
                    if (response.headers().firstValue("CB-AFTER").isEmpty()) {
                        futureResult.completeExceptionally(new RuntimeException(
                                "coinbase trades response did not contain header \"cb-after\": %s".formatted(response)));
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
                                OrderBook prices = new OrderBook();
                                prices.getAskEntries().stream().toList().getLast().setPrice(trade.get("ask").get(0).asDouble());
                                prices.getBidEntries().stream().toList().getLast().setSize(trade.get("size").asLong());
                                prices.getBidEntries().stream().toList().getLast().setPrice(trade.get("bid").get(0).asDouble());
                                prices.getAskEntries().stream().toList().getLast().setSize(trade.get("size").asLong());

                                tradesBeforeStopTime.add(new Trade(tradePair, prices.getTradePair().getAsk(),
                                        trade.get("size").asLong(),
                                        Side.getSide(trade.get("side").asText()), trade.get("trade_id").asLong(), time));
                            }
                        }
                    }
                } catch (IOException | InterruptedException ex) {
                    logger.error("ex: ", ex);
                }
            }
        });

        return futureResult;
    }

    /**
     * This method only needs to be implemented to support live syncing.
     */
    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        String startDateString = "2016-10-17T15%3A00%3A00.000000000Z";

        DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
                currentCandleStartedAt, ZoneOffset.UTC));
        long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
        // Get the closest supported granularity to the ideal granularity.
        int actualGranularity = getSupportedGranularity(secondsPerCandle);


        return client.sendAsync(
                  requestBuilder
                                .uri(URI.create(String.format(
                                        "%s/products/%%s/candles?granularity=%%s&start=%%s".formatted(API_URL),
                                        tradePair.toString('-'), actualGranularity, startDateString)))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    logger.info("coinbase candles data response: " + response);
                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }

                    if (res.isEmpty()) {
                        return Optional.empty();
                    }

                    JsonNode currCandle;
                    Iterator<JsonNode> candleItr = res.iterator();
                    int currentTill = -1;
                    double openPrice = -1;
                    double highSoFar = -1;
                    double lowSoFar = 0;
                    long volumeSoFar = 0;
                    double lastTradePrice = -1;
                    boolean foundFirst = false;
                    while (candleItr.hasNext()) {
                        currCandle = candleItr.next();
                        if (currCandle.get(0).asInt() < currentCandleStartedAt.getEpochSecond() ||
                                currCandle.get(0).asInt() >= currentCandleStartedAt.getEpochSecond() +
                                        secondsPerCandle) {

                            continue;
                        } else {
                            if (!foundFirst) {

                                currentTill = currCandle.get(0).asInt();
                                lastTradePrice = currCandle.get(4).asDouble();
                                foundFirst = true;
                            } else if (
                                    currCandle.get(0).asInt() - currentTill > secondsPerCandle) {
                                // We've reached the end of the parent candle's duration
                                break;
                            }
                        }

                        openPrice = currCandle.get(3).asDouble();

                        if (currCandle.get(2).asDouble() > highSoFar) {
                            highSoFar = currCandle.get(2).asDouble();
                        }

                        if (currCandle.get(1).asDouble() < lowSoFar) {
                            lowSoFar = currCandle.get(1).asDouble();
                        }

                        volumeSoFar += currCandle.get(5).asLong();
                    }


                    return Optional.of(new InProgressCandleData(currentTill, openPrice, highSoFar, lowSoFar,
                            Instant.now().getEpochSecond(), lastTradePrice, volumeSoFar));
                });
    }

    @Override
    public List<Order> getPendingOrders() {
        return List.of();
    }

    @Override
    public void cancelOrder(String orderId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

        requestBuilder.uri(URI.create(
                "%s/orders/%s".formatted(API_URL, orderId)
        )) ;
        requestBuilder.setHeader("Content-Type", "application/json");
        requestBuilder.setHeader("Authorization", "Bearer " + apiKey);
        requestBuilder.method("DELETE", HttpRequest.BodyPublishers.noBody());
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                ;
        if (response.statusCode()!= 200) {
            throw new RuntimeException("Error cancelling order: %d".formatted(response.statusCode()));
        }
        logger.info("Order cancelled: {}", orderId);

    }

    @Override
    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        Objects.requireNonNull(tradePair, "TradePair cannot be null");

        CompletableFuture<List<OrderBook>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                String uriStr = API_URL + "/products/" + tradePair.toString('-') + "/book?level=2";
                requestBuilder.uri(URI.create(uriStr));

                HttpResponse<String> response = client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200) {
                    throw new RuntimeException("Failed to fetch order book: " + response.body());
                }

                JsonNode rootNode = OBJECT_MAPPER.readTree(response.body());
                Instant timestamp = Instant.now();

                List<OrderBookEntry> bidEntries = new ArrayList<>();
                List<OrderBookEntry> askEntries = new ArrayList<>();

                // Process bids
                JsonNode bidsNode = rootNode.get("bids");
                if (bidsNode != null && bidsNode.isArray()) {
                    for (JsonNode bid : bidsNode) {
                        double price = bid.get(0).asDouble();
                        double size = bid.get(1).asDouble();
                        bidEntries.add(new OrderBookEntry(price, size));
                    }
                }

                // Process asks
                JsonNode asksNode = rootNode.get("asks");
                if (asksNode != null && asksNode.isArray()) {
                    for (JsonNode ask : asksNode) {
                        double price = ask.get(0).asDouble();
                        double size = ask.get(1).asDouble();
                        askEntries.add(new OrderBookEntry(price, size));
                    }
                }

                // Create and complete the OrderBook result
                OrderBook orderBook = new OrderBook(timestamp, bidEntries, askEntries);
                futureResult.complete(List.of(orderBook));

            } catch (IOException | InterruptedException e) {
                futureResult.completeExceptionally(e);
            }
        });

        return futureResult;
    }


    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException {

        requestBuilder.uri(URI.create("%s/orders?product_id=%s".formatted(API_URL, tradePair.toString('-'))));
        List<Order> orders = new ArrayList<>();

        HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("coinbase response: %s".formatted(response.body()));
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode node = objectMapper.readTree(response.body());

        if (node.has("orders")) {
            for (JsonNode orderx : node.get("orders")) {
                Order order = objectMapper.readValue(orderx.asText(), Order.class);
                orders.add(order);
            }
        } else {
            logger.info("No orders found");
            return orders;
        }

        return orders;


    }

    @Override
    public ObservableList<Order> getOrders() throws IOException, InterruptedException, ExecutionException {

        requestBuilder.uri(URI.create("%s/orders".formatted(API_URL)));
        ObservableList<Order> orders = FXCollections.observableArrayList();
        HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("coinbase response: %s".formatted(response.body()));
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode node = objectMapper.readTree(response.body());

        if (node.has("orders")) {
            for (JsonNode orderx : node.get("orders")) {
                Order order = objectMapper.readValue(orderx.asText(), Order.class);
                orders.add(order);
            }


        } else {
            logger.info("No orders found");
            return orders;
        }



        return orders;

    }

    @Override
    public List<Position> getPositions() {
        return null;
    }
    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

        String urls= "%s://%s:%d".formatted(websocketURL, tradePair.toString('-'), 20);
        message =
                "{\"type\": \"subscribe\", \"channels\": [{\"name\": \"level2\",\"product_id\": \"%s\"}]}".formatted(tradePair.toString('-'));


    }


    @Override
    public void stopStreamLiveTrades(@NotNull TradePair tradePair) {

        String urls = websocketURL + "://" + tradePair.toString('-') + ":" + 20;
        message =
                "{\"type\": \"unsubscribe\", \"channels\": [{\"name\": \"level2\",\"product_id\": \"%s\"}]}".formatted(tradePair.toString('-'));


    }

    @Override
    public List<PriceData> streamLivePrices(@NotNull TradePair symbol) {
        String urls = websocketURL + "://" + symbol.toString('-') + ":" + 20;
        message = "{\"type\": \"subscribe\", \"channels\": [{\"name\": \"ticker\",\"product_id\": \"%s\"}]}".formatted(symbol);


        return Collections.emptyList();
    }

    @Override
    public List<CandleData> streamLiveCandlestick(@NotNull TradePair symbol, int intervalSeconds) {

        String urls = websocketURL + "://" + symbol + ":" + intervalSeconds;
        message =
                "{\"type\": \"subscribe\", \"channels\": [{\"name\": \"level2\",\"product_id\": \"%s\"}]}".formatted(symbol);


        return Collections.emptyList();

    }

    @Override
    public List<TradePair> getTradePairs() {

        requestBuilder.uri(URI.create("%s/products".formatted(API_URL)));

      ArrayList<TradePair> tradePairs = new ArrayList<>();
        try {

            HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();


            JsonNode res = OBJECT_MAPPER.readTree(response.body());
            logger.info("coinbase response:{}", (res));

            if (!res.has("id"))
                throw new IllegalStateException(res.asText());



            //coinbase response: [{"id":"DOGE-BTC","base_currency":"DOGE","quote_currency":"BTC","quote_increment":"0.00000001","base_increment":"0.1","display_name":"DOGE-BTC","min_market_funds":"0.000016","margin_enabled":false,"post_only":false,"limit_only":false,"cancel_only":false,"status":"online","status_message":"","trading_disabled":false,"fx_stablecoin":false,"max_slippage_percentage":"0.03000000","auction_mode":false,
            for (JsonNode rate : res) {
                org.investpro.Currency baseCurrency, counterCurrency;


                String fullDisplayName = rate.get("base_currency").asText();


                String shortDisplayName = rate.get("base_currency").asText();
                String code = rate.get("base_currency").asText();
                int fractionalDigits = 8;
                String symbol = rate.get("base_currency").asText();
                baseCurrency = new Currency(CurrencyType.CRYPTO, fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, symbol) {
                    /**
                     */
                    @Override
                    public int compareTo(@NotNull Currency o) {
                        return 0;
                    }

                    /**
                     * @param o
                     * @return
                     */
                    @Override
                    public int compareTo(java.util.@NotNull Currency o) {
                        return 0;
                    }
                };
                String fullDisplayName2 = rate.get("quote_currency").asText();
                String shortDisplayName2 = rate.get("quote_currency").asText();
                String code2 = rate.get("quote_currency").asText();
                int fractionalDigits2 = 8;
                String symbol2 = rate.get("quote_currency").asText();

                counterCurrency = new Currency(CurrencyType.CRYPTO,
                        fullDisplayName2, shortDisplayName2, code2, fractionalDigits2, symbol2
                        , symbol) {

                    @Override
                    public int compareTo(@NotNull Currency o) {
                        return 0;
                    }


                    @Override
                    public int compareTo(java.util.@NotNull Currency o) {
                        return 0;
                    }
                };


                TradePair tp = new TradePair(
                        baseCurrency, counterCurrency
                );
                tradePairs.add(tp);
                logger.info("coinbase trade pair: %s".formatted(tp));

                Currency.save(tp.getBaseCurrency());
                Currency.save(tp.getCounterCurrency());
            }



        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return tradePairs;



    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return true;
    }

    @Override
    public void cancelAllOrders() {


    }

    @Override
    public List<Deposit> Deposit() {
        return null;
    }

    @Override
    public List<Trade> getLiveTrades(List<TradePair> tradePairs) {
        return List.of();
    }


    @Override
    public double fetchLivesBidAsk(TradePair tradePair) {
        return 0;
    }

    @Override
    public CustomWebSocketClient getWebsocketClient() {
        return null;
    }

    @Override
    public List<Account> getAccountSummary() {
        return List.of();
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
    public List<Withdrawal> Withdraw() {
        return null;
    }

    /**
     * Checks whether the given secondsPerCandle is supported by Coinbase.
     *
     * @param secondsPerCandle the candle duration in seconds
     * @return true if the granularity is supported, false otherwise
     */
    public boolean isSupportedGranularity(int secondsPerCandle) {
        return SUPPORTED_GRANULARITY.contains(secondsPerCandle);
    }


    public int getSupportedGranularity(int secondsPerCandle) {

        // If the given secondsPerCandle is supported, return the exact granularity.
        if (isSupportedGranularity(secondsPerCandle)) {
            return secondsPerCandle;

        }

        return 0;
        // If the given secondsPerCandle is not supported, return the closest supported granularities.

    }
}

