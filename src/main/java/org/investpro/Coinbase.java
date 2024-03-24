package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.java_websocket.drafts.Draft_6455;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.time.format.DateTimeFormatter.ISO_INSTANT;


class Coinbase extends Exchange {
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Logger logger = LoggerFactory.getLogger(Coinbase.class);
    CoinbaseWebSocketClient websocketClient;
    private TradePair tradePair;
    private String apiSecret;
    private String apiKey;
    HttpResponse<String> response;
    HttpRequest.Builder request = HttpRequest.newBuilder();
    HttpClient.Builder client = HttpClient.newBuilder();
    //
//    Accounts	Get a single account's holds	Get Account		Look for the hold object
    private JsonNode res;
    public Coinbase(String apiKey, String apiSecret) {
        super(apiKey, apiSecret);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.websocketClient = new CoinbaseWebSocketClient(URI.create("wss://ws-feed.pro.coinbase.com"), new Draft_6455());

//        CB-ACCESS-KEY API key as a string
//        CB-ACCESS-SIGN Message signature (see below)
//        CB-ACCESS-TIMESTAMP Timestamp for your req

        request.headers(
                "CB-ACCESS-KEY", apiKey);
        request.headers(
                "CB-ACCESS-SIGN", apiSecret);
        request.headers(
                "CB-ACCESS-TIMESTAMP", String.valueOf(new Date().getTime()));
        websocketClient.addHeader(
                "CB-ACCESS-KEY",
                apiKey
        );
        websocketClient.addHeader(
                "CB-ACCESS-SIGN",
                apiSecret
        );
        websocketClient.addHeader(
                "CB-ACCESS-TIMESTAMP", String.valueOf(new Date().getTime()));
        this.websocketClient.setTradePair(tradePair);


    }

    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public String getSignal() {
        return null;
    }

    @Override
    public void connect() {

    }



    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public CompletableFuture<String> createOrder(Orders order) throws JsonProcessingException {
        Objects.requireNonNull(order);

        String uriStr = "https://api.pro.coinbase.com/orders";

        String requestBody = OBJECT_MAPPER.writeValueAsString(order);
        request.uri(URI.create(uriStr))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        return new Server().getData("https://api.pro.coinbase.com/orders", "POST", request);
    }

    @Override
    public String getTimestamp() {
        return new Date().toString();
    }

    @Override
    public TradePair getSelecTradePair() {
        return tradePair;
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return null;
    }

    @Override
    Boolean isConnected() {
        return websocketClient.connectionEstablished.get();
    }

