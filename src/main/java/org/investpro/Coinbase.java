package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.beans.property.IntegerProperty;
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

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;

public  class Coinbase extends Exchange {
    ; // This argument is for creating a WebSocket client for live trading data.
    CustomWebSocketClient webSocketClient=new CustomWebSocketClient();

    String websocketURL="wss://advanced-trade-ws.coinbase.com";
    static  HttpClient client = HttpClient.newHttpClient();
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static final Logger logger = LoggerFactory.getLogger(Coinbase.class);
    public static final String API_URL = "https://api.coinbase.com/api/v3/brokerage";
    String apiKey;


    public Coinbase(String apikey, String apiSecret){
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
                "%s/accounts".formatted(API_URL)
        )) ;
        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString())
                ;
        if (response.statusCode()!= 200) {
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
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException {

        requestBuilder.uri(URI.create(
                "%s/orders".formatted(API_URL)
        )) ;
        requestBuilder.setHeader("Content-Type", "application/json");
        requestBuilder.setHeader("Authorization", "Bearer %s".formatted(apiKey));
        requestBuilder.method("POST", HttpRequest.BodyPublishers.ofString(OBJECT_MAPPER.writeValueAsString(new CreateOrderRequest(
                tradePair.toString('/'),
                side,
                orderType,
                price,
                size,timestamp
                ,
                stopLoss,
                takeProfit))))
                ;
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                ;
        if (response.statusCode()!= 200) {
            throw new RuntimeException("Error creating order: %d".formatted(response.statusCode()));
        }
        logger.info("Order created: {}", response.body());
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

        requestBuilder.uri(URI.create(
                API_URL + "/orders/" + orderId
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
        return CompletableFuture.completedFuture(orderId);
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
                String uriStr = API_URL;
                uriStr += "/products/" + tradePair.toString('-') + "/trades";

                if (i != 0) {
                    uriStr += "?after=" + afterCursor.get();
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
                                tradesBeforeStopTime.add(new Trade(tradePair,
                                        DefaultMoney.ofFiat(trade.get("price").asText(), tradePair.getCounterCurrency()),
                                        DefaultMoney.ofCrypto(trade.get("size").asText(), tradePair.getBaseCurrency()),
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
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
                currentCandleStartedAt, ZoneOffset.UTC));
        long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
        // Get the closest supported granularity to the ideal granularity.
        int actualGranularity = getSupportedGranularities(secondsPerCandle);


        return client.sendAsync(
                  requestBuilder
                                .uri(URI.create(String.format(
                                        "%s/products/%%s/candles?granularity=%%s&start=%%s".formatted(API_URL),
                                        tradePair.toString('-'), actualGranularity, startDateString)))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    logger.info("coinbase response: " + response);
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
                    double lowSoFar = Double.MAX_VALUE;
                    double volumeSoFar = 0;
                    double lastTradePrice = -1;
                    boolean foundFirst = false;
                    while (candleItr.hasNext()) {
                        currCandle = candleItr.next();
                        if (currCandle.get(0).asInt() < currentCandleStartedAt.getEpochSecond() ||
                                currCandle.get(0).asInt() >= currentCandleStartedAt.getEpochSecond() +
                                        secondsPerCandle) {
                            // skip this sub-candle if it is not in the parent candle's duration (this is just a
                            // sanity guard) TODO(mike): Consider making this a "break;" once we understand why
                            //  Coinbase is  not respecting start/end times
                            continue;
                        } else {
                            if (!foundFirst) {
                                // FIXME: Why are we only using the first sub-candle here?
                                currentTill = currCandle.get(0).asInt();
                                lastTradePrice = currCandle.get(4).asDouble();
                                foundFirst = true;
                            }
                        }

                        openPrice = currCandle.get(3).asDouble();

                        if (currCandle.get(2).asDouble() > highSoFar) {
                            highSoFar = currCandle.get(2).asDouble();
                        }

                        if (currCandle.get(1).asDouble() < lowSoFar) {
                            lowSoFar = currCandle.get(1).asDouble();
                        }

                        volumeSoFar += currCandle.get(5).asDouble();
                    }

                    int openTime = (int) (currentCandleStartedAt.toEpochMilli() / 1000L);

                    return Optional.of(new InProgressCandleData(openTime, openPrice, highSoFar, lowSoFar,
                            currentTill, lastTradePrice, volumeSoFar));
                });
    }

    @Override
    public List<Order> getPendingOrders() throws IOException, InterruptedException {
        return List.of();
    }

    @Override
    public CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) throws IOException, InterruptedException, ExecutionException {

        requestBuilder.uri(URI.create(API_URL + "/products/" + tradePair.toString('-') + "/book"));
        HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("coinbase response: " + response.body());
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

        requestBuilder.uri(URI.create("%s/orders?product_id=%s".formatted(API_URL, tradePair.toString('-'))));
        List<Order> orders = new ArrayList<>();

        HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("coinbase response: %s".formatted(response.body()));
        ObjectMapper objectMapper = new ObjectMapper();
        Order[] ordersArray = objectMapper.readValue(response.body(), Order[].class);
        Collections.addAll(orders, ordersArray);
        return orders;


    }

    @Override
    public ObservableList<Order> getOrders() throws IOException, InterruptedException {
        return null;
    }









    @Override
    public CompletableFuture<ArrayList<TradePair>> getTradePairs() {

        requestBuilder.uri(URI.create("%s/products".formatted(API_URL)));

      ArrayList<TradePair> tradePairs = new ArrayList<>();
        try {

            HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();


            JsonNode res = OBJECT_MAPPER.readTree(response.body());
            logger.info("coinbase response: %s".formatted(res));


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


                TradePair tp = new TradePair(
                        baseCurrency, counterCurrency
                );
                tradePairs.add(tp);
                logger.info("coinbase trade pair: %s".formatted(tp));


            }


        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return CompletableFuture.completedFuture(tradePairs);



    }
CustomWebSocketClient customWebSocketClient = new CustomWebSocketClient();
    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

        String urls= "%s://%s:%d".formatted(websocketURL, tradePair.toString('-'), 20);
        message =
                "{\"type\": \"subscribe\", \"channels\": [{\"name\": \"level2\",\"product_id\": \"%s\"}]}".formatted(tradePair.toString('-'));
        CompletableFuture<String> dat = webSocketClient.sendWebSocketRequest(urls, message);

        dat= customWebSocketClient.sendWebSocketRequest(urls, message);
        logger.info("WebSocket",dat);
    }


    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {

    }

    @Override
    public List<PriceData> streamLivePrices(@NotNull TradePair symbol) {
        return List.of();
    }

    @Override
    public List<CandleData> streamLiveCandlestick(@NotNull TradePair symbol, int intervalSeconds) {

        String urls= websocketURL+ "://"+symbol + ":"+intervalSeconds ;
        message =
                "{\"type\": \"subscribe\", \"channels\": [{\"name\": \"level2\",\"product_id\": \"%s\"}]}".formatted(symbol);
        CompletableFuture<String> dat = webSocketClient.sendWebSocketRequest(urls, message);
        logger.info("WebSocket",dat);
        return Collections.emptyList();

    }

    @Override
    public List<OrderBook> streamOrderBook(@NotNull TradePair tradePair) {
        return List.of();
    }

    @Override
    public CompletableFuture<String> cancelAllOrders() throws InvalidKeyException, NoSuchAlgorithmException, IOException {
        return null;
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return false;
    }

    @Override
    ArrayList<CryptoDeposit> getCryptosDeposit() throws IOException, InterruptedException {
        return null;
    }

    @Override
    ArrayList<CryptoWithdraw> getCryptosWithdraw() throws IOException, InterruptedException {
        return null;
    }


    // Set of supported granularities in seconds for Coinbase
    private static final Set<Integer> SUPPORTED_GRANULARITIES = new TreeSet<>(Set.of(
            60,     // 1 minute
            300,    // 5 minutes
            900,    // 15 minutes
            3600,   // 1 hour
            21600,  // 6 hours
            86400   // 1 day
    ));

    /**
     * Checks whether the given secondsPerCandle is supported by Coinbase.
     *
     * @param secondsPerCandle the candle duration in seconds
     * @return true if the granularity is supported, false otherwise
     */
    public boolean isSupportedGranularity(int secondsPerCandle) {
        return SUPPORTED_GRANULARITIES.contains(secondsPerCandle);
    }

    /**
     * Returns the set of supported granularities for Coinbase.
     *
     * @return Set of supported granularity values in seconds.
     */
    public int  getSupportedGranularities(int secondsPerCandle) {

        // If the given secondsPerCandle is supported, return the exact granularity.
        if (isSupportedGranularity(secondsPerCandle)) {
            return secondsPerCandle;

        }

        return 0;
        // If the given secondsPerCandle is not supported, return the closest supported granularities.

    }
}

