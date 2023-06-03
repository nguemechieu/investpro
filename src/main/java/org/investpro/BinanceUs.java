package org.investpro;


import com.fasterxml.jackson.core.JsonProcessingException;
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
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.nanoTime;
import static java.lang.System.out;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.investpro.UsersManager.alert;

public class BinanceUs extends Exchange {

    private static final Logger logger = LoggerFactory.getLogger(BinanceUs.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    static TradePair tradePair;
    private final HttpClient client = HttpClient.newHttpClient();
    private static boolean isConnected;
    private String api_key;
    private String accountId;
    private final AtomicReference<String> url = new AtomicReference<>("https://api.binance.us/api/v3/");
    private String apiSecret;
    private String symbol;
    private double price;

    public BinanceUs(String apiKey, String apiSecret) {
        super(binanceUsWebSocket(apiKey,apiSecret));


        this.api_key = apiKey;
        this.apiSecret = apiSecret;


        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            logger.error("BinanceUs " + nanoTime());
            logger.error(e.getMessage());
            e.printStackTrace();
        });
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36");
        requestBuilder.header("Origin", "https://api.binance.us");
        requestBuilder.header("Referer", "https://api.binance.us");





        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("X-MBX-APIKEY", "Bearer " + apiKey);
        requestBuilder.header("X-MBX-APISECRET", apiSecret);
        requestBuilder.header("Sec-Fetch-Site", "same-origin");
        requestBuilder.header("Sec-Fetch-User", "?1");

        requestBuilder.header("Accept-Language", "en-US,en;q=0.9");

        logger.info("BinanceUs " + nanoTime());
        this.api_key = apiKey;
        isConnected = false;




    }

    private static ExchangeWebSocketClient binanceUsWebSocket(String apiKey, String apiSecret) {

        BinanceWebSocket binanceUsWebSocket = new BinanceWebSocket(apiKey);

        binanceUsWebSocket.addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0;)");
        binanceUsWebSocket.addHeader("Origin", "https://api.binance.us");
        binanceUsWebSocket.addHeader("Referer", "https://api.binance.us");
        binanceUsWebSocket.addHeader("Sec-Fetch-Dest", "empty");
        binanceUsWebSocket.addHeader("Sec-Fetch-Mode", "cors");
        binanceUsWebSocket.addHeader("Accept", "application/json");
        binanceUsWebSocket.addHeader(
                "X-MBX-APIKEY", "Bearer " + apiKey
        );
        binanceUsWebSocket.addHeader("X-MBX-APISECRET", apiSecret);
        binanceUsWebSocket.addHeader("Sec-Fetch-Site", "same-origin");
        binanceUsWebSocket.addHeader("Sec-Fetch-User", "?1");


        binanceUsWebSocket.connect();
        return binanceUsWebSocket;
    }


    //api/v3/account
    public @NotNull List<Account> getAccount() throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "https://api.binance.us/api/v3/account"
        ));
        requestBuilder.GET();

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.info("BinanceUs " + nanoTime());
        if (response.statusCode() == 200) {
            {
//                "makerCommission":15,
//                    "takerCommission":15,
//                    "buyerCommission":0,
//                    "sellerCommission":0,
//                    "commissionRates":{
//                "maker":"0.00150000",
//                        "taker":"0.00150000",
//                        "buyer":"0.00000000",
//                        "seller":"0.00000000"
//            },
//                "canTrade":true,
//                    "canWithdraw":true,
//                    "canDeposit":true,
//                    "brokered":false,
//                    "requireSelfTradePrevention":false,
//                    "updateTime":123456789,
//                    "accountType":"SPOT",
//                    "balances":[
//                {
//                    "asset":"BTC",
//                        "free":"4723846.89208129",
//                        "locked":"0.00000000"
//                },
//                {
//                    "asset":"LTC",
//                        "free":"4763368.68006011",
//                        "locked":"0.00000000"
//                }
//    ],
//                "permissions":[
//                "SPOT"
//    ]
                JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body());
                Account account = new Account();
                account.setBalance(jsonNode.get("balances").get(0).get("free").asDouble());
                account.setAsset(jsonNode.get("balances").get(0).get("asset").asText());
                account.setCanTrade(jsonNode.get("canTrade").asBoolean());
                account.setCanWithdraw(jsonNode.get("canWithdraw").asBoolean());
                account.setCanDeposit(jsonNode.get("canDeposit").asBoolean());
                account.setBrokered(jsonNode.get("brokered").asBoolean());
                account.setRequireSelfTradePrevention(jsonNode.get("requireSelfTradePrevention").asBoolean());
                account.setUpdateTime(jsonNode.get("updateTime").asLong());
                account.setAccountType(jsonNode.get("accountType").asText());
                account.setCommissionRates(jsonNode.get("commissionRates").get("maker").asDouble(),
                        jsonNode.get("commissionRates").get("taker").asDouble(),
                        jsonNode.get("commissionRates").get("buyer").asDouble(),
                        jsonNode.get("commissionRates").get("seller").asDouble());
                return account;


            }
        } else {
            logger.error("BinanceUs " + nanoTime());
            logger.error("BinanceUs " + response.statusCode());
            logger.error("BinanceUs " + response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error");
            alert.setContentText(response.body());
            alert.showAndWait();
        }
        return
                null;
    }

    //GET /sapi/v1/asset/query/trading-fee
    public double getTradingFee() throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "https://api.binance.us/sapi/v1/asset/query/trading-fee"
        ));
        requestBuilder.GET();
        logger.info("BinanceUs " + nanoTime());
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.info("BinanceUs " + nanoTime());

        if (response.statusCode() != 200) {
            logger.error("BinanceUs " + nanoTime());
            logger.error("BinanceUs " + response.statusCode());
            logger.error("BinanceUs " + response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error");
            alert.setContentText(response.body());
            alert.showAndWait();
        }
//        [
//        {
//            "symbol": "1INCHUSD",
//                "makerCommission": "0.004",
//                "takerCommission": "0.006"
//        },
//        {
//            "symbol": "1INCHUSDT",
//                "makerCommission": "0.004",
//                "takerCommission": "0.006"
//        }
//]
        JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body());

        String symbol = jsonNode.get(0).get("symbol").asText();
        double makerCommission = jsonNode.get(0).get("makerCommission").asDouble();
        double takerCommission = jsonNode.get(0).get("takerCommission").asDouble();


        logger.info("BinanceUs " + nanoTime());
        logger.info("BinanceUs " + response.statusCode());
        logger.info("BinanceUs " + response.body());
        logger.info("BinanceUs " + symbol);
        logger.info("BinanceUs " + makerCommission);
        logger.info("BinanceUs " + takerCommission);
        return makerCommission + takerCommission;

    }

    // "GET" "$api_url/api/v3/order?orderId=$orderId&symbol=$symbol&timestamp=$timestamp&signature=$signature" \
    //     -H "X-MBX-APIKEY: $api_key"
    public List<Order> getOrder(@NotNull TradePair tradePair, String orderId) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create("https://api.binance.us/api/v3/order/symbol=?" + tradePair.toString('/')));
        requestBuilder.GET();
        logger.info("BinanceUs " + nanoTime());
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.info("BinanceUs " + nanoTime());
        logger.info("BinanceUs " + response.statusCode());
        logger.info("BinanceUs " + response.body());
        logger.info("BinanceUs " + orderId);
        if (response.statusCode() != 200) {
            logger.error("BinanceUs " + nanoTime());
            logger.error("BinanceUs " + response.statusCode());
            logger.error("BinanceUs " + response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error");
            alert.setContentText(response.body());
            alert.showAndWait();
        }
        JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body());


        ArrayList<Order> orders = new ArrayList<>();
        String symbol, orderListId;
        for (int i = 0; i < jsonNode.size(); i++) {
            if (jsonNode.get(i).has("symbol")) {
                symbol = jsonNode.get(i).get("symbol").asText();
                orderId = jsonNode.get(i).get("symbol").get("orderId").asText();
                orderListId = String.valueOf(jsonNode.get(i).get("symbol").get("orderListId"));
                logger.info(

                        symbol
                        ,
                        orderId,
                        orderListId
                );

            } else if (jsonNode.get(i).has("clientOrderId")) {

//                "symbol": "LTCBTC",
//                        "orderId": 1,
//                        "orderListId": -1 //Unless part of an OCO, the value will always be -1.
//                "clientOrderId": "myOrder1",
//                        "price": "0.1",
//                        "origQty": "1.0",
//                        "executedQty": "0.0",
//                        "cummulativeQuoteQty": "0.0",
//                        "status": "NEW",
//                        "timeInForce": "GTC",
//                        "type": "LIMIT",
//                        "side": "BUY",
//                        "stopPrice": "0.0",
//                        "icebergQty": "0.0",
//                        "time": 1499827319559,
//                        "updateTime": 1499827319559,
//                        "isWorking": true,
//                        "origQuoteOrderQty": "0.000000",
//                        "workingTime":1507725176595,
//                        "selfTradePreventionMode": "NONE"
//            }

                symbol = jsonNode.get(i).get("symbol").asText();
                orderId = jsonNode.get(i).get("clientOrderId").asText();
                orderListId = String.valueOf(jsonNode.get(i).get("clientOrderId").get("orderListId"));
                String price = jsonNode.get(i).get("price").asText();
                String origQty = jsonNode.get(i).get("origQty").asText();

                String executedQty = jsonNode.get(i).get("executedQty").asText();
                String cummulativeQuoteQty = jsonNode.get(i).get("cummulativeQuoteQty").asText();
                String status = jsonNode.get(i).get("status").asText();
                String timeInForce = jsonNode.get(i).get("timeInForce").asText();
                String type = jsonNode.get(i).get("type").asText();
                String side = jsonNode.get(i).get("side").asText();
                String stopPrice = jsonNode.get(i).get("stopPrice").asText();
                String icebergQty = jsonNode.get(i).get("icebergQty").asText();
                String time = String.valueOf(jsonNode.get(i).get("time").asLong());
                String updateTime = String.valueOf(jsonNode.get(i).get("updateTime").asLong());
                String isWorking = String.valueOf(jsonNode.get(i).get("isWorking").asBoolean());
                String origQuoteOrderQty = jsonNode.get(i).get("origQuoteOrderQty").asText();
                String workingTime = String.valueOf(jsonNode.get(i).get("workingTime").asLong());
                String selfTradePreventionMode = jsonNode.get(i).get("selfTradePreventionMode").asText();


                Order order = new Order(symbol, orderId, orderListId,
                        price, origQty, executedQty, cummulativeQuoteQty, status, timeInForce, type, side, stopPrice, icebergQty, time, updateTime, isWorking, origQuoteOrderQty, workingTime, selfTradePreventionMode);

                orders.add(order);
            }
        }
        logger.info("BinanceUs " + nanoTime());
        logger.info("BinanceUs " + response.statusCode());
        logger.info("BinanceUs " + response.body());

        return orders;
    }

    //    Get Trades
//    Example
//
//# Get HMAC SHA256 signature
//
//    timestamp=`date +%s000`
//
//    api_key=<your_api_key>
//    secret_key=<your_secret_key>
//
//    api_url="https://api.binance.us"
//
//    signature=`echo -n "symbol=BNBBTC&timestamp=$timestamp" | openssl dgst -sha256 -hmac $secret_key`
//
//    curl -X "GET" "$api_url/api/v3/myTrades?symbol=BNBBTC&timestamp=$timestamp&signature=$signature" \
//            -H "X-MBX-APIKEY: $api_key"
//    Response
//
//[
//    {
//        "symbol": "BNBBTC",
//            "id": 28457,
//            "orderId": 100234,
//            "orderListId": -1,
//            "price": "4.00000100",
//            "qty": "12.00000000",
//            "quoteQty": "48.000012",
//            "commission": "10.10000000",
//            "commissionAsset": "BNB",
//            "time": 1499865549590,
//            "isBuyer": true,
//            "isMaker": false,
//            "isBestMatch": true
//    }
//]
//    GET /api/v3/myTrades (HMAC SHA256)
    public List<Trade> getTrades(@NotNull TradePair tradePair) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "https://api.binance.us/api/v3/myTrades?symbol=" + tradePair.toString('/')
        ));
        requestBuilder.GET();

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.info("BinanceUs " + nanoTime());
        logger.info("BinanceUs " + response.statusCode());
        logger.info("BinanceUs " + response.body());
        logger.info("BinanceUs " + tradePair);

        List<Trade> trades = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        JsonNode jsonNode = mapper.readTree(response.body());
        for (int i = 0; i < jsonNode.size(); i++) {
            Trade trade = new Trade(jsonNode.get(i).get("symbol").asText(),
                    jsonNode.get(i).get("id").asLong(),
                    jsonNode.get(i).get("orderId").asLong(),
                    jsonNode.get(i).get("orderListId").asLong(),
                    jsonNode.get(i).get("price").asText(),
                    jsonNode.get(i).get("qty").asText(),
                    jsonNode.get(i).get("quoteQty").asText(),
                    jsonNode.get(i).get("commission").asText(),
                    jsonNode.get(i).get("commissionAsset").asText(),
                    jsonNode.get(i).get("time").asLong(),
                    jsonNode.get(i).get("isBuyer").asBoolean(),
                    jsonNode.get(i).get("isMaker").asBoolean(),
                    jsonNode.get(i).get("isBestMatch").asBoolean());
            trades.add(trade);
        }
        return trades;
    }

    public void cancelOrder(@NotNull TradePair tradePair, long orderId) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "https://api.binance.us/api/v3/order/symbol=?" + tradePair.toString('/')
        ));
        requestBuilder.DELETE();
        logger.info("BinanceUs " + nanoTime());

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.info("BinanceUs " + nanoTime());
        logger.info("BinanceUs " + response.statusCode());
        logger.info("BinanceUs " + response.body());
        logger.info("BinanceUs " + orderId);
    }

//    Cancel Open Orders for Symbol
//            Example
//
//# Get HMAC SHA256 signature
//
//    timestamp=`date +%s000`
//
//    api_key=<your_api_key>
//    secret_key=<your_secret_key>
//    symbol=<symbol>
//
//    api_url="https://api.binance.us"
//
//    signature=`echo -n "symbol=$symbol&timestamp=$timestamp" | openssl dgst -sha256 -hmac $secret_key`
//
//    curl -X "DELETE" "$api_url/api/v3/openOrders?symbol=$symbol&timestamp=$timestamp&signature=$signature" \
//            -H "X-MBX-APIKEY: $api_key"
//    Response
//
//[
//    {
//        "symbol": "BTCUSDT",
//            "origClientOrderId": "KZJijIsAFf5BU5oxkWTAU3",
//            "orderId": 0,
//            "orderListId": -1,
//            "clientOrderId": "8epSIUMvNCknntXcSzWs7H",
//            "price": "0.10000000",
//            "origQty": "1.00000000",
//            "executedQty": "0.00000000",
//            "cummulativeQuoteQty": "0.00000000",
//            "status": "CANCELED",
//            "timeInForce": "GTC",
//            "type": "LIMIT",
//            "side": "BUY"
//    }
//]
//    DELETE /api/v3/openOrders (HMAC SHA256)

    public void cancelOpenOrder(@NotNull TradePair tradePair, long orderId) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "https://api.binance.us/api/v3/openOrders?symbol=?" + tradePair.toString('/')
        ));
        requestBuilder.DELETE();
        logger.info("BinanceUs " + nanoTime());
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.info("BinanceUs " + nanoTime());

        if (response.statusCode() != 200) {
            logger.error("BinanceUs " + nanoTime());
            logger.error("BinanceUs " + response.statusCode());
            logger.error("BinanceUs " + response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error");
            alert.setContentText(response.body());
            alert.showAndWait();
        } else {
            logger.info("BinanceUs " + nanoTime());
            logger.info("BinanceUs " + response.statusCode());
            logger.info("BinanceUs " + response.body());
            logger.info("BinanceUs " + orderId);

            ObjectMapper mapper = new ObjectMapper();
            JsonNode jsonNode = mapper.readTree(response.body());
            ArrayNode arrayNode = (ArrayNode) jsonNode;


        }

    }


    public void createOrder(@NotNull TradePair tradePair, double price, @NotNull ENUM_ORDER_TYPE type, @NotNull Side side, double quantity, double stopLoss1, double takeProfit1) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "https://api.binance.us/api/v3/order/symbol=?" + tradePair.toString('/')
        ));
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                "{\"symbol\":\"" + tradePair.toString('/') + "\",\"side\":\"" + side + "\",\"type\":\"" + type + "\",\"timeInForce\":\"GTC\",\"quantity\":\"" + quantity + "\",\"price\":\"" + price + "\",\"stopPrice\":\"" + stopLoss1 + "\",\"takeProfit\":\"" + takeProfit1 + "\"}"
        ));
        logger.info("BinanceUs " + nanoTime());
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.info("BinanceUs " + nanoTime());


        if (response.statusCode() != 200) {
            logger.error("BinanceUs " + nanoTime());
            logger.error("BinanceUs " + response.statusCode());
            logger.error("BinanceUs " + response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("Error");
            alert.setContentText(response.body());
            alert.showAndWait();
        }else {
            logger.info("BinanceUs " + nanoTime());
            logger.info("BinanceUs " + response.statusCode());
            logger.info("BinanceUs " + response.body());

            JsonNode jsonNode = OBJECT_MAPPER.readTree(response.body());
            logger.info("BinanceUs " + nanoTime());
            logger.info("BinanceUs " + jsonNode.toString());

        }




    }

    @Override
    public String getName() {
        return
                "BinanceUs";

        }

    @Override
    public BinanceUsCandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair) {
            @Override
            public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
                return null;
            }

            @Override
            public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
                return null;
            }

            @Override
            public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
                return null;
            }
        };


    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle() {
        return null;
    }


//    private @Nullable String timestampSignature(
//            String apiKey,
//            String passphrase
//    ) {
//        Objects.requireNonNull(apiKey);
//        Objects.requireNonNull(passphrase);
//
//        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_INSTANT);
//        String stringToSign = timestamp + "\n" + apiKey + "\n" + passphrase;
//
//        try {
//            byte[] hash = MessageDigest.getInstance("SHA-256").digest(stringToSign.getBytes());
//            return Base64.getEncoder().encodeToString(hash);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }
//
//
//    }


    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return
                new BinanceUsWebSocket(tradePair) ;


    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        return
                new HashSet<>(Arrays.asList(
                        60, 60 * 5, 60 * 30, 3600, 3600 * 2, 3600 * 4,
                        3600 * 6, 3600 * 12, 3600 * 24, 3600 * 24 * 7, 3600 * 24 * 7 * 4,
                        3600 * 24 * 7 * 4 * 12
                ));

    }

    /**
     * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
     * <p>
     * This method only needs to be implemented to support live syncing.
     */
    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt, boolean isAutoTrading) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);

        if (stopAt.isAfter(Instant.now())) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            IntegerProperty afterCursor = new SimpleIntegerProperty(0);
            List<Trade> tradesBeforeStopTime = new ArrayList<>();

            // For Public Endpoints, our rate limit is 3 requests per second, up to 6 requests per second in
            // burst.
            // We will know if we get rate limited if we get a 429 response code.
            for (int i = 0; !futureResult.isDone(); i++) {
                String uriStr = "https://api.binance.us/api/v3/";
                uriStr += "trades?symbol=" + tradePair.toString('/');

                if (i != 0) {
                    uriStr += "?after=" + afterCursor.get();
                }
                requestBuilder.uri(URI.create(uriStr));
                requestBuilder.GET();
                try {
                    HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
                    Log.info("response headers-->: ", response.toString());
                    if (response.headers().firstValue("date").isEmpty()) {
                        futureResult.completeExceptionally(new RuntimeException("cryptoinvestor.cryptoinvestor.CurrencyDataProvider.BinanceUs trades response did not contain header \"date\": " + response.body()));

                    }
                    afterCursor.setValue(Integer.valueOf((response.headers().firstValue("CB-AFTER").get())));
                    JsonNode tradesResponse = OBJECT_MAPPER.readTree(response.body());
                    if (!tradesResponse.isArray()) {
                        futureResult.completeExceptionally(new RuntimeException(
                                "cryptoinvestor.cryptoinvestor.CurrencyDataProvider.BinanceUs trades response was not an array!"));
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
                                        trade.get("p").asDouble(),

                                        trade.get("q").asDouble(),

                                        Side.getSide(trade.get("S").asText()), trade.at("E").asLong(),
                                        Date.from(Instant.from(ISO_INSTANT.parse(trade.get("t").asText()))).getTime()));
                            }
                        }
                    }
                } catch (IOException | InterruptedException ex) {
                    Log.error("ex: " + ex);
                    futureResult.completeExceptionally(ex);
                } catch (ParseException | URISyntaxException e) {
                    throw new RuntimeException(e);
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
        // Get the closest supported granularity to the ideal granularity.


        String endDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .format(LocalDateTime.ofEpochSecond(new Date().getTime(), 0, ZoneOffset.UTC));

        Log.info("Start date: " + startDateString, "");
        Log.info("End date: " + endDateString, "");
        Log.info("TradePair " + tradePair, "");
        Log.info("Second per Candle: " + secondsPerCandle, "");
        String x;
        String str;
        if (secondsPerCandle < 3600) {
            x = String.valueOf(secondsPerCandle / 60);
            str = "m";
        } else if (secondsPerCandle < 86400) {
            x = String.valueOf((secondsPerCandle / 3600));
            str = "h";
        } else if (secondsPerCandle < 604800) {
            x = String.valueOf(secondsPerCandle / 86400);
            str = "d";
        } else if (secondsPerCandle < 2592000) {
            x = String.valueOf((secondsPerCandle / 604800));
            str = "w";
        } else {
            x = String.valueOf((secondsPerCandle * 7 / 2592000 / 7));
            str = "M";
        }
        String timeFrame = x + str;

        return client.sendAsync(
                        requestBuilder
                                .uri(URI.create(
                                        "https://api.binance.us/api/v3/klines?symbol=" + tradePair.toString('/') + "&interval=" + timeFrame
                                ))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    Log.info("BinanceUs response: ", response);
                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                        logger.info(response);
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


    private @NotNull JSONObject getJSON() {

        JSONObject jsonObject = new JSONObject();
        try {
            URI url = URI.create("https://api.binance.us/api/v2/exchange-rates");
            HttpsURLConnection conn = (HttpsURLConnection) url.toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Accept-Charset", "utf-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10)");
            conn.setRequestProperty("CB-ACCESS-KEY", api_key);//    API key as a string
            String timestamp = new Date().toString();

            conn.setRequestProperty("CB-ACCESS-SIGN", timestamp + "GET" + url);
            //"base64-encoded signature (see Signing a Message)");
            conn.setRequestProperty("CB-ACCESS-TIMESTAMP", new Date().toString());//    Timestamp for your request


            conn.connect();
            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);

            }
            in.close();

            out.println(response);
            //Put data into json file
            jsonObject = new JSONObject(response.toString());
            out.println(jsonObject.toString(4));

            String rates;
            if (jsonObject.has("data")) {
                JSONObject dat = new JSONObject(jsonObject.getJSONObject("data").toString(4));
                if (dat.has("rates")) {
                    rates = dat.getJSONObject("rates").toString(4);
                    out.println(rates);
                }

            }


        } catch (IOException e) {
            e.printStackTrace();
        }
        out.println(jsonObject.toString(4));
        return jsonObject;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {


        System.out.println("Connected");
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4) + " " + handshake);

    }

    @Override
    public void onMessage(String message) {
        System.out.println(message);
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        System.out.println("Disconnected");
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

    }

    @Override
    public void onError(Exception ex) {
        System.out.println("Error");
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

    }

    @Override
    public String getSymbol() {
        return symbol;
    }

    @Override
    public double getLivePrice(TradePair tradePair) {
        return 0;

    }


    @Override
    public ArrayList<Double> getVolume() {
        return null;
    }

    @Override
    public String getOpen() {
        return null;
    }

    @Override
    public String getHigh() {
        return null;
    }

    @Override
    public String getLow() {
        return null;
    }

    @Override
    public String getClose() {
        return null;
    }

    @Override
    public String getTimestamp() {
        return null;
    }

    @Override
    public String getTradeId() {
        return null;
    }

    @Override
    public String getOrderId() {
        return null;
    }

    @Override
    public String getTradeType() {
        return null;
    }

    @Override
    public String getSide() {
        return null;
    }

    @Override
    public String getExchange() {
        return null;
    }

    @Override
    public String getCurrency() {
        return null;
    }

    @Override
    public String getAmount() {
        return null;
    }

    @Override
    public String getFee() {
        return null;
    }

    @Override
    public String getAvailable() {
        return null;
    }

    @Override
    public String getBalance() {
        return null;
    }

    @Override
    public String getPending() {
        return null;
    }

    @Override
    public String getTotal() {
        return null;
    }

    @Override
    public String getDeposit() {
        return null;
    }

    @Override
    public String getWithdraw() {
        return null;
    }

    @Override
    public void deposit(Double value) {

    }

    @Override
    public void withdraw(Double value) {

    }


    public void createOrder(
            @NotNull TradePair tradePair, TRADE_ORDER_TYPE orderType,
            Side side,
            int size,
            double price,
            double stopPrice,
            double takeProfit
    ) {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.binance.us/" +
                "api/v3/orders/" + tradePair.toString('/') +
                "?side=" + side +
                "&type=" +orderType+
                "&quantity=" + size +
                "&price=" + price + "&stopLoss=" + stopPrice + "&takeProfit=" + takeProfit;

        System.out.println(uriStr);


    }

    public void CancelOrder(long orderID) {
    }

    public void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE market, double quantity, int i, @NotNull Date timestamp, long orderID, double stopPrice, double takeProfitPrice) throws IOException, InterruptedException {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.binance.us/" +
                "api/v3/orders/" + tradePair.toString('/') +
                "?side=" + buy +
                "&type=" + market +
                "&quantity=" + quantity +
                "&timestamp=" + timestamp +
                "&orderId=" + orderID +
                "&stopPrice=" + stopPrice +
                "&takeProfitPrice=" + takeProfitPrice;

        System.out.println(uriStr);

        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                uriStr

        ));
        //requestBuilder.POST(HttpRequest.BodyPublishers.ofString())
        String body =
                requestBuilder.build().method();
        client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        ).request().expectContinue();

        System.out.println(body);

    }

    public void closeAll() throws IOException, InterruptedException {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.binance.us/" +
                "api/v3/orders";

        System.out.println(uriStr);

        requestBuilder.DELETE();
        //requestBuilder.POST(HttpRequest.BodyPublishers.ofString())
        String body =
                requestBuilder.build().method();
        client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        ).request().expectContinue();

        System.out.println(body);
    }

    public void createOrder(@NotNull TradePair tradePair, Side buy, ENUM_ORDER_TYPE stopLoss, Double quantity, double price, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) {

        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.binance.us/" +
                "api/v3/orders/" + tradePair.toString('/') +
                "?side=" + buy +
                "&type=" + stopLoss +
                "&quantity=" + quantity +
                "&price=" + price +
                "&stopLoss=" + stopPrice +
                "&takeProfit=" + takeProfitPrice;

        System.out.println(uriStr);

        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                uriStr

        ));


    }


    //Price Data