    public Account getAccount(String accountId) {

        String uriStr = STR."https://api.pro.coinbase.com/accounts/\{accountId}";

        request.uri(URI.create(uriStr));

        try {
            return OBJECT_MAPPER.readValue(client.build()
                    .send(request.GET().build(), HttpResponse.BodyHandlers.ofString())
                    .body(), Account.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

    }

    //    Coinbase accounts	Get all Coinbase wallets	List Accounts
//    Currencies	Get a currency	Get Product
//    Fees	Get fees	Get Transactions Summary
//    Orders	Get all fills	List Fills
//    Orders	Get all orders	List Orders
//    Orders	Cancel all orders	Cancel Orders		Add IDs of orders to cancel
//    Orders	Create a new order	Create Order
//    Orders	Get single order	Get Order
//    Orders	Cancel an order	Cancel Orders		Add ID of order to cancel
//    Products	Get all known trading pairs	List Products
//    Products	Get single product	Get Product
//    Products	Get product book	Get Product Book
//
//    WebSocket L2 channel
//    Products	Get product candles	Get Product Candles
//    Products	Get product stats	WebSocket Status channel
//    Products	Get product ticker	Get Product
//    Get Market Trades
//    Products	Get product trades	Get Market Trades
//    Accounts	Get a single account's ledger		Show an Account	Look for balance
//    Accounts	Get a single account's transfers		List Transactions	Look for "type": "transfer"
//    Address book	Get address book		Show Addresss
//    Coinbase accounts	Generate crypto address		Create Address
//    Currencies	Get all known currencies		Get Currencies
//    Transfers	Deposit from Coinbase account		Deposit Funds
//    Transfers	Deposit from payment method		Deposit Funds
//    Transfers	Get all payment methods		List Payment Methods
//    Transfers	Get all transfers		List Transactions	Look for "type": "transfer"
//    Transfers	Get a single transfer		Show a Transaction	Look for "type": "transfer"
//    Transfers	Withdraw to Coinbase account		Withdraw Funds
//    Transfers	Withdraw to crypto address		Send Money
//    Transfers	Withdraw to payment method		Withdraw Funds
//    Conversions	Convert currency	Create Convert Quote
//
//    Commit Convert Trade
//    Conversions	Get a conversion	Get Convert Trade
//    Transfers	Get fee estimate for crypto withdrawal
//    Coinbase price oracle	Get signed prices
//    Profiles	Get profiles	List Portfolios
//    Profiles	Create a profile	Create Portfolio
//    Profiles	Transfer funds between profiles	Move Portfolio Funds
//    Profiles	Get profile by id	Get Portfolio Breakdown
//    Profiles	Rename a profile	Edit Portfolio
//    Profiles	Delete a profile	Delete Portfolio
//    Reports	Get all reports	n/a
//    Reports	Create a report	n/a
//    Reports	Get a report	n/a
//    Users	Get user exchange limits	n/a
//    Wrapped assets	Get all wrapped assets
//    Wrapped assets	Get conversion rate of wrapped asset
    @Override
    public CompletableFuture<String> cancelOrder(String orderId) {
        Objects.requireNonNull(orderId);

        String uriStr = STR."https://api.pro.coinbase.com/orders/\{orderId}";

        request
                .uri(URI.create(uriStr))
                .header("Content-Type", "application/json")
                .DELETE()
                .build();

        return new Server().getData("https://api.pro.coinbase.com/orders/" + orderId, "DELETE", request);
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
            // FIXME: We need to address this!
            for (int i = 0; !futureResult.isDone(); i++) {
                String uriStr = "https://api.pro.coinbase.com/";
                uriStr += STR."products/\{tradePair.toString('-')}/trades";

                if (i != 0) {
                    uriStr += STR."?after=\{afterCursor.get()}";
                    logger.info(STR."uriStr: \{uriStr}");
                }

                try {
                    HttpResponse<String> response = HttpClient.newHttpClient().send(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(uriStr))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofString());

                    logger.info(STR."response headers: \{response.headers()}");
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
                                logger.info(STR."tradesBeforeStopTime: \{tradesBeforeStopTime}");
                                logger.info(STR."time: \{time}");
                                logger.info(STR."price: \{trade.get("price").asText()}");
                                logger.info(STR."size: \{trade.get("size").asText()}");
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
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
                currentCandleStartedAt, ZoneOffset.UTC));
        long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
        // Get the closest supported granularity to the ideal granularity.
        int actualGranularity = getCandleDataSupplier(secondsPerCandle, tradePair).getSupportedGranularities().stream()
                .min(Comparator.comparingInt(i -> (int) Math.abs(i - idealGranularity)))
                .orElseThrow(() -> new NoSuchElementException("Supported granularity was empty!"));
        // TODO: If actualGranularity = secondsPerCandle there are no sub-candles to fetch and we must get all the
        //  data for the current live syncing candle from the raw trades method.
        return client.build().sendAsync(
                        HttpRequest.newBuilder()
                                .uri(URI.create(String.format(
                                        "https://api.pro.coinbase.com/products/%s/candles?granularity=%s&start=%s",
                                        tradePair.toString('-'), actualGranularity, startDateString)))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    logger.info(STR."coinbase response: \{response}");

                    if (response == null || response.isEmpty()) {
                        new Message("ERROR", STR."\{tradePair.toString('-')} candles response was empty");
                        return Optional.empty();
                    }

                    try {
                        res = OBJECT_MAPPER.readTree(response);
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }

                    if (res.has("message") && res.get("message").asText().equals("not_found")) {

                            new Message("ERROR", tradePair.toString('-'));
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
                            logger.info(STR."currCandle: \{currCandle}");
                            continue;
                        } else {
                            if (!foundFirst) {
                                currentTill = currCandle.get(0).asInt();
                                lastTradePrice = currCandle.get(4).asDouble();
                                foundFirst = true;
                            } else if (currCandle.get(4).asDouble() > lastTradePrice) {

                                currentTill = currCandle.get(0).asInt();
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
    public List<TradePair> getTradePairSymbol() {

        //COINBASE URL TO GET ALL TRADES PAIRS
        String url = "https://api.pro.coinbase.com/products";


        request.uri(URI.create(url));

        ArrayList<TradePair> tradePairs = new ArrayList<>();
        try {

            response = new Server().client.sendAsync(request.GET().build(), HttpResponse.BodyHandlers.ofString()).get();



                res = OBJECT_MAPPER.readTree(response.body());
                logger.info(STR."coinbase response: \{res}");

                //coinbase response: [{"id":"DOGE-BTC","base_currency":"DOGE","quote_currency":"BTC","quote_increment":"0.00000001","base_increment":"0.1","display_name":"DOGE-BTC","min_market_funds":"0.000016","margin_enabled":false,"post_only":false,"limit_only":false,"cancel_only":false,"status":"online","status_message":"","trading_disabled":false,"fx_stablecoin":false,"max_slippage_percentage":"0.03000000","auction_mode":false,
                JsonNode rates = res;
                for (JsonNode rate : rates) {
                    CryptoCurrency baseCurrency, counterCurrency;


                    String fullDisplayName = rate.get("base_currency").asText();


                    String shortDisplayName = rate.get("base_currency").asText();
                    String code = rate.get("base_currency").asText();
                    int fractionalDigits = rate.get("base_increment").asInt();
                    String symbol = rate.get("base_currency").asText();
                    baseCurrency = new CryptoCurrency(fullDisplayName, shortDisplayName, code, fractionalDigits, symbol, code);
                    String fullDisplayName2 = rate.get("quote_currency").asText();
                    String shortDisplayName2 = rate.get("quote_currency").asText();
                    String code2 = rate.get("quote_currency").asText();
                    int fractionalDigits2 = rate.get("quote_increment").asInt();
                    String symbol2 = rate.get("quote_currency").asText();

                    counterCurrency = new CryptoCurrency(
                            fullDisplayName2, shortDisplayName2, code2, fractionalDigits2, symbol2,
                            code2

                    );


                    TradePair tp = new TradePair(
                            baseCurrency, counterCurrency
                    );
                    tradePairs.add(tp);

                    logger.info(Currency.getAvailableCurrencies().toString());
                }
        } catch (JsonProcessingException | ClassNotFoundException | SQLException | ExecutionException |
                 InterruptedException ex) {
                throw new RuntimeException(ex);
            }


        return tradePairs;

    }

    @Override
    public CompletableFuture<String> getOrderBook(TradePair tradePair) {
        Objects.requireNonNull(tradePair);

        String uriStr = STR."https://api.pro.coinbase.com/products/\{tradePair.toString('-')}/book?level=1";

        request.uri(URI.create(uriStr))
                .header("Content-Type", "application/json")
                .GET()
                .build();

        return client.build().sendAsync(request.uri(
                        URI.create(uriStr)
                ).GET().build(), HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body);
    }


    @Override
    public Ticker getLivePrice(TradePair tradePair) {
        Objects.requireNonNull(tradePair);

        String uriStr = STR."https://api.pro.coinbase.com/products/\{tradePair.toString('-')}/ticker";


        request.uri(URI.create(uriStr));
        try {
            res = OBJECT_MAPPER.readTree(client.build()
                    .sendAsync(request.GET().build(), HttpResponse.BodyHandlers.ofString()).get(5000, TimeUnit.MILLISECONDS).body());

            Ticker ticker = new Ticker();

            for (JsonNode rate : res) {


                ticker.setBidPrice(rate.get("bid").asDouble());
                ticker.setAskPrice(rate.get("ask").asDouble());
                ticker.setVolume(rate.get("volume").asDouble());
                ticker.setTimestamp(rate.get("timestamp").asLong() * 1000L);
                logger.info(
                        "bid: {ticker.getBidPrice()}, ask: {ticker.getAskPrice()}, volume: {ticker.getVolume()}, timestamp: {ticker.getTimestamp()}"
                );
            }
            return ticker;

        } catch (IOException | InterruptedException | ExecutionException | TimeoutException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public Account getUserAccountDetails() {
        String uriStr = "https://api.pro.coinbase.com/accounts";
        request.uri(URI.create(uriStr));


        try {
            return OBJECT_MAPPER.readValue(client.build()
                    .send(request.GET().build(), HttpResponse.BodyHandlers.ofString())
                    .body(), Account.class);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    // Existing code


    @Override
    public double getSize() {
        return 0;
    }

    @Override
    public double getLivePrice() {
        return 0;
    }

    @Override
    public String getName() {
        return "Coinbase";
    }

    public TradePair getTradePair() {
        return tradePair;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }

}