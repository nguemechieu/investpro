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
import javafx.scene.Node;
import javafx.scene.control.ListView;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;


import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Kraken extends Exchange {

    private static final Logger logger = LoggerFactory.getLogger(Kraken.class);


    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    //private static final String ur0 = "wss://stream.binance.us:9443";
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private String telegramToken;
    protected String PASSPHRASE = "w73hzit0cgl";
    protected String API_SECRET = "FEXDflwq+XnAU2Oussbk1FOK7YM6b9A4qWbCw0TWSj0xUBCwtZ2V0MVaJIGSjWWtp9PjmR/XMQoH9IZ9GTCaKQ==";
    String API_KEY0 = "39ed6c9ec56976ad7fcab4323ac60dac";
    private final HttpClient client=HttpClient.newHttpClient();
     TradePair tradePair;
     Instant currentCandleStartedAt;
    long secondsIntoCurrentCandle;
    int secondsPerCandle;

    public Kraken( String telegramToken, String binanceUsApiKey) {
        super(null);
        this.telegramToken = telegramToken;


        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36");
        requestBuilder.header("Origin", "https://api.kraken.com");
        requestBuilder.header("Referer", "https://www.kraken.com");
        requestBuilder.header("Sec-Fetch-Dest", "empty");
        requestBuilder.header("Sec-Fetch-Mode", "cors");
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Authorization", binanceUsApiKey);

        logger.info("BinanceUs " );
    }


    public Kraken(String coinbaseApiKey, String coinbaseSecret, String telegramToken) {
        super(null);
        this.telegramToken = telegramToken;
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36");
        requestBuilder.header("Origin", "https://api.kraken.com");
        requestBuilder.header("Referer", "https://www.kraken.com");
        requestBuilder.header("Sec-Fetch-Dest", "empty");
        requestBuilder.header("Sec-Fetch-Mode", "cors");
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Authorization", coinbaseApiKey + ":" + coinbaseSecret);
        logger.info("Kraken " );

    }

    @Override
    public String getName() {
        return
                "KRAKEN";
    }

    @Override
    public KrakenCandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return
                new KrakenCandleDataSupplier(secondsPerCandle, tradePair) {
                    @Override
                    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(TradePair tradePair1, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle1) {
                        return
                                CompletableFuture.supplyAsync(Optional::empty);
                    }

                    @Override
                    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair1, Instant stopAt) {
                        return null;
                    }

                    @Override
                    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair1, Instant stopAt, boolean isTrade) {
                        return null;
                    }

                    @Override
                    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle1, TradePair tradePair1) {
                        return null;
                    }
                };
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
        return null;
    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        return
                Set.of(
                        60, 60 * 5, 60 * 15, 3600, 3600 * 6, 3600 * 24,
                        3600 * 24 * 7, 3600 * 24 * 30, 3600 * 24 * 30 * 7, 3600 * 24 * 30 * 365
                );
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
        CompletableFuture.runAsync(() -> {
            IntegerProperty afterCursor = new SimpleIntegerProperty(0);
            List<Trade> tradesBeforeStopTime = new ArrayList<>();

            // For Public Endpoints, our rate limit is 3 requests per second, up to 6 requests per second in
            // burst.
            // We will know if we get rate limited if we get a 429 response code.
            for (int i = 0; !futureResult.isDone(); i++) {
                String uriStr = "https://api.binance.us/api/v3";
                uriStr += "trades?symbol=" + tradePair.toString('/') + "/";

                if (i != 0) {
                    uriStr += "?after=" + afterCursor.get();
                }
                requestBuilder.uri(URI.create(uriStr));
                try {
                    HttpResponse<String> response = HttpClient.newHttpClient().send(requestBuilder.build()
                            ,
                            HttpResponse.BodyHandlers.ofString());

                    Log.info("response headers: ", response.headers().toString());
                    if (response.headers().firstValue("CB-AFTER").isEmpty()) {
                        futureResult.completeExceptionally(new RuntimeException(
                                "cryptoinvestor.cryptoinvestor.CurrencyDataProvider.Oanda trades response did not contain header \"CB-AFTER\": " + response));
                        return;
                    }

                    afterCursor.setValue(Integer.valueOf((response.headers().firstValue("CB-AFTER").get())));

                    JsonNode tradesResponse = OBJECT_MAPPER.readTree(response.body());

                    if (!tradesResponse.isArray()) {
                        futureResult.completeExceptionally(new RuntimeException(
                                "cryptoinvestor.cryptoinvestor.CurrencyDataProvider.Oanda trades response was not an array!"));
                    }
                    if (tradesResponse.isEmpty()) {
                        futureResult.completeExceptionally(new IllegalArgumentException("tradesResponse was empty"));
                    } else {
                        for (int j = 0; j < tradesResponse.size(); j++) {
                            JsonNode trade = tradesResponse.get(j);
                            long time = Date.from(Instant.from(ISO_INSTANT.parse(trade.get("time").asText()))).getTime();
                            if (stopAt.isAfter(Instant.ofEpochSecond(time))) {
                                futureResult.complete(tradesBeforeStopTime);
                                break;
                            } else {
                                tradesBeforeStopTime.add(new Trade(tradePair,
                                        trade.get("price").asDouble(),
                                        trade.get("size").asDouble(),
                                        Side.getSide(trade.get("side").asText()), trade.get("trade_id").asLong(), time));

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
    public double getTradingFee() throws IOException, InterruptedException {
        return 0;
    }

    @Override
    public void cancelOrder(@NotNull TradePair tradePair, long orderId) throws IOException, InterruptedException {

        String uriStr = "https://api.binance.us/api/v3";
        uriStr += "order?symbol=" + tradePair.toString('/') + "/";
        uriStr += "&orderId=" + orderId;
        requestBuilder.uri(URI.create(uriStr));
        HttpResponse<String> response = HttpClient.newHttpClient().send(requestBuilder.build()
              ,
                HttpResponse.BodyHandlers.ofString());
        Log.info("response headers: ", response.headers().toString());

    }

    /**
     * This method only needs to be implemented to support live syncing.
     */
    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle(
            TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        this.tradePair = tradePair;
        this.currentCandleStartedAt = currentCandleStartedAt;
        this.secondsIntoCurrentCandle = secondsIntoCurrentCandle;
        this.secondsPerCandle = secondsPerCandle;
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

        return HttpClient.newHttpClient().sendAsync(
                        HttpRequest.newBuilder()
                                .uri(URI.create(
                                        "https://api.kraken.com/api/v3/klines?symbol=" + tradePair.toString('/') + "&interval=" + timeFrame
                                ))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                        JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                        logger.info("kraken response: ", response);
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
            URL url = new URL("https://api.kraken.com/v2/exchange-rates");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Accept-Charset", "utf-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10)");
            conn.setRequestProperty("CB-ACCESS-KEY", API_KEY0);//    API key as a string
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


            //Put data into json file
            jsonObject = new JSONObject(response.toString());

            logger.info(jsonObject.toString(4));

            String rates;
            if (jsonObject.has("data")) {
                JSONObject dat = new JSONObject(jsonObject.getJSONObject("data").toString(4));
                if (dat.has("rates")) {
                    rates = dat.getJSONObject("rates").toString(4);
                    logger.info(rates);
                }

            }


        } catch (IOException e) {
            e.printStackTrace();
        }

        logger.info(jsonObject.toString(4));
        return jsonObject;
    }

    @Override
    public void onOpen(ServerHandshake handshake) {


        JSONObject jsonObject = getJSON();
        logger.info(jsonObject.toString(4));

    }

    @Override
    public void onMessage(String message) {

        JSONObject jsonObject = getJSON();
       logger.info(jsonObject.toString(4));

    }

    @Override
    public void onClose(int code, String reason, boolean remote) {

        JSONObject jsonObject = getJSON();
        logger.info(jsonObject.toString(4));

    }

    @Override
    public void onError(Exception ex) {

        JSONObject jsonObject = getJSON();
        logger.info(ex+"   "+jsonObject.toString(4));

    }

    @Override
    public String getSymbol() {
        return null;
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

    @Override
    public void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE market, double quantity, int i, @NotNull Date timestamp, long orderID, double stopPrice, double takeProfitPrice) throws IOException, InterruptedException {

    }

    @Override
    public @NotNull List<Currency> getAvailableSymbols() throws IOException, InterruptedException {
        return new ArrayList<>();
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, @NotNull Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

    }


    @Override
    public void closeAllOrders() {

    }

    @Override
    public List<String> getTradePair() throws IOException, InterruptedException, SQLException, ClassNotFoundException {
        requestBuilder.uri(URI.create(
                "https://api.kraken.com/0/public/Assets"
        ));

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        JSONObject jsonObject = new JSONObject(response.body());
        System.out.println(jsonObject.toString(4));
        List<TradePair> tradePairs = new ArrayList<>();
        JSONObject jsonArray = jsonObject.getJSONObject("result");
        for (int i = 0; i < jsonArray.length(); i++) {

            TradePair tradePair;
            if (jsonArray.has("altname"))    {
                tradePair = new TradePair(jsonArray.getString("altname"),"USD");
                tradePairs.add(tradePair);}
        }
        return null;
    }

    @Override
    public void connect(String text, String text1, String userIdText) {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

    }

    @Override
    public boolean isConnected() {
        return false;
    }

    @Override
    public Node getAllOrders() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public Account getAccounts() throws IOException, InterruptedException {
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
    public @NotNull List<Account> getAccount() throws IOException, InterruptedException {
        return null;
    }

    @Override
    public void cancelOrder(long orderID) throws IOException, InterruptedException {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

    }

    @Override
    public void cancelAllOrders() {

    }

    @Override
    public void cancelAllOpenOrders() {

    }

    @Override
    public ListView<Order> getOrderView() {
        ListView<Order> orders = new ListView<>();
        return orders;
    }

    @Override
    public List<OrderBook> getOrderBook(TradePair tradePair) {
        return null;
    }

    public void createMarketOrder(@NotNull TradePair tradePair, String side, double size) {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.binance.us/" +
                "api/v3/orders/" + tradePair.toString('/') +
                "?side=" + side +
                "&type=market" +
                "&quantity=" + size +
                "&price=" + jsonObject.getJSONObject("data").getJSONObject("rates").getDouble("USD");

        System.out.println(uriStr);


    }

    public void createOrder(TradePair tradePair, Side sell, ENUM_ORDER_TYPE market, double quantity, int i, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) {






    }

    public void closeAll() {
        JSONObject jsonObject = getJSON();
        logger.info(
                jsonObject.toString(4)
        );


    }

    public void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE stopLoss, Double quantity, double price, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) {





    }

    @Override
    public ConcurrentHashMap<String, Double> getLiveTickerPrice() throws IOException, InterruptedException {
        return null;
    }

    @Override
    double getLiveTickerPrices() throws IOException, InterruptedException {
        return 0;
    }

    public void CancelOrder(long orderID) throws IOException, InterruptedException {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.kraken.com/" +
                "api/v3/orders/" + orderID;

        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.header("Accept", "application/json");

        requestBuilder.method("DELETE",
                HttpRequest.BodyPublishers.noBody());

        HttpResponse<String> response= client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        System.out.println(response.statusCode());

        System.out.println(uriStr);
    }

    public String getTelegramToken() {
        return telegramToken;
    }

    public void setTelegramToken(String telegramToken) {
        this.telegramToken = telegramToken;
    }


    public static abstract class KrakenCandleDataSupplier extends CandleDataSupplier {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first trade

        KrakenCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
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

            String uriStr = "https://api.binance.us/api/v3/klines?symbol=" + tradePair.toString('/') + "&interval=" + timeFrame;

            if (startTime == EARLIEST_DATA) {
                // signal more data is false
                return CompletableFuture.completedFuture(Collections.emptyList());
            }
            requestBuilder.uri(URI.create(uriStr));
            //requestBuilder.header("CB-AFTER", String.valueOf(afterCursor.get()));
            return HttpClient.newHttpClient().sendAsync(
                            requestBuilder.build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {
                        Log.info("Binance response: -->", response);
                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response);
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
                            return Collections.emptyList();
                        }
                    });
        }

        public abstract CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle);

        public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt, boolean isTrade);
    }

}