//    Get Live Ticker Price
//    Example
//
//# Example A, symbol param provided
//    curl -X "GET" "https://api.binance.us/api/v3/ticker/price?symbol=LTCBTC"
//
//            # Example B, no symbol provided
//    curl -X "GET" "https://api.binance.us/api/v3/ticker/price"
//    Response A
//
//    {
//        "symbol": "LTCBTC",
//            "price": "0.00378800"
//    }
//    Response B
//
//            [
//    {
//        "symbol": "BTCUSD",
//            "price": "59705.0700"
//    },
//    {
//        "symbol": "ETHUSD",
//            "price": "4178.7200"
//    }
//]
//    GET /api/v3/ticker/price
    public ConcurrentHashMap<String, Double> getLiveTickerPrice() throws IOException, InterruptedException {

//        [
//        {
//            "symbol": "BTCUSD4",
//                "price": "22882.5400"
//        },
//        {
//            "symbol": "ETHUSD4",
//                "price": "1626.0300"
//        },
//        {
//            "symbol": "XRPUSD",
//                "price": "0.2970"
//        },
//        {
//            "symbol": "BCHUSD4",
//                "price": "134.2000"
//        },
//        {
//            "symbol": "LTCUSD4",
//                "price": "96.2100"
//        },
//        {
//            "symbol": "USDTUSD4",
//                "price": "1.0003"
//        },
//        {
//            "symbol": "BTCUSDT",
//                "price": "26998.92000000"
//        },
//        {
//            "symbol": "ETHUSDT",
//                "price": "1709.76000000"
//        },
//        {
//            "symbol": "XRPUSDT",
//                "price": "0.29691000"
//        },
//        {
//            "symbol": "BCHUSDT",
//                "price": "119.70000000"
//        },
//        {
//            "symbol": "LTCUSDT",
//                "price": "88.18000000"
//        },
//        {
//            "symbol": "BNBUSD4",
//                "price": "325.6257"
//        },
//        {
//            "symbol": "BNBUSDT",
//                "price": "307.50000000"
//        },
//        {
//            "symbol": "ETHBTC",
//                "price": "0.06332200"
//        },
//        {
//            "symbol": "XRPBTC",
//                "price": "0.00000864"
//        },
//        {
//            "symbol": "BNBBTC",
//                "price": "0.01138500"
//        },
//        {
//            "symbol": "LTCBTC",
//                "price": "0.00327200"
//        },
//        {
//            "symbol": "BCHBTC",
//                "price": "0.00439000"
//        },
//        {
//            "symbol": "ADAUSD4",
//                "price": "0.3920"
//        },
//        {
//            "symbol": "BATUSD4",
//                "price": "0.2843"
//        },
//        {
//            "symbol": "ETCUSD4",
//                "price": "22.7276"
//        },
//        {
//            "symbol": "XLMUSD4",
//                "price": "0.0910"
//        },
//        {
//            "symbol": "ZRXUSD4",
//                "price": "0.2525"
//        },
//        {
//            "symbol": "ADAUSDT",
//                "price": "0.33980000"
//        },
//        {
//            "symbol": "BATUSDT",
//                "price": "0.23740000"
//        },
//        {
//            "symbol": "ETCUSDT",
//                "price": "19.21000000"
//        },
//        {
//            "symbol": "XLMUSDT",
//                "price": "0.09080000"
//        },
//        {
//            "symbol": "ZRXUSDT",
//                "price": "0.21350000"
//        },
//        {
//            "symbol": "LINKUSD4",
//                "price": "6.9647"
//        },
//        {
//            "symbol": "RVNUSD4",
//                "price": "0.0295"
//        },
//        {
//            "symbol": "DASHUSD4",
//                "price": "60.2100"
//        },
//        {
//            "symbol": "ZECUSD4",
//                "price": "43.9600"
//        },
//        {
//            "symbol": "ALGOUSD4",
//                "price": "0.2580"
//        },
//        {
//            "symbol": "IOTAUSD4",
//                "price": "0.2500"
//        },
//        {
//            "symbol": "BUSDUSD4",
//                "price": "1.0005"
//        },
//        {
//            "symbol": "BTCBUSD",
//                "price": "27006.45000000"
//        },
//        {
//            "symbol": "DOGEUSDT",
//                "price": "0.07216000"
//        },
//        {
//            "symbol": "WAVESUSD4",
//                "price": "2.6353"
//        },
//        {
//            "symbol": "ATOMUSDT",
//                "price": "10.82300000"
//        },
//        {
//            "symbol": "ATOMUSD4",
//                "price": "14.4280"
//        },
//        {
//            "symbol": "NEOUSDT",
//                "price": "12.00000000"
//        },
//        {
//            "symbol": "NEOUSD4",
//                "price": "8.7720"
//        },
//        {
//            "symbol": "VETUSDT",
//                "price": "0.02179000"
//        },
//        {
//            "symbol": "QTUMUSDT",
//                "price": "2.70000000"
//        },
//        {
//            "symbol": "QTUMUSD4",
//                "price": "2.8040"
//        },
//        {
//            "symbol": "NANOUSD",
//                "price": "2.1240"
//        },
//        {
//            "symbol": "ICXUSD4",
//                "price": "0.2397"
//        },
//        {
//            "symbol": "ENJUSD4",
//                "price": "0.4568"
//        },
//        {
//            "symbol": "ONTUSD4",
//                "price": "0.2301"
//        },
//        {
//            "symbol": "ONTUSDT",
//                "price": "0.21440000"
//        },
//        {
//            "symbol": "ZILUSD4",
//                "price": "0.0287"
//        },
//        {
//            "symbol": "ZILBUSD",
//                "price": "0.02553000"
//        },
//        {
//            "symbol": "VETUSD4",
//                "price": "0.0241"
//        },
//        {
//            "symbol": "BNBBUSD",
//                "price": "307.90000000"
//        },
//        {
//            "symbol": "XRPBUSD",
//                "price": "0.30138000"
//        },
//        {
//            "symbol": "ETHBUSD",
//                "price": "1710.73000000"
//        },
//        {
//            "symbol": "ALGOBUSD",
//                "price": "0.20080000"
//        },
//        {
//            "symbol": "XTZUSD4",
//                "price": "1.1814"
//        },
//        {
//            "symbol": "XTZBUSD",
//                "price": "1.07400000"
//        },
//        {
//            "symbol": "HBARUSD4",
//                "price": "0.0699"
//        },
//        {
//            "symbol": "HBARBUSD",
//                "price": "0.05840000"
//        },
//        {
//            "symbol": "OMGUSD4",
//                "price": "1.6934"
//        },
//        {
//            "symbol": "OMGBUSD",
//                "price": "1.71540000"
//        },
//        {
//            "symbol": "MATICUSD4",
//                "price": "1.1954"
//        },
//        {
//            "symbol": "MATICBUSD",
//                "price": "1.04210000"
//        },
//        {
//            "symbol": "XTZBTC",
//                "price": "0.00003944"
//        },
//        {
//            "symbol": "ADABTC",
//                "price": "0.00001259"
//        },
//        {
//            "symbol": "REPBUSD",
//                "price": "5.20000000"
//        },
//        {
//            "symbol": "REPUSD",
//                "price": "5.3400"
//        },
//        {
//            "symbol": "EOSBUSD",
//                "price": "1.08080000"
//        },
//        {
//            "symbol": "EOSUSD4",
//                "price": "1.0900"
//        },
//        {
//            "symbol": "DOGEUSD4",
//                "price": "0.0918"
//        },
//        {
//            "symbol": "KNCUSD4",
//                "price": "0.8910"
//        },
//        {
//            "symbol": "KNCUSDT",
//                "price": "0.65700000"
//        },
//        {
//            "symbol": "VTHOUSDT",
//                "price": "0.00129100"
//        },
//        {
//            "symbol": "VTHOUSD4",
//                "price": "0.0012"
//        },
//        {
//            "symbol": "USDCUSD4",
//                "price": "1.0018"
//        },
//        {
//            "symbol": "COMPUSDT",
//                "price": "40.57000000"
//        },
//        {
//            "symbol": "COMPUSD4",
//                "price": "52.0700"
//        },
//        {
//            "symbol": "MANAUSD4",
//                "price": "0.7261"
//        },
//        {
//            "symbol": "HNTUSD4",
//                "price": "2.9002"
//        },
//        {
//            "symbol": "HNTUSDT",
//                "price": "1.27000000"
//        },
//        {
//            "symbol": "MKRUSD4",
//                "price": "697.5119"
//        },
//        {
//            "symbol": "MKRUSDT",
//                "price": "653.00000000"
//        },
//        {
//            "symbol": "DAIUSD4",
//                "price": "1.0005"
//        },
//        {
//            "symbol": "ONEUSDT",
//                "price": "0.01917000"
//        },
//        {
//            "symbol": "ONEUSD4",
//                "price": "0.0264"
//        },
//        {
//            "symbol": "BANDUSDT",
//                "price": "1.62100000"
//        },
//        {
//            "symbol": "BANDUSD4",
//                "price": "2.0780"
//        },
//        {
//            "symbol": "STORJUSDT",
//                "price": "0.32970000"
//        },
//        {
//            "symbol": "STORJUSD4",
//                "price": "0.4241"
//        },
//        {
//            "symbol": "BUSDUSDT",
//                "price": "0.99840000"
//        },
//        {
//            "symbol": "UNIUSD4",
//                "price": "6.8095"
//        },
//        {
//            "symbol": "UNIUSDT",
//                "price": "5.61200000"
//        },
//        {
//            "symbol": "SOLUSD4",
//                "price": "23.2867"
//        },
//        {
//            "symbol": "SOLUSDT",
//                "price": "19.74000000"
//        },
//        {
//            "symbol": "LINKBTC",
//                "price": "0.00025220"
//        },
//        {
//            "symbol": "VETBTC",
//                "price": "0.00000081"
//        },
//        {
//            "symbol": "UNIBTC",
//                "price": "0.00020780"
//        },
//        {
//            "symbol": "EGLDUSDT",
//                "price": "40.62000000"
//        },
//        {
//            "symbol": "EGLDUSD4",
//                "price": "44.9190"
//        },
//        {
//            "symbol": "PAXGUSDT",
//                "price": "1954.00000000"
//        },
//        {
//            "symbol": "PAXGUSD4",
//                "price": "1860.1300"
//        },
//        {
//            "symbol": "OXTUSDT",
//                "price": "0.08340000"
//        },
//        {
//            "symbol": "OXTUSD4",
//                "price": "0.0967"
//        },
//        {
//            "symbol": "ZENUSDT",
//                "price": "9.74000000"
//        },
//        {
//            "symbol": "ZENUSD4",
//                "price": "10.3240"
//        },
//        {
//            "symbol": "BTCUSDC",
//                "price": "27028.75000000"
//        },
//        {
//            "symbol": "ONEBUSD",
//                "price": "0.01927000"
//        },
//        {
//            "symbol": "FILUSDT",
//                "price": "5.26900000"
//        },
//        {
//            "symbol": "FILUSD4",
//                "price": "5.2700"
//        },
//        {
//            "symbol": "AAVEUSDT",
//                "price": "69.70000000"
//        },
//        {
//            "symbol": "AAVEUSD4",
//                "price": "85.6100"
//        },
//        {
//            "symbol": "GRTUSDT",
//                "price": "0.13070000"
//        },
//        {
//            "symbol": "GRTUSD4",
//                "price": "0.1292"
//        },
//        {
//            "symbol": "SUSHIUSD4",
//                "price": "1.4250"
//        },
//        {
//            "symbol": "ANKRUSD4",
//                "price": "0.0285"
//        },
//        {
//            "symbol": "AMPUSD",
//                "price": "0.0081"
//        },
//        {
//            "symbol": "SHIBUSDT",
//                "price": "0.00001030"
//        },
//        {
//            "symbol": "SHIBBUSD",
//                "price": "0.00001029"
//        },
//        {
//            "symbol": "CRVUSDT",
//                "price": "0.88800000"
//        },
//        {
//            "symbol": "CRVUSD4",
//                "price": "1.0470"
//        },
//        {
//            "symbol": "AXSUSDT",
//                "price": "7.97000000"
//        },
//        {
//            "symbol": "AXSUSD4",
//                "price": "10.9900"
//        },
//        {
//            "symbol": "SOLBTC",
//                "price": "0.00073010"
//        },
//        {
//            "symbol": "AVAXUSDT",
//                "price": "16.41000000"
//        },
//        {
//            "symbol": "AVAXUSD4",
//                "price": "19.9500"
//        },
//        {
//            "symbol": "CTSIUSDT",
//                "price": "0.13580000"
//        },
//        {
//            "symbol": "CTSIUSD4",
//                "price": "0.1616"
//        },
//        {
//            "symbol": "DOTUSDT",
//                "price": "5.82200000"
//        },
//        {
//            "symbol": "DOTUSD4",
//                "price": "6.6600"
//        },
//        {
//            "symbol": "YFIUSDT",
//                "price": "8044.00000000"
//        },
//        {
//            "symbol": "YFIUSD4",
//                "price": "7598.8400"
//        },
//        {
//            "symbol": "1INCHUSDT",
//                "price": "0.48500000"
//        },
//        {
//            "symbol": "1INCHUSD4",
//                "price": "0.5740"
//        },
//        {
//            "symbol": "FTMUSDT",
//                "price": "0.41370000"
//        },
//        {
//            "symbol": "FTMUSD4",
//                "price": "0.5577"
//        },
//        {
//            "symbol": "USDCUSDT",
//                "price": "0.99870000"
//        },
//        {
//            "symbol": "ETHUSDC",
//                "price": "1710.67000000"
//        },
//        {
//            "symbol": "USDCBUSD",
//                "price": "0.99950000"
//        },
//        {
//            "symbol": "MATICUSDT",
//                "price": "1.03640000"
//        },
//        {
//            "symbol": "MANAUSDT",
//                "price": "0.55880000"
//        },
//        {
//            "symbol": "MANABUSD",
//                "price": "0.56510000"
//        },
//        {
//            "symbol": "ALGOUSDT",
//                "price": "0.19850000"
//        },
//        {
//            "symbol": "ADABUSD",
//                "price": "0.34290000"
//        },
//        {
//            "symbol": "SOLBUSD",
//                "price": "19.91000000"
//        },
//        {
//            "symbol": "LINKUSDT",
//                "price": "6.84300000"
//        },
//        {
//            "symbol": "EOSUSDT",
//                "price": "1.09200000"
//        },
//        {
//            "symbol": "ZECUSDT",
//                "price": "35.10000000"
//        },
//        {
//            "symbol": "ENJUSDT",
//                "price": "0.36550000"
//        },
//        {
//            "symbol": "NEARUSDT",
//                "price": "1.82900000"
//        },
//        {
//            "symbol": "NEARBUSD",
//                "price": "1.82900000"
//        },
//        {
//            "symbol": "NEARUSD4",
//                "price": "2.3770"
//        },
//        {
//            "symbol": "OMGUSDT",
//                "price": "1.73900000"
//        },
//        {
//            "symbol": "SUSHIUSDT",
//                "price": "0.99800000"
//        },
//        {
//            "symbol": "LRCUSDT",
//                "price": "0.34020000"
//        },
//        {
//            "symbol": "LRCUSD4",
//                "price": "0.3718"
//        },
//        {
//            "symbol": "LRCBTC",
//                "price": "0.00001238"
//        },
//        {
//            "symbol": "KSHIBUSD4",
//                "price": "0.0144"
//        },
//        {
//            "symbol": "LPTUSDT",
//                "price": "6.42000000"
//        },
//        {
//            "symbol": "LPTBUSD",
//                "price": "6.59000000"
//        },
//        {
//            "symbol": "LPTUSD4",
//                "price": "8.7800"
//        },
//        {
//            "symbol": "POLYUSDT",
//                "price": "0.26240000"
//        },
//        {
//            "symbol": "POLYBUSD",
//                "price": "0.26400000"
//        },
//        {
//            "symbol": "POLYUSD",
//                "price": "0.2628"
//        },
//        {
//            "symbol": "POLYBTC",
//                "price": "0.00001360"
//        },
//        {
//            "symbol": "MATICBTC",
//                "price": "0.00003841"
//        },
//        {
//            "symbol": "DOTBTC",
//                "price": "0.00021550"
//        },
//        {
//            "symbol": "NMRUSDT",
//                "price": "17.84000000"
//        },
//        {
//            "symbol": "NMRUSD4",
//                "price": "21.3500"
//        },
//        {
//            "symbol": "SLPUSDT",
//                "price": "0.00256300"
//        },
//        {
//            "symbol": "SLPUSD4",
//                "price": "0.0032"
//        },
//        {
//            "symbol": "ANTUSDT",
//                "price": "2.22000000"
//        },
//        {
//            "symbol": "ANTUSD4",
//                "price": "2.8640"
//        },
//        {
//            "symbol": "XNOUSD4",
//                "price": "0.8450"
//        },
//        {
//            "symbol": "CHZUSDT",
//                "price": "0.11190000"
//        },
//        {
//            "symbol": "CHZUSD4",
//                "price": "0.1396"
//        },
//        {
//            "symbol": "OGNUSDT",
//                "price": "0.10630000"
//        },
//        {
//            "symbol": "OGNUSD4",
//                "price": "0.1298"
//        },
//        {
//            "symbol": "GALAUSDT",
//                "price": "0.03729000"
//        },
//        {
//            "symbol": "GALAUSD4",
//                "price": "0.0528"
//        },
//        {
//            "symbol": "TLMUSDT",
//                "price": "0.01811000"
//        },
//        {
//            "symbol": "TLMUSD4",
//                "price": "0.0235"
//        },
//        {
//            "symbol": "SNXUSDT",
//                "price": "2.31700000"
//        },
//        {
//            "symbol": "SNXUSD4",
//                "price": "2.5310"
//        },
//        {
//            "symbol": "AUDIOUSDT",
//                "price": "0.25780000"
//        },
//        {
//            "symbol": "AUDIOUSD4",
//                "price": "0.2640"
//        },
//        {
//            "symbol": "ENSUSDT",
//                "price": "12.68000000"
//        },
//        {
//            "symbol": "MANABTC",
//                "price": "0.00002074"
//        },
//        {
//            "symbol": "ATOMBTC",
//                "price": "0.00039930"
//        },
//        {
//            "symbol": "AVAXBTC",
//                "price": "0.00060880"
//        },
//        {
//            "symbol": "WBTCBTC",
//                "price": "1.00110000"
//        },
//        {
//            "symbol": "REQUSDT",
//                "price": "0.09230000"
//        },
//        {
//            "symbol": "REQUSD4",
//                "price": "0.1085"
//        },
//        {
//            "symbol": "APEUSDT",
//                "price": "3.87200000"
//        },
//        {
//            "symbol": "APEUSD4",
//                "price": "5.6927"
//        },
//        {
//            "symbol": "FLUXUSDT",
//                "price": "0.60000000"
//        },
//        {
//            "symbol": "FLUXUSD4",
//                "price": "0.9050"
//        },
//        {
//            "symbol": "TRXBTC",
//                "price": "0.00000234"
//        },
//        {
//            "symbol": "TRXBUSD",
//                "price": "0.06404000"
//        },
//        {
//            "symbol": "TRXUSDT",
//                "price": "0.06324000"
//        },
//        {
//            "symbol": "TRXUSD4",
//                "price": "0.0637"
//        },
//        {
//            "symbol": "COTIUSDT",
//                "price": "0.07040000"
//        },
//        {
//            "symbol": "COTIUSD4",
//                "price": "0.0985"
//        },
//        {
//            "symbol": "VOXELUSDT",
//                "price": "0.23230000"
//        },
//        {
//            "symbol": "VOXELUSD4",
//                "price": "0.3344"
//        },
//        {
//            "symbol": "RLCUSDT",
//                "price": "1.65500000"
//        },
//        {
//            "symbol": "RLCUSD4",
//                "price": "1.9810"
//        },
//        {
//            "symbol": "USTUSDT",
//                "price": "0.00650000"
//        },
//        {
//            "symbol": "USTUSD",
//                "price": "0.0068"
//        },
//        {
//            "symbol": "BICOUSDT",
//                "price": "0.35100000"
//        },
//        {
//            "symbol": "BICOUSD4",
//                "price": "0.3850"
//        },
//        {
//            "symbol": "API3USDT",
//                "price": "1.46600000"
//        },
//        {
//            "symbol": "API3USD4",
//                "price": "1.7070"
//        },
//        {
//            "symbol": "ENSUSD4",
//                "price": "15.0700"
//        },
//        {
//            "symbol": "BTCUST",
//                "price": "1000000.00000000"
//        },
//        {
//            "symbol": "BNTUSDT",
//                "price": "0.55200000"
//        },
//        {
//            "symbol": "BNTUSD4",
//                "price": "0.4480"
//        },
//        {
//            "symbol": "IMXUSDT",
//                "price": "1.01100000"
//        },
//        {
//            "symbol": "IMXUSD4",
//                "price": "0.8810"
//        },
//        {
//            "symbol": "SPELLUSDT",
//                "price": "0.00065490"
//        },
//        {
//            "symbol": "SPELLUSD4",
//                "price": "0.0009"
//        },
//        {
//            "symbol": "JASMYUSDT",
//                "price": "0.00496200"
//        },
//        {
//            "symbol": "JASMYUSD4",
//                "price": "0.0068"
//        },
//        {
//            "symbol": "FLOWUSDT",
//                "price": "0.90900000"
//        },
//        {
//            "symbol": "FLOWUSD4",
//                "price": "1.1100"
//        },
//        {
//            "symbol": "GTCUSDT",
//                "price": "1.96500000"
//        },
//        {
//            "symbol": "GTCUSD4",
//                "price": "2.0620"
//        },
//        {
//            "symbol": "THETAUSDT",
//                "price": "0.95800000"
//        },
//        {
//            "symbol": "THETAUSD4",
//                "price": "1.0900"
//        },
//        {
//            "symbol": "TFUELUSDT",
//                "price": "0.04970000"
//        },
//        {
//            "symbol": "TFUELUSD4",
//                "price": "0.0567"
//        },
//        {
//            "symbol": "OCEANUSDT",
//                "price": "0.32920000"
//        },
//        {
//            "symbol": "OCEANUSD4",
//                "price": "0.4314"
//        },
//        {
//            "symbol": "LAZIOUSDT",
//                "price": "2.50400000"
//        },
//        {
//            "symbol": "LAZIOUSD4",
//                "price": "3.4410"
//        },
//        {
//            "symbol": "SANTOSUSDT",
//                "price": "4.08700000"
//        },
//        {
//            "symbol": "SANTOSUSD4",
//                "price": "5.6920"
//        },
//        {
//            "symbol": "ALPINEUSDT",
//                "price": "2.08910000"
//        },
//        {
//            "symbol": "ALPINEUSD4",
//                "price": "2.7805"
//        },
//        {
//            "symbol": "PORTOUSDT",
//                "price": "2.22630000"
//        },
//        {
//            "symbol": "PORTOUSD4",
//                "price": "3.0457"
//        },
//        {
//            "symbol": "RENUSDT",
//                "price": "0.09592300"
//        },
//        {
//            "symbol": "RENUSD4",
//                "price": "0.1011"
//        },
//        {
//            "symbol": "CELRUSDT",
//                "price": "0.02746000"
//        },
//        {
//            "symbol": "CELRUSD4",
//                "price": "0.0196"
//        },
//        {
//            "symbol": "SKLUSDT",
//                "price": "0.03717000"
//        },
//        {
//            "symbol": "SKLUSD4",
//                "price": "0.0384"
//        },
//        {
//            "symbol": "VITEUSDT",
//                "price": "0.02215000"
//        },
//        {
//            "symbol": "VITEUSD4",
//                "price": "0.0254"
//        },
//        {
//            "symbol": "WAXPUSDT",
//                "price": "0.06400000"
//        },
//        {
//            "symbol": "WAXPUSD4",
//                "price": "0.0799"
//        },
//        {
//            "symbol": "LTOUSDT",
//                "price": "0.10120000"
//        },
//        {
//            "symbol": "LTOUSD4",
//                "price": "0.1039"
//        },
//        {
//            "symbol": "FETUSDT",
//                "price": "0.33870000"
//        },
//        {
//            "symbol": "FETUSD4",
//                "price": "0.4293"
//        },
//        {
//            "symbol": "BONDUSDT",
//                "price": "3.99500000"
//        },
//        {
//            "symbol": "BONDUSD4",
//                "price": "4.5100"
//        },
//        {
//            "symbol": "LOKAUSDT",
//                "price": "0.48000000"
//        },
//        {
//            "symbol": "LOKAUSD4",
//                "price": "0.7049"
//        },
//        {
//            "symbol": "ICPUSDT",
//                "price": "4.70300000"
//        },
//        {
//            "symbol": "ICPUSD4",
//                "price": "5.7000"
//        },
//        {
//            "symbol": "TUSDT",
//                "price": "0.03839000"
//        },
//        {
//            "symbol": "TUSD4",
//                "price": "0.0437"
//        },
//        {
//            "symbol": "OPUSDT",
//                "price": "2.04400000"
//        },
//        {
//            "symbol": "OPUSD4",
//                "price": "2.8290"
//        },
//        {
//            "symbol": "ROSEUSDT",
//                "price": "0.05331000"
//        },
//        {
//            "symbol": "ROSEUSD4",
//                "price": "0.0546"
//        },
//        {
//            "symbol": "CELOUSDT",
//                "price": "0.56200000"
//        },
//        {
//            "symbol": "CELOUSD4",
//                "price": "0.7990"
//        },
//        {
//            "symbol": "KDAUSDT",
//                "price": "0.90900000"
//        },
//        {
//            "symbol": "KDAUSD4",
//                "price": "1.1670"
//        },
//        {
//            "symbol": "KSMUSDT",
//                "price": "31.41000000"
//        },
//        {
//            "symbol": "KSMUSD4",
//                "price": "35.8000"
//        },
//        {
//            "symbol": "ACHUSDT",
//                "price": "0.03099000"
//        },
//        {
//            "symbol": "ACHUSD4",
//                "price": "0.0190"
//        },
//        {
//            "symbol": "DARUSDT",
//                "price": "0.16551000"
//        },
//        {
//            "symbol": "DARUSD4",
//                "price": "0.2487"
//        },
//        {
//            "symbol": "RNDRUSDT",
//                "price": "1.13100000"
//        },
//        {
//            "symbol": "RNDRUSD4",
//                "price": "1.8850"
//        },
//        {
//            "symbol": "SYSUSDT",
//                "price": "0.15800000"
//        },
//        {
//            "symbol": "SYSUSD4",
//                "price": "0.1694"
//        },
//        {
//            "symbol": "RADUSDT",
//                "price": "2.23400000"
//        },
//        {
//            "symbol": "RADUSD4",
//                "price": "1.9200"
//        },
//        {
//            "symbol": "ILVUSDT",
//                "price": "58.67000000"
//        },
//        {
//            "symbol": "ILVUSD4",
//                "price": "91.6000"
//        },
//        {
//            "symbol": "LDOUSDT",
//                "price": "2.09800000"
//        },
//        {
//            "symbol": "LDOUSD4",
//                "price": "2.0470"
//        },
//        {
//            "symbol": "RAREUSDT",
//                "price": "0.11300000"
//        },
//        {
//            "symbol": "RAREUSD4",
//                "price": "0.1560"
//        },
//        {
//            "symbol": "LSKUSDT",
//                "price": "1.01000000"
//        },
//        {
//            "symbol": "LSKUSD4",
//                "price": "1.3990"
//        },
//        {
//            "symbol": "DGBUSDT",
//                "price": "0.00931000"
//        },
//        {
//            "symbol": "DGBUSD4",
//                "price": "0.0107"
//        },
//        {
//            "symbol": "REEFUSDT",
//                "price": "0.00247600"
//        },
//        {
//            "symbol": "REEFUSD4",
//                "price": "0.0034"
//        },
//        {
//            "symbol": "SRMUSDT",
//                "price": "0.16201000"
//        },
//        {
//            "symbol": "SRMUSD",
//                "price": "0.1772"
//        },
//        {
//            "symbol": "ALICEUSDT",
//                "price": "1.40400000"
//        },
//        {
//            "symbol": "ALICEUSD4",
//                "price": "1.8010"
//        },
//        {
//            "symbol": "FORTHUSDT",
//                "price": "3.20000000"
//        },
//        {
//            "symbol": "FORTHUSD4",
//                "price": "3.6600"
//        },
//        {
//            "symbol": "ASTRUSDT",
//                "price": "0.05920000"
//        },
//        {
//            "symbol": "ASTRUSD4",
//                "price": "0.0541"
//        },
//        {
//            "symbol": "BTRSTUSDT",
//                "price": "0.83200000"
//        },
//        {
//            "symbol": "BTRSTUSD4",
//                "price": "1.0830"
//        },
//        {
//            "symbol": "GALUSDT",
//                "price": "1.57200000"
//        },
//        {
//            "symbol": "GALUSD4",
//                "price": "2.3040"
//        },
//        {
//            "symbol": "SANDUSDT",
//                "price": "0.58610000"
//        },
//        {
//            "symbol": "SANDUSD4",
//                "price": "0.7269"
//        },
//        {
//            "symbol": "BALUSDT",
//                "price": "6.54300000"
//        },
//        {
//            "symbol": "BALUSD4",
//                "price": "6.8690"
//        },
//        {
//            "symbol": "POLYXUSD4",
//                "price": "0.2063"
//        },
//        {
//            "symbol": "GLMUSDT",
//                "price": "0.22210000"
//        },
//        {
//            "symbol": "GLMUSD4",
//                "price": "0.2611"
//        },
//        {
//            "symbol": "CLVUSDT",
//                "price": "0.05993000"
//        },
//        {
//            "symbol": "CLVUSD4",
//                "price": "0.0759"
//        },
//        {
//            "symbol": "TUSDUSDT",
//                "price": "0.99890000"
//        },
//        {
//            "symbol": "TUSDUSD4",
//                "price": "1.0004"
//        },
//        {
//            "symbol": "QNTUSDT",
//                "price": "118.70000000"
//        },
//        {
//            "symbol": "QNTUSD4",
//                "price": "138.0000"
//        },
//        {
//            "symbol": "STGUSDT",
//                "price": "0.62700000"
//        },
//        {
//            "symbol": "STGUSD4",
//                "price": "0.7365"
//        },
//        {
//            "symbol": "AXLUSDT",
//                "price": "0.59500000"
//        },
//        {
//            "symbol": "AXLUSD4",
//                "price": "0.6030"
//        },
//        {
//            "symbol": "KAVAUSDT",
//                "price": "0.84100000"
//        },
//        {
//            "symbol": "KAVAUSD4",
//                "price": "0.9670"
//        },
//        {
//            "symbol": "APTUSDT",
//                "price": "10.98080000"
//        },
//        {
//            "symbol": "APTUSD4",
//                "price": "14.7068"
//        },
//        {
//            "symbol": "MASKUSDT",
//                "price": "5.71100000"
//        },
//        {
//            "symbol": "MASKUSD4",
//                "price": "4.4650"
//        },
//        {
//            "symbol": "BOSONUSDT",
//                "price": "0.23240000"
//        },
//        {
//            "symbol": "BOSONUSD4",
//                "price": "0.2539"
//        },
//        {
//            "symbol": "PONDUSDT",
//                "price": "0.00916000"
//        },
//        {
//            "symbol": "PONDUSD4",
//                "price": "0.0110"
//        },
//        {
//            "symbol": "SOLUSDC",
//                "price": "19.76000000"
//        },
//        {
//            "symbol": "ADAUSDC",
//                "price": "0.33960000"
//        },
//        {
//            "symbol": "MXCUSDT",
//                "price": "0.01904000"
//        },
//        {
//            "symbol": "MXCUSD4",
//                "price": "0.0310"
//        },
//        {
//            "symbol": "JAMUSDT",
//                "price": "0.00250100"
//        },
//        {
//            "symbol": "JAMUSD4",
//                "price": "0.0030"
//        },
//        {
//            "symbol": "TRACUSDT",
//                "price": "0.40950000"
//        },
//        {
//            "symbol": "PROMUSDT",
//                "price": "4.69000000"
//        },
//        {
//            "symbol": "PROMUSD4",
//                "price": "4.8600"
//        },
//        {
//            "symbol": "DIAUSDT",
//                "price": "0.36100000"
//        },
//        {
//            "symbol": "DIAUSD4",
//                "price": "0.4230"
//        },
//        {
//            "symbol": "BTCDAI",
//                "price": "27015.40000000"
//        },
//        {
//            "symbol": "ETHDAI",
//                "price": "1728.57000000"
//        },
//        {
//            "symbol": "ADAETH",
//                "price": "0.00019900"
//        },
//        {
//            "symbol": "DOGEBTC",
//                "price": "0.00000267"
//        },
//        {
//            "symbol": "LOOMUSDT",
//                "price": "0.06409000"
//        },
//        {
//            "symbol": "LOOMUSD4",
//                "price": "0.0541"
//        },
//        {
//            "symbol": "STMXUSDT",
//                "price": "0.00526100"
//        },
//        {
//            "symbol": "BTCUSD",
//                "price": "27033.54000000"
//        },
//        {
//            "symbol": "ETHUSD",
//                "price": "1711.67000000"
//        },
//        {
//            "symbol": "BCHUSD",
//                "price": "119.40000000"
//        },
//        {
//            "symbol": "LTCUSD",
//                "price": "88.36000000"
//        },
//        {
//            "symbol": "USDTUSD",
//                "price": "1.00070000"
//        },
//        {
//            "symbol": "BNBUSD",
//                "price": "307.90000000"
//        },
//        {
//            "symbol": "ADAUSD",
//                "price": "0.34160000"
//        },
//        {
//            "symbol": "BATUSD",
//                "price": "0.23630000"
//        },
//        {
//            "symbol": "ETCUSD",
//                "price": "19.34000000"
//        },
//        {
//            "symbol": "XLMUSD",
//                "price": "0.09130000"
//        },
//        {
//            "symbol": "ZRXUSD",
//                "price": "0.21460000"
//        },
//        {
//            "symbol": "LINKUSD",
//                "price": "6.85300000"
//        },
//        {
//            "symbol": "RVNUSD",
//                "price": "0.02390000"
//        },
//        {
//            "symbol": "DASHUSD",
//                "price": "55.49000000"
//        },
//        {
//            "symbol": "ZECUSD",
//                "price": "35.20000000"
//        },
//        {
//            "symbol": "ALGOUSD",
//                "price": "0.19900000"
//        },
//        {
//            "symbol": "IOTAUSD",
//                "price": "0.19690000"
//        },
//        {
//            "symbol": "BUSDUSD",
//                "price": "0.99950000"
//        },
//        {
//            "symbol": "WAVESUSD",
//                "price": "2.01600000"
//        },
//        {
//            "symbol": "ATOMUSD",
//                "price": "10.83500000"
//        },
//        {
//            "symbol": "NEOUSD",
//                "price": "11.97100000"
//        },
//        {
//            "symbol": "QTUMUSD",
//                "price": "2.97800000"
//        },
//        {
//            "symbol": "ICXUSD",
//                "price": "0.19880000"
//        },
//        {
//            "symbol": "ENJUSD",
//                "price": "0.36510000"
//        },
//        {
//            "symbol": "ONTUSD",
//                "price": "0.21500000"
//        },
//        {
//            "symbol": "ZILUSD",
//                "price": "0.02560000"
//        },
//        {
//            "symbol": "VETUSD",
//                "price": "0.02185000"
//        },
//        {
//            "symbol": "XTZUSD",
//                "price": "1.06830000"
//        },
//        {
//            "symbol": "HBARUSD",
//                "price": "0.05790000"
//        },
//        {
//            "symbol": "OMGUSD",
//                "price": "1.73800000"
//        },
//        {
//            "symbol": "MATICUSD",
//                "price": "1.04000000"
//        },
//        {
//            "symbol": "EOSUSD",
//                "price": "1.07860000"
//        },
//        {
//            "symbol": "DOGEUSD",
//                "price": "0.07230000"
//        },
//        {
//            "symbol": "KNCUSD",
//                "price": "0.65600000"
//        },
//        {
//            "symbol": "VTHOUSD",
//                "price": "0.00129300"
//        },
//        {
//            "symbol": "USDCUSD",
//                "price": "1.00000000"
//        },
//        {
//            "symbol": "COMPUSD",
//                "price": "40.54000000"
//        },
//        {
//            "symbol": "MANAUSD",
//                "price": "0.56160000"
//        },
//        {
//            "symbol": "HNTUSD",
//                "price": "1.28000000"
//        },
//        {
//            "symbol": "MKRUSD",
//                "price": "654.00000000"
//        },
//        {
//            "symbol": "DAIUSD",
//                "price": "1.00150000"
//        },
//        {
//            "symbol": "ONEUSD",
//                "price": "0.01910000"
//        },
//        {
//            "symbol": "BANDUSD",
//                "price": "1.63300000"
//        },
//        {
//            "symbol": "STORJUSD",
//                "price": "0.33490000"
//        },
//        {
//            "symbol": "UNIUSD",
//                "price": "5.62700000"
//        },
//        {
//            "symbol": "SOLUSD",
//                "price": "19.77000000"
//        },
//        {
//            "symbol": "EGLDUSD",
//                "price": "40.85000000"
//        },
//        {
//            "symbol": "PAXGUSD",
//                "price": "1958.00000000"
//        },
//        {
//            "symbol": "OXTUSD",
//                "price": "0.08310000"
//        },
//        {
//            "symbol": "ZENUSD",
//                "price": "9.76000000"
//        },
//        {
//            "symbol": "FILUSD",
//                "price": "5.29100000"
//        },
//        {
//            "symbol": "AAVEUSD",
//                "price": "69.60000000"
//        },
//        {
//            "symbol": "GRTUSD",
//                "price": "0.13170000"
//        },
//        {
//            "symbol": "SUSHIUSD",
//                "price": "1.00000000"
//        },
//        {
//            "symbol": "ANKRUSD",
//                "price": "0.03042000"
//        },
//        {
//            "symbol": "CRVUSD",
//                "price": "0.88300000"
//        },
//        {
//            "symbol": "AXSUSD",
//                "price": "7.94000000"
//        },
//        {
//            "symbol": "AVAXUSD",
//                "price": "16.41000000"
//        },
//        {
//            "symbol": "CTSIUSD",
//                "price": "0.13740000"
//        },
//        {
//            "symbol": "DOTUSD",
//                "price": "5.84000000"
//        },
//        {
//            "symbol": "YFIUSD",
//                "price": "8029.00000000"
//        },
//        {
//            "symbol": "1INCHUSD",
//                "price": "0.48800000"
//        },
//        {
//            "symbol": "FTMUSD",
//                "price": "0.41410000"
//        },
//        {
//            "symbol": "NEARUSD",
//                "price": "1.83500000"
//        },
//        {
//            "symbol": "LRCUSD",
//                "price": "0.33490000"
//        },
//        {
//            "symbol": "KSHIBUSD",
//                "price": "0.01350000"
//        },
//        {
//            "symbol": "LPTUSD",
//                "price": "6.41000000"
//        },
//        {
//            "symbol": "NMRUSD",
//                "price": "17.98000000"
//        },
//        {
//            "symbol": "SLPUSD",
//                "price": "0.00255100"
//        },
//        {
//            "symbol": "ANTUSD",
//                "price": "2.21900000"
//        },
//        {
//            "symbol": "XNOUSD",
//                "price": "0.87500000"
//        },
//        {
//            "symbol": "CHZUSD",
//                "price": "0.11310000"
//        },
//        {
//            "symbol": "OGNUSD",
//                "price": "0.10860000"
//        },
//        {
//            "symbol": "GALAUSD",
//                "price": "0.03732000"
//        },
//        {
//            "symbol": "TLMUSD",
//                "price": "0.01810000"
//        },
//        {
//            "symbol": "SNXUSD",
//                "price": "2.31600000"
//        },
//        {
//            "symbol": "AUDIOUSD",
//                "price": "0.25890000"
//        },
//        {
//            "symbol": "REQUSD",
//                "price": "0.09370000"
//        },
//        {
//            "symbol": "APEUSD",
//                "price": "3.87600000"
//        },
//        {
//            "symbol": "FLUXUSD",
//                "price": "0.60300000"
//        },
//        {
//            "symbol": "TRXUSD",
//                "price": "0.06324000"
//        },
//        {
//            "symbol": "COTIUSD",
//                "price": "0.07250000"
//        },
//        {
//            "symbol": "VOXELUSD",
//                "price": "0.23230000"
//        },
//        {
//            "symbol": "RLCUSD",
//                "price": "1.66200000"
//        },
//        {
//            "symbol": "BICOUSD",
//                "price": "0.35400000"
//        },
//        {
//            "symbol": "API3USD",
//                "price": "1.46100000"
//        },
//        {
//            "symbol": "ENSUSD",
//                "price": "12.72000000"
//        },
//        {
//            "symbol": "BNTUSD",
//                "price": "0.55600000"
//        },
//        {
//            "symbol": "IMXUSD",
//                "price": "1.01400000"
//        },
//        {
//            "symbol": "SPELLUSD",
//                "price": "0.00064940"
//        },
//        {
//            "symbol": "JASMYUSD",
//                "price": "0.00497800"
//        },
//        {
//            "symbol": "FLOWUSD",
//                "price": "0.91800000"
//        },
//        {
//            "symbol": "GTCUSD",
//                "price": "1.96800000"
//        },
//        {
//            "symbol": "THETAUSD",
//                "price": "0.96100000"
//        },
//        {
//            "symbol": "TFUELUSD",
//                "price": "0.05210000"
//        },
//        {
//            "symbol": "OCEANUSD",
//                "price": "0.32870000"
//        },
//        {
//            "symbol": "LAZIOUSD",
//                "price": "2.50920000"
//        },
//        {
//            "symbol": "SANTOSUSD",
//                "price": "4.17000000"
//        },
//        {
//            "symbol": "ALPINEUSD",
//                "price": "2.09480000"
//        },
//        {
//            "symbol": "PORTOUSD",
//                "price": "2.23160000"
//        },
//        {
//            "symbol": "RENUSD",
//                "price": "0.09713500"
//        },
//        {
//            "symbol": "CELRUSD",
//                "price": "0.02715000"
//        },
//        {
//            "symbol": "SKLUSD",
//                "price": "0.03716000"
//        },
//        {
//            "symbol": "VITEUSD",
//                "price": "0.02220000"
//        },
//        {
//            "symbol": "WAXPUSD",
//                "price": "0.06420000"
//        },
//        {
//            "symbol": "LTOUSD",
//                "price": "0.10490000"
//        },
//        {
//            "symbol": "FETUSD",
//                "price": "0.34000000"
//        },
//        {
//            "symbol": "BONDUSD",
//                "price": "3.94300000"
//        },
//        {
//            "symbol": "LOKAUSD",
//                "price": "0.47590000"
//        },
//        {
//            "symbol": "ICPUSD",
//                "price": "4.71300000"
//        },
//        {
//            "symbol": "TUSD",
//                "price": "0.03900000"
//        },
//        {
//            "symbol": "OPUSD",
//                "price": "2.05500000"
//        },
//        {
//            "symbol": "ROSEUSD",
//                "price": "0.05376000"
//        },
//        {
//            "symbol": "CELOUSD",
//                "price": "0.56800000"
//        },
//        {
//            "symbol": "KDAUSD",
//                "price": "0.91300000"
//        },
//        {
//            "symbol": "KSMUSD",
//                "price": "31.58000000"
//        },
//        {
//            "symbol": "ACHUSD",
//                "price": "0.03103000"
//        },
//        {
//            "symbol": "DARUSD",
//                "price": "0.16590000"
//        },
//        {
//            "symbol": "RNDRUSD",
//                "price": "1.14000000"
//        },
//        {
//            "symbol": "SYSUSD",
//                "price": "0.15680000"
//        },
//        {
//            "symbol": "RADUSD",
//                "price": "2.23500000"
//        },
//        {
//            "symbol": "ILVUSD",
//                "price": "58.50000000"
//        },
//        {
//            "symbol": "LDOUSD",
//                "price": "2.09600000"
//        },
//        {
//            "symbol": "RAREUSD",
//                "price": "0.11240000"
//        },
//        {
//            "symbol": "LSKUSD",
//                "price": "1.01100000"
//        },
//        {
//            "symbol": "DGBUSD",
//                "price": "0.00930000"
//        },
//        {
//            "symbol": "REEFUSD",
//                "price": "0.00245800"
//        },
//        {
//            "symbol": "ALICEUSD",
//                "price": "1.43600000"
//        },
//        {
//            "symbol": "FORTHUSD",
//                "price": "3.16000000"
//        },
//        {
//            "symbol": "ASTRUSD",
//                "price": "0.05900000"
//        },
//        {
//            "symbol": "BTRSTUSD",
//                "price": "0.92700000"
//        },
//        {
//            "symbol": "GALUSD",
//                "price": "1.57600000"
//        },
//        {
//            "symbol": "SANDUSD",
//                "price": "0.58990000"
//        },
//        {
//            "symbol": "BALUSD",
//                "price": "6.49500000"
//        },
//        {
//            "symbol": "POLYXUSD",
//                "price": "0.15480000"
//        },
//        {
//            "symbol": "GLMUSD",
//                "price": "0.22730000"
//        },
//        {
//            "symbol": "CLVUSD",
//                "price": "0.05998000"
//        },
//        {
//            "symbol": "TUSDUSD",
//                "price": "0.95090000"
//        },
//        {
//            "symbol": "QNTUSD",
//                "price": "118.60000000"
//        },
//        {
//            "symbol": "STGUSD",
//                "price": "0.62880000"
//        },
//        {
//            "symbol": "AXLUSD",
//                "price": "0.59500000"
//        },
//        {
//            "symbol": "KAVAUSD",
//                "price": "0.84400000"
//        },
//        {
//            "symbol": "APTUSD",
//                "price": "10.96950000"
//        },
//        {
//            "symbol": "MASKUSD",
//                "price": "5.68800000"
//        },
//        {
//            "symbol": "BOSONUSD",
//                "price": "0.22820000"
//        },
//        {
//            "symbol": "PONDUSD",
//                "price": "0.00919000"
//        },
//        {
//            "symbol": "MXCUSD",
//                "price": "0.01902000"
//        },
//        {
//            "symbol": "JAMUSD",
//                "price": "0.00272400"
//        },
//        {
//            "symbol": "PROMUSD",
//                "price": "4.69000000"
//        },
//        {
//            "symbol": "DIAUSD",
//                "price": "0.35700000"
//        },
//        {
//            "symbol": "LOOMUSD",
//                "price": "0.06541000"
//        },
//        {
//            "symbol": "STMXUSD",
//                "price": "0.00528500"
//        },
//        {
//            "symbol": "SHIBUSD",
//                "price": "0.00001034"
//        },
//        {
//            "symbol": "TRACUSD",
//                "price": "0.41700000"
//        }
//]

        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/ticker/price")
        );

        HttpResponse<String> response =
                client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );
        System.out.println(response.statusCode());
        System.out.println(response.body());

        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        System.out.println(json);
        ConcurrentHashMap<String, Double> prices=new ConcurrentHashMap<>();

        for (JsonNode jsonNode : json) {
            System.out.println(jsonNode.get("symbol").asText());
            System.out.println(jsonNode.get("price").asText());
           prices.put(jsonNode.get("symbol").asText(), Double.valueOf(jsonNode.get("price").asText()));
        }
        System.out.println(prices);
        System.out.println(prices.size());
        return prices;

    }
    public String getApi_key() {
        return api_key;
    }

    public @NotNull List<Currency> getAvailableSymbols() throws IOException, InterruptedException {
        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/exchangeInfo")
        );

        HttpResponse<String> response =
                client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );
        System.out.println(response.statusCode());
        System.out.println(response.body());


        //  ,{"symbol":"RNDRUSDT","status":"TRADING","baseAsset":"RNDR","baseAssetPrecision":8,"quoteAsset":"USDT","quotePrecision":8,"quoteAssetPrecision":8,"baseCommissionPrecision":8,"quoteCommissionPrecision":8,"orderTypes":["LIMIT","LIMIT_MAKER","MARKET","STOP_LOSS_LIMIT","TAKE_PROFIT_LIMIT"],"icebergAllowed":true,"ocoAllowed":true,"quoteOrderQtyMarketAllowed":true,"allowTrailingStop":true,"cancelReplaceAllowed":true,"isSpotTradingAllowed":true,"isMarginTradingAllowed":false,"filters":[{"filterType":"PRICE_FILTER","minPrice":"0.00100000","maxPrice":"1000.00000000","tickSize":"0.00100000"}


        File file = null;
        try {


            file = new File("src/main/resources/symbols.json");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(response.body());

            fileWriter.flush();
            fileWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }


        List<Currency> currencies = new ArrayList<>();
        JSONObject json = new JSONObject(response.body());
        System.out.println(json);

        ObjectMapper mapper = new ObjectMapper();
        for (JsonNode jsonNode : mapper.readTree(response.body()).get("symbols")) {
            // System.out.println(jsonNode.get("symbols").asText());
            //  System.out.println(jsonNode.get("status").asText());
            System.out.println(jsonNode.get("baseAsset").asText());
            System.out.println(jsonNode.get("baseAssetPrecision").asText());
            System.out.println(jsonNode.get("quoteAsset").asText());
            System.out.println(jsonNode.get("quotePrecision").asText());
            System.out.println(jsonNode.get("quoteAssetPrecision").asText());
            System.out.println(jsonNode.get("baseCommissionPrecision").asText());
            System.out.println(jsonNode.get("quoteCommissionPrecision").asText());
            System.out.println(jsonNode.get("orderTypes").toString());
            System.out.println(jsonNode.get("icebergAllowed").asText());
            String symbol = jsonNode.get("symbol").asText();
            String status = jsonNode.get("status").asText();
            String baseAsset = jsonNode.get("baseAsset").asText();
            String baseAssetPrecision = jsonNode.get("baseAssetPrecision").asText();
            String quoteAsset = jsonNode.get("quoteAsset").asText();
            String quotePrecision = jsonNode.get("quotePrecision").asText();
            String quoteAssetPrecision = jsonNode.get("quoteAssetPrecision").asText();
            String baseCommissionPrecision = jsonNode.get("baseCommissionPrecision").asText();
            String quoteCommissionPrecision = jsonNode.get("quoteCommissionPrecision").asText();
            String orderTypes = jsonNode.get("orderTypes").toString();
            String icebergAllowed = jsonNode.get("icebergAllowed").asText();
            String ocoAllowed = jsonNode.get("ocoAllowed").asText();
            String quoteOrderQtyMarketAllowed = jsonNode.get("quoteOrderQtyMarketAllowed").asText();
            String allowTrailingStop = jsonNode.get("allowTrailingStop").asText();
            String cancelReplaceAllowed = jsonNode.get("cancelReplaceAllowed").asText();
            logger.info(
                    "symbol: " + symbol + "\n" +
                            "status: " + status + "\n" +
                            "baseAsset: " + baseAsset + "\n" +
                            "baseAssetPrecision: " + baseAssetPrecision + "\n" +
                            "quoteAsset: " + quoteAsset + "\n" +
                            "quotePrecision: " + quotePrecision + "\n" +
                            "quoteAssetPrecision: " + quoteAssetPrecision + "\n" +
                            "baseCommissionPrecision: " + baseCommissionPrecision + "\n" +
                            "quoteCommissionPrecision: " + quoteCommissionPrecision + "\n" +
                            "orderTypes: " + orderTypes + "\n" +
                            "icebergAllowed: " + icebergAllowed + "\n" +
                            "ocoAllowed: " + ocoAllowed + "\n" +
                            "quoteOrderQtyMarketAllowed: " + quoteOrderQtyMarketAllowed + "\n" +
                            "allowTrailingStop: " + allowTrailingStop + "\n" +
                            "cancelReplaceAllowed: " + cancelReplaceAllowed + "\n" +
                            "isSpotTradingAllowed: " + jsonNode.get("isSpotTradingAllowed").asText() + "\n" +
                            "isMarginTradingAllowed: " + jsonNode.get("isMarginTradingAllowed").asText() + "\n"
            );


            symbol = baseAsset;
                currencies.add(new Currency(
                                       CurrencyType.CRYPTO, symbol, symbol, symbol,
                                       Integer.parseInt(baseAssetPrecision), symbol, "") {
                    @Override
                    public int compareTo(@NotNull Currency o) {
                        return 0;
                    }

                    @Override
                    public int compareTo(java.util.@NotNull Currency o) {
                        return 0;
                    }});





                    logger.info(


                            "symbol: " + symbol + "\n" +
                                    "status: " + status + "\n" +
                                    "baseAsset: " + baseAsset + "\n" +
                                    "baseAssetPrecision: " + baseAssetPrecision + "\n" +
                                    "quoteAsset: " + quoteAsset + "\n" +
                                    "quotePrecision: " + quotePrecision + "\n" +
                                    "quoteAssetPrecision: " + quoteAssetPrecision + "\n" +
                                    "baseCommissionPrecision: " + baseCommissionPrecision + "\n" +
                                    "quoteCommissionPrecision: " + quoteCommissionPrecision + "\n" +
                                    "orderTypes: " + orderTypes + "\n" +
                                    "icebergAllowed: " + icebergAllowed + "\n" +
                                    "ocoAllowed: " + ocoAllowed + "\n" +
                                    "quoteOrderQtyMarketAllowed: " + quoteOrderQtyMarketAllowed + "\n"
                    );


            logger.info(currencies.toString());
        }

        return currencies;
    }


    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size,
                            @NotNull Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

        URI uri = URI.create("https://api.binance.us/api/v3/order/symbol?" + tradePair.toString('/')
                + "&side=" + side + "&type=LIMIT&timeInForce=GTC&quantity=" + size
                + "&price=" + price + "&stopPrice=" + stopLoss + "&takeProfit="
                + takeProfit
                + "&newClientOrderId=" + UUID.randomUUID());

        requestBuilder.uri(uri);

        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            System.out.println(json);
            System.out.println(json.get("orderId"));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(response.body());
            alert.showAndWait();
        }
    }

    @Override
    public void closeAllOrders() throws IOException, InterruptedException {
        URI uri = URI.create("https://api.binance.us/api/v3/cancel");
        requestBuilder.uri(uri);
        requestBuilder.DELETE();
        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            System.out.println(json);
            System.out.println(json.get("orderId"));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(response.body());
            alert.showAndWait();
        }

    }

    @Override
    public List<String> getTradePair() throws IOException, InterruptedException {

        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/exchangeInfo")
        );

        HttpResponse<String> response =
                client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );
        System.out.println(response.statusCode());
        System.out.println(response.body());


        //  ,{"symbol":"RNDRUSDT","status":"TRADING","baseAsset":"RNDR","baseAssetPrecision":8,"quoteAsset":"USDT","quotePrecision":8,"quoteAssetPrecision":8,"baseCommissionPrecision":8,"quoteCommissionPrecision":8,"orderTypes":["LIMIT","LIMIT_MAKER","MARKET","STOP_LOSS_LIMIT","TAKE_PROFIT_LIMIT"],"icebergAllowed":true,"ocoAllowed":true,"quoteOrderQtyMarketAllowed":true,"allowTrailingStop":true,"cancelReplaceAllowed":true,"isSpotTradingAllowed":true,"isMarginTradingAllowed":false,"filters":[{"filterType":"PRICE_FILTER","minPrice":"0.00100000","maxPrice":"1000.00000000","tickSize":"0.00100000"}


        File file = null;
        try {


            file = new File("src/main/resources/symbols.json");
            if (!file.exists()) {
                file.createNewFile();
            }
            FileWriter fileWriter = new FileWriter(file);
            fileWriter.write(response.body());

            fileWriter.flush();
            fileWriter.close();

        } catch (IOException e) {
            e.printStackTrace();
        }

        JSONObject json = new JSONObject(response.body());
        System.out.println(json);

        ObjectMapper mapper = new ObjectMapper();
        Set<TradePair> tradePairs = new HashSet<>();
        List<String> data = new ArrayList<>();
        for (JsonNode jsonNode : mapper.readTree(response.body()).get("symbols")) {
            // System.out.println(jsonNode.get("symbols").asText());
            System.out.println(jsonNode.get("status").asText());
            System.out.println(jsonNode.get("baseAsset").asText());
            System.out.println(jsonNode.get("baseAssetPrecision").asText());
            System.out.println(jsonNode.get("quoteAsset").asText());
            System.out.println(jsonNode.get("quotePrecision").asText());
            System.out.println(jsonNode.get("quoteAssetPrecision").asText());
            System.out.println(jsonNode.get("baseCommissionPrecision").asText());
            System.out.println(jsonNode.get("quoteCommissionPrecision").asText());
            System.out.println(jsonNode.get("orderTypes").toString());
            System.out.println(jsonNode.get("icebergAllowed").asText());
            String symbol = jsonNode.get("symbol").asText();
            String status = jsonNode.get("status").asText();
            String baseAsset = jsonNode.get("baseAsset").asText();
            String baseAssetPrecision = jsonNode.get("baseAssetPrecision").asText();
            String quoteAsset = jsonNode.get("quoteAsset").asText();
            String quotePrecision = jsonNode.get("quotePrecision").asText();
            String quoteAssetPrecision = jsonNode.get("quoteAssetPrecision").asText();
            String baseCommissionPrecision = jsonNode.get("baseCommissionPrecision").asText();
            String quoteCommissionPrecision = jsonNode.get("quoteCommissionPrecision").asText();
            String orderTypes = jsonNode.get("orderTypes").toString();
            String icebergAllowed = jsonNode.get("icebergAllowed").asText();
            String ocoAllowed = jsonNode.get("ocoAllowed").asText();
            String quoteOrderQtyMarketAllowed = jsonNode.get("quoteOrderQtyMarketAllowed").asText();
            String allowTrailingStop = jsonNode.get("allowTrailingStop").asText();
            String cancelReplaceAllowed = jsonNode.get("cancelReplaceAllowed").asText();
            logger.info(
                    "symbol: " + symbol + "\n" +
                            "status: " + status + "\n" +
                            "baseAsset: " + baseAsset + "\n" +
                            "baseAssetPrecision: " + baseAssetPrecision + "\n" +
                            "quoteAsset: " + quoteAsset + "\n" +
                            "quotePrecision: " + quotePrecision + "\n" +
                            "quoteAssetPrecision: " + quoteAssetPrecision + "\n" +
                            "baseCommissionPrecision: " + baseCommissionPrecision + "\n" +
                            "quoteCommissionPrecision: " + quoteCommissionPrecision + "\n" +
                            "orderTypes: " + orderTypes + "\n" +
                            "icebergAllowed: " + icebergAllowed + "\n" +
                            "ocoAllowed: " + ocoAllowed + "\n" +
                            "quoteOrderQtyMarketAllowed: " + quoteOrderQtyMarketAllowed + "\n" +
                            "allowTrailingStop: " + allowTrailingStop + "\n" +
                            "cancelReplaceAllowed: " + cancelReplaceAllowed + "\n" +
                            "isSpotTradingAllowed: " + jsonNode.get("isSpotTradingAllowed").asText() + "\n" +
                            "isMarginTradingAllowed: " + jsonNode.get("isMarginTradingAllowed").asText() + "\n"
            );

            data.add(
                    baseAsset + "/" + quoteAsset
            );
        }

        return data;

    }

    @Override
    public void connect(String text, String text1, String userIdText) throws IOException, InterruptedException {
requestBuilder.uri(URI.create("https://api.binance.us/api/v3/"));
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("X-MBX-APIKEY", text);
        requestBuilder.header("X-MBX-APISECRET", text1);
        api_key=text;

        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        JSONObject json = new JSONObject(response);
//        System.out.println(json);
//        System.out.println(json.get("status"));
//        System.out.println(json.get("message"));
//        System.out.println(json.get("serverTime"));
//        System.out.println(json.get("time"));
        if (json.get("status").equals("ok")) {
            System.out.println(json.get("serverTime"));
            System.out.println(json.get("time"));
            isConnected=true;
        }else {
            isConnected=false;
        }

    }

    @Override
    public boolean isConnected() {
        return isConnected;

    }

    @Override
    public ListView<Order> getAllOrders() throws IOException, InterruptedException {

        String uriStr = "https://api.binance.us/api/v3/orders";

        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.GET();
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());

        if (response.statusCode() == 200) {

            JSONObject jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
            ObservableList<Order> ob = FXCollections.observableArrayList();

            for (
                    int i = 0;
                    i < jsonObject.getJSONArray("orders").length();
                    i++
            ) {

                JSONObject obj = (JSONObject) jsonObject.getJSONArray("orders").get(i);
                System.out.println(obj.toString());
                Order order = new Order(
                        obj.getString("clientTradeID"),
                        obj.getString("triggerCondition"),
                        obj.getString("createTime"),
                        obj.getString("price"),
                        obj.getString("clientTradeID"),
                        obj.getString("state"),
                        obj.getString("timeInForce"),
                        obj.getString("tradeID")
                );
                // orders.put(order, order);
                ob.add(order);
                logger.info(order.toString());


            }
            return new ListView<>(ob);
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);

            alert.setContentText(response.body());
            alert.showAndWait();
        }
        return null;

    }

    @Override
    public Account getAccounts() throws IOException, InterruptedException {

        String uriStr = "https://api.binance.us/api/v3/accounts";
        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.GET();
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JSONObject jsonObject;

        if (response.statusCode() == 200) {
            jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
            return new BinanceUsAccount(jsonObject);
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(response.body());
            alert.showAndWait();
        }

        return null;
    }

    @Override
    public void getPositionBook(TradePair tradePair) throws IOException, InterruptedException {

    }

    @Override
    public void getOpenOrder(TradePair tradePair) {

    }

    @Override
    public void getOrderHistory(TradePair tradePair) throws IOException, InterruptedException {

    }

    @Override
    public List<Order> getPendingOrders() {
        return null;
    }


    @Override
    public void cancelOrder(long orderID) throws IOException, InterruptedException {
        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/order/" + orderID)
        );
        requestBuilder.DELETE();

        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        JSONObject json = new JSONObject(response.body());
        System.out.println(json);
        System.out.println(json.get("status"));
        System.out.println(json.get("message"));
        System.out.println(json.get("serverTime"));
        System.out.println(json.get("time"));
        if (json.get("status").equals("ok")) {
            System.out.println(json.get("serverTime"));
            System.out.println(json.get("time"));
            isConnected = true;
        } else {
            isConnected = false;
        }

    }

    @Override
    public void cancelAllOrders() throws IOException, InterruptedException {
        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/orders")
        );
        requestBuilder.DELETE();

        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        JSONObject json = new JSONObject(response.body());
        System.out.println(json);
        System.out.println(json.get("status"));
        System.out.println(json.get("message"));
        System.out.println(json.get("serverTime"));
        System.out.println(json.get("time"));
        if (json.get("status").equals("ok")) {
            System.out.println(json.get("serverTime"));
            System.out.println(json.get("time"));
            isConnected = true;
        } else {
            isConnected = false;
        }


    }

    @Override
    public void cancelAllOpenOrders() throws IOException, InterruptedException {
        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/openOrders")
        );
        requestBuilder.DELETE();

        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        JSONObject json = new JSONObject(response.body());
        System.out.println(json);
        System.out.println(json.get("status"));
        System.out.println(json.get("message"));
        System.out.println(json.get("serverTime"));
        System.out.println(json.get("time"));
        if (json.get("status").equals("ok")) {
            System.out.println(json.get("serverTime"));
            System.out.println(json.get("time"));
            isConnected = true;
        } else {
            isConnected = false;
        }

    }

    @Override
    public ListView<Order> getOrderView() throws IOException, InterruptedException {
        ListView<Order> orders = new ListView<>();

        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/orders")
        );

        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        System.out.println(json);
        System.out.println(json.get("orders").toString());
