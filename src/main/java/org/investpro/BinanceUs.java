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
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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


public class BinanceUs extends Exchange {

    private static final Logger logger = LoggerFactory.getLogger(BinanceUs.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    static TradePair tradePair;
    private static final HttpClient client = HttpClient.newHttpClient();
    private static boolean isConnected;
    private String api_key;
    private String accountId;
    private final AtomicReference<String> url = new AtomicReference<>("https://api.binance.us/api/v3/");
    private String apiSecret;

    public BinanceUs(String apiKey, String apiSecret) {
        super(binanceUsWebSocket(apiKey, apiSecret));


        this.api_key = apiKey;
        this.apiSecret = apiSecret;


        requestBuilder.header("X-MBX-APIKEY", apiKey);
        requestBuilder.header("X-MBX-APISECRET", apiSecret);
        requestBuilder.header("Signature", signature(apiKey, apiSecret));
        requestBuilder.header("Timestamp", String.valueOf(nanoTime()));


        logger.info("BinanceUs " + nanoTime());
        this.api_key = apiKey;
        isConnected = false;


    }

    private static @NotNull ExchangeWebSocketClient binanceUsWebSocket(String apiKey, String apiSecret) {

        BinanceUsWebSocket binanceUsWebSocket = new BinanceUsWebSocket();


        return binanceUsWebSocket;
    }

    private @NotNull String signature(String apiKey, String apiSecret) {
        return "sha256=" + Base64.getEncoder().encodeToString((apiKey + ":" + apiSecret).getBytes());
    }

    //api/v3/account
    @Override
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
                        jsonNode.get("commissionRates").get("seller").asDouble())
                ;
                account.setPermissions(jsonNode.get("permissions").get(0).asText());


            }
        } else {
            logger.error("BinanceUs " + nanoTime());
            logger.error("BinanceUs " + response.statusCode());
            logger.error("BinanceUs " + response.body());
            new Message(
                    "BinanceUs " + nanoTime() +
                            "BinanceUs " + response.statusCode(),
                    "BinanceUs " + response.body()
            );


        }
        return new ArrayList<>();

    }

    //GET /sapi/v1/asset/query/trading-fee
    @Override
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
            new Message(
                    "BinanceUs " + nanoTime() +
                            "BinanceUs " + response.statusCode(),
                    "BinanceUs " + response.body()
            );

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
            new Message(
                    "BinanceUs " + nanoTime() +
                            "BinanceUs " + response.statusCode(),
                    "BinanceUs " + response.body()
            );
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

    @Override
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
                "BINANCE_US";

        }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair);
    }



    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return
                new BinanceUsWebSocket();


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

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return null;
    }




    /**
     * This method only needs to be implemented to support live syncing.
     */



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
    public void onError(@NotNull Exception ex) {
        System.out.println("Error");
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

    }

    @Override
    public String getSymbol() {
        return tradePair.toString('/');
    }

    @Override
    public double getLivePrice(@NotNull TradePair tradePair) {
        requestBuilder.uri(URI.create("https://api.binance.us/api/v3/prices/" + tradePair.toString('/') + "/ticker"));
        requestBuilder.GET().build();

        client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).thenApply(HttpResponse::body)
                .thenApply(response -> {
                    System.out.println(response);
                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                        logger.info(response);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }

                    if (res.isEmpty()) {
                        return -1;
                    } else {
                        if (res.get(0).has("price")) {
                            return res.get(0).get("price").asDouble();
                        }
                    }


                    return 0;
                }).thenAccept(out::println);


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

        String currency = tradePair.getBaseCurrency().code;
        String address
                = "234567";
        String addressTag = tradePair.getBaseCurrency().code;
        requestBuilder.uri(
                URI.create("https://api.binance.us/" +
                        "api/v3/withdraw" +
                        "?currency=" + currency +
                        "&address=" + address +
                        "&amount=" + value +
                        "&addressTag=" + addressTag)
        );
        String body =
                requestBuilder.build().method();
        try {
            client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            ).request().expectContinue();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        System.out.println(body);
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));


    }


    @Override
    public void createOrder(@NotNull TradePair tradePair, Side buy, ENUM_ORDER_TYPE market, double quantity, int i, @NotNull Date timestamp, long orderID, double stopPrice, double takeProfitPrice) throws IOException, InterruptedException {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.binance.us/" +
                "api/v3/orders/" + tradePair.toString('/');

        String[] body =
                {
                        "{\"symbol\":\"" + tradePair.toString('/') + "\",\"side\":\"" + buy + "\",\"type"
                                + "\":\"" + market + "\",\"quantity\":\"" + quantity + "\",\"timestamp\":\"" + timestamp + "\",\"orderId\":\"" + orderID + "\",\"stopPrice\":\"" + stopPrice + "\",\"takeProfitPrice\":\"" + takeProfitPrice + "\"}"

                };

        System.out.println(uriStr);

        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                Arrays.toString(body)


        ));


        client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        ).request().expectContinue();


    }

    @Override
    public void closeAll() throws IOException, InterruptedException {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.binance.us/" +
                "api/v3/orders";

        System.out.println(uriStr);

        requestBuilder.DELETE();

        String body =
                requestBuilder.build().method();
        client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        ).request().expectContinue();

        System.out.println(body);
    }

    @Override
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

    @Override
    public ConcurrentHashMap<String, Double> getLiveTickerPrice() throws IOException, InterruptedException {
        return null;
    }

    @Override
    double getLiveTickerPrices() throws IOException, InterruptedException {

        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/ticker/price")
        );

        HttpResponse<String> response =
                client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );
        System.out.println(response.statusCode());

        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));
        System.out.println(response.body());

        if (response.statusCode() == 200) {
            JSONObject jsonObject1 = getJSON();
            System.out.println(jsonObject1.toString(4));
            System.out.println(response.body());
            JSONArray jsonArray = jsonObject1.getJSONArray("symbols");
            System.out.println(jsonArray.toString(4));
            JSONObject jsonObject2 = jsonArray.getJSONObject(0);
            System.out.println(jsonObject2.toString(4));
            System.out.println(response.body());
            JSONObject jsonObject3 = jsonObject2.getJSONObject("price");
            System.out.println(jsonObject3.toString(4));
            System.out.println(response.body());
            JSONObject jsonObject4 = jsonObject3.getJSONObject("price");
            System.out.println(jsonObject4.toString(4));
            System.out.println(response.body());
            JSONObject jsonObject5 = jsonObject4.getJSONObject("price");
            System.out.println(jsonObject5.toString(4));

            return jsonObject5.getDouble("price");
        } else {
            new Message(
                    Message.MessageType.ERROR,
                    response.body()
            );
        }

        return 0;
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


        File file;
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
            currencies.add(new CryptoCurrency(
                    symbol, symbol, symbol,
                    Integer.parseInt(baseAssetPrecision), symbol, symbol) {
                @Override
                public int compareTo(@NotNull Currency o) {
                    return 0;
                }

                @Override
                public int compareTo(java.util.@NotNull Currency o) {
                    return 0;
                }
            });





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
        Currency.registerCurrencies(currencies);
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
            new Message(
                    Alert.AlertType.ERROR.name(),

                    response.body()
            );
        }
    }

    @Override
    public void closeAllOrders() {
        URI uri = URI.create("https://api.binance.us/api/v3/cancel");
        requestBuilder.uri(uri);
        requestBuilder.DELETE();
        HttpResponse<String> response;
        try {
            response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (response.statusCode() == 200) {
            JSONObject json = new JSONObject(response.body());
            System.out.println(json);
            System.out.println(json.get("orderId"));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            new Message(
                    Alert.AlertType.ERROR.name(),
                    response.body()
            );
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


        File file;
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


            CryptoCurrency currency = new CryptoCurrency(
                    baseAsset,
                    baseAsset,
                    baseAsset,
                    Integer.parseInt(baseAssetPrecision),
                    baseAsset, baseAsset


            );

//            if (java.util.Currency.getAvailableCurrencies().stream().anyMatch(c -> c.getCurrencyCode().equals(currency.code))) {
//                Log.info(
//                        "Currency already registered: " , currency.code
//                );
//                //skipping if currency is already in the list
//            }else {
//                //registering the currency


            Currency.registerCurrency(currency);


            data.add(
                    baseAsset + "/" + quoteAsset
            );
        }

        return data;

    }

    @Override
    public void connect(String text, String text1, String userIdText) {
        requestBuilder.uri(URI.create("https://api.binance.us/api/v3/"));

        api_key = text;

        HttpResponse<String> response = null;
        try {
            response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        JSONObject json = new JSONObject(response);
//        System.out.println(json);
//        System.out.println(json.get("status"));
//        System.out.println(json.get("message"));
//        System.out.println(json.get("serverTime"));
//        System.out.println(json.get("time"));
        if (json.get("status").equals("ok")) {
            System.out.println(json.get("serverTime"));
            System.out.println(json.get("time"));
            isConnected = true;
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
            new Message("error", response.body().split("\n")[0]);
        }
        return null;

    }

    @Override
    public Account getAccounts() throws IOException, InterruptedException {

        String uriStr = "https://api.binance.us/api/v3/account";
        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.GET();
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JSONObject jsonObject;

        if (response.statusCode() == 200) {
            jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
            new BinanceUsAccount(jsonObject);
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
        String uriStr = "https://api.binance.us/api/v3/depth";
        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.GET();
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JSONObject jsonObject;

        if (response.statusCode() == 200) {
            jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));

        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            new Message("error", response.body());
        }

    }

    @Override
    public void getOpenOrder(TradePair tradePair) {
        String uriStr = "https://api.binance.us/api/v3/openOrders";
        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.GET();
        HttpResponse<String> response;
        try {
            response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        JSONObject jsonObject;

        if (response.statusCode() == 200) {
            jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            new Message("error", response.body());
        }

    }

    @Override
    public void getOrderHistory(TradePair tradePair) throws IOException, InterruptedException {
        String uriStr = "https://api.binance.us/api/v3/orderHistory";
        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.GET();
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        JSONObject jsonObject;

        if (response.statusCode() == 200) {
            jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            new Message("error", response.body());
        }

    }

    @Override
    public List<Order> getPendingOrders() {
        String uriStr = "https://api.binance.us/api/v3/pendingOrders";
        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.GET();
        HttpResponse<String> response = null;
        try {
            response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        JSONObject jsonObject;

        if (response.statusCode() == 200) {
            jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
            return null;
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            new Message("error", response.body());
        }

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
    public void cancelAllOrders() {
        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/orders")
        );
        requestBuilder.DELETE();

        HttpResponse<String> response;
        try {
            response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
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
    public void cancelAllOpenOrders() {
        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/openOrders")
        );
        requestBuilder.DELETE();

        HttpResponse<String> response = null;
        try {
            response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
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
    public ListView<Order> getOrderView() {
        ListView<Order> orders = new ListView<>();

        requestBuilder.uri(
                URI.create("https://api.binance.us/api/v3/orders")
        );

        HttpResponse<String> response;
        try {
            response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        JsonNode json;
        try {
            json = OBJECT_MAPPER.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
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
            new Message("error", response.body());
            return null;
        }

        return orders;


    }

    @Override
    public List<OrderBook> getOrderBook(TradePair tradePair) {
        requestBuilder.uri(URI.create(url + "orderBook"));
        requestBuilder.GET();
        HttpResponse<String> response;
        try {
            response = client.send(
                    requestBuilder.build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
        JsonNode json = null;
        try {
            json = OBJECT_MAPPER.readTree(response.body());
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
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


    public static class BinanceUsCandleDataSupplier extends CandleDataSupplier {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first trade
        String url = "https://api.binance.us/api/v3/";

        BinanceUsCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(300, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        }


        @Override
        public List<CandleData> getCandleData() {
            List<CandleData> candles = new ArrayList<>();


            requestBuilder.uri(URI.create(url + "klines?symbol=" + tradePair.toString('/')));
            requestBuilder.GET();
            HttpResponse<String> response = null;
            try {
                response = client.send(
                        requestBuilder.build(),
                        HttpResponse.BodyHandlers.ofString()
                );
            } catch (IOException | InterruptedException e) {
                throw new RuntimeException(e);
            }
            JsonNode json = null;
            try {
                json = OBJECT_MAPPER.readTree(response.body());
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
            System.out.println(json);
            System.out.println(json.get("klines").toString());
            System.out.println(json.get("time").toString());


            for (int i = 0; i < json.get("klines").size(); i++) {
                JSONObject obj = new JSONObject(json.get("klines").get(i).toString());
                CandleData candle = new CandleData(

                        obj.getDouble("open"),
                        obj.getDouble("high"),
                        obj.getDouble("low"),
                        obj.getDouble("close"),
                        obj.getInt("time"),

                        obj.getInt("volume")
                );
                System.out.println(candle);
                candles.add(candle);
            }


            return candles;
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
                TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
            String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
                    currentCandleStartedAt, ZoneOffset.UTC));
            // Get the closest supported granularity to the ideal granularity.


            String endDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    .format(LocalDateTime.ofEpochSecond(new Date().getTime(), 23, ZoneOffset.UTC));

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


        @Override
        public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
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
                            futureResult.completeExceptionally(new RuntimeException("InvestPro.CurrencyDataProvider.BinanceUs trades response did not contain header \"date\": " + response.body()));

                        }
                        afterCursor.setValue(Integer.valueOf((response.headers().firstValue("CB-AFTER").get())));
                        JsonNode tradesResponse = OBJECT_MAPPER.readTree(response.body());
                        if (!tradesResponse.isArray()) {
                            futureResult.completeExceptionally(new RuntimeException(
                                    "InvestPro.CurrencyDataProvider.BinanceUs trades response was not an array!"));
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

                                    logger.info(
                                            "tradesBeforeStopTime: " + tradesBeforeStopTime);

                                }
                            }
                        }
                    } catch (IOException | InterruptedException ex) {
                        Log.error("ex: " + ex);
                        futureResult.completeExceptionally(ex);
                    }
                }
            });

            return futureResult;
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


            return HttpClient.newHttpClient().sendAsync(
                            requestBuilder.GET().build(),
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

    }

}