//[
//  {
//    "symbol": "LTCBTC",
//    "orderId": 1,
//    "orderListId": -1, //Unless OCO, the value will always be -1
//    "clientOrderId": "myOrder1",
//    "price": "0.1",
//    "origQty": "1.0",
//    "executedQty": "0.0",
//    "cummulativeQuoteQty": "0.0",
//    "status": "NEW",
//    "timeInForce": "GTC",
//    "type": "LIMIT",
//    "side": "BUY",
//    "stopPrice": "0.0",
//    "icebergQty": "0.0",
//    "time": 1499827319559,
//    "updateTime": 1499827319559,
//    "isWorking": true,
//    "origQuoteOrderQty": "0.000000",
//    "selfTradePreventionMode": "NONE"
//  }
//]
        if (response.statusCode() == 200) {
            ArrayNode arrayNode = (ArrayNode) json.get("orders");
            for (int i = 0; i < arrayNode.size(); i++) {

                JSONObject obj = new JSONObject(arrayNode.get(i).toString());
                Order order = new Order(

                        obj.getString("price"),
                        obj.getString("timeInForce"),
                        obj.getString("symbol"),
                        obj.getString("orderId"),
                        obj.getString("orderListId"),
                        obj.getString("clientOrderId"),
                        obj.getString("origQty"),
                        obj.getString("executedQty"),
                        obj.getString("cummulativeQuoteQty"),
                        obj.getString("status"),
                        obj.getString("type"),
                        obj.getString("side"),
                        obj.getString("stopPrice"),
                        obj.getString("icebergQty"),
                        obj.getString("time"),
                        obj.getString("updateTime"),
                        obj.getString("isWorking"),
                        obj.getString("origQuoteOrderQty"),
                        obj.getString("selfTradePreventionMode")


                );
                System.out.println(order);
                orders.getItems().add(order);
                logger.info(order.toString());

            }

        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(response.body());
            alert.showAndWait();
            return null;
        }

        return orders;


    }

    @Override
    public List<OrderBook> getOrderBook(TradePair tradePair) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(url + "orderBook"));
        requestBuilder.GET();
        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );
        JsonNode json = OBJECT_MAPPER.readTree(response.body());
        System.out.println(json);
        System.out.println(json.get("asks").toString());
        System.out.println(json.get("bids").toString());
        System.out.println(json.get("timestamp").toString());
        System.out.println(json.get("datetime").toString());
        System.out.println(json.get("nonce").toString());
        System.out.println(json.get("serverTime").toString());
        System.out.println(json.get("status").toString());
        System.out.println(json.get("symbol").toString());
        System.out.println(json.get("type").toString());
        System.out.println(json.get("side").toString());
        System.out.println(json.get("price").toString());
        System.out.println(json.get("amount").toString());
        System.out.println(json.get("orderId").toString());
        System.out.println(json.get("origQty").toString());
        System.out.println(json.get("executedQty").toString());

        return null;

    }

    public String getAccountId() {
        return accountId;
    }

    public void setAccountId(String accountId) {
        this.accountId = accountId;
    }

    public String getApiSecret() {
        return apiSecret;
    }

    public void setApiSecret(String apiSecret) {
        this.apiSecret = apiSecret;
    }


    public static abstract class BinanceUsCandleDataSupplier extends CandleDataSupplier {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first trade
        String url = "https://api.binance.us/api/v3/";

        BinanceUsCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        }


        @Override
        public List<CandleData> getCandleData() {
            return new ArrayList<>();
        }

        @Override
        public Future<List<CandleData>> get() {
            if (endTime.get() == -1) {
                endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
            }


            String x;
            String str;
            if (secondsPerCandle < 3600) {
                x = String.valueOf(secondsPerCandle / 60);
                str = "m";
            } else if (secondsPerCandle < 86400) {
                x = String.valueOf((secondsPerCandle / 3600));
                str = "h";
            } else if (secondsPerCandle < 604800) {
                x = String.valueOf(secondsPerCandle / 86400);
                str = "d";
            } else if (secondsPerCandle < 2592000) {
                x = String.valueOf((secondsPerCandle / 604800));
                str = "w";
            } else {
                x = String.valueOf((secondsPerCandle * 7 / 2592000 / 7));
                str = "M";
            }
            String timeFrame = x + str;

            int startTime = Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA);

            String uriStr = url + "klines?symbol=" + tradePair.toString('/') +
                    "&interval=" + timeFrame;

            if (startTime == EARLIEST_DATA) {// signal more data is false
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            requestBuilder.uri(URI.create(uriStr));
            //requestBuilder.header("CB-AFTER", String.valueOf(afterCursor.get()));
            return HttpClient.newHttpClient().sendAsync(
                            requestBuilder.build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {
                        Log.info("Binance Us response: -->{}", response);
                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response);
                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (!res.isEmpty()) {


                            if (res.has("message")) {
                                System.out.println(res.get("message").asText());
                                return
                                        Collections.emptyList();
                            }
                            // Remove the current in-progress candle


                            logger.info("Binance Us response: --> {}", res);
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
                                logger.info(candleData.toString());
                            }
                            logger.info(
                                    "Fetched candle data for " + tradePair.toString('/') + " from " + startTime + " to " + endTime.get() + " in " + secondsPerCandle + " seconds"
                            );

                            candleData.sort(Comparator.comparingInt(CandleData::getOpenTime));
                            return candleData;
                        } else {
                            return Collections.emptyList();
                        }
                    });
        }

            public abstract CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle (TradePair
            tradePair, Instant currentCandleStartedAt,long secondsIntoCurrentCandle, int secondsPerCandle);

            public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil (TradePair tradePair, Instant stopAt);
        }

}