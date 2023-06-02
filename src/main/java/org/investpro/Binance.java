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
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static java.lang.System.nanoTime;
import static java.lang.System.out;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class Binance extends Exchange {
    private static final Logger logger = LoggerFactory.getLogger(Binance.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private final String api_key;

    private final HttpClient client=HttpClient.newHttpClient();
    public Binance(  String apiKey, String apiSecret, String accountId) {
        super( null);
        requestBuilder.header("Content-Type", "application/json");
        requestBuilder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/90.0.4430.212 Safari/537.36");
        requestBuilder.header(  "Origin", "https://api.binance.com");
        requestBuilder.header("Referer", "https://api.binance.com");
        requestBuilder.header("Sec-Fetch-Dest", "empty");
        requestBuilder.header("Sec-Fetch-Mode", "cors");
        requestBuilder.header("Accept", "application/json");
        requestBuilder.header("Authorization",
                "Bearer " + apiKey
        );

        logger.info("BinanceUs " + nanoTime());


        this .api_key = apiKey;

    }



    public void createOrder(TradePair tradePair,double price1, ENUM_ORDER_TYPE type1, Side side1, double quantity1, double stopLoss1, double takeProfit1) throws IOException, InterruptedException {

        String url = "https://api.binance.com/api/v3/order";
        String body = "{\n" +
                "  \"symbol\": \"" + tradePair + "\",\n" +
                "  \"side\": \"" + side1 + "\",\n" +
                "  \"type\": \"" + type1 + "\",\n" +
                "  \"timeInForce\": \"GTC\",\n" +
                "  \"quantity\": " + quantity1 + ",\n" +
                "  \"price\": " + price1 + ",\n" +
                "  \"stopPrice\": " + stopLoss1 + ",\n" +
                "  \"icebergQty\": " + takeProfit1 + ",\n" +
                "  \"newOrderRespType\": \"FULL\",\n" +
                "  \"recvWindow\": 10000,\n" +
                "  \"timestamp\": \"" + LocalDateTime.now().format(DateTimeFormatter.ISO_INSTANT) + "\"\n" +
                "}";
        logger.info(url);
        logger.info(body);
        requestBuilder.uri(URI.create(url));
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body));
        HttpResponse<String> response =client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        logger.info(String.valueOf(response.statusCode()));
        logger.info(response.body());
    }

    @Override
    public String getName() {
        return
                "BinanceUs";

    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }


    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle() {
        return null;
    }


    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
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
public Set<Integer> getSupportedGranularities() {
    return
            new HashSet<>(Arrays.asList(
                    60, 60 * 5, 60 * 15, 3600, 3600 * 6, 3600 * 24,
                    3600 * 24 * 7, 3600 * 24 * 30, 3600 * 24 * 30 * 7, 3600 * 24 * 30 * 365

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
                String uriStr = "https://api.binance.com/api/v3";
                uriStr += "trades?symbol=" + tradePair.toString('/') + "/";

                if (i != 0) {
                    uriStr += "?after=" + afterCursor.get();
                }
                requestBuilder.uri(URI.create(uriStr));
                try {
                    HttpResponse<String> response = client.send(requestBuilder.build()
                            ,
                            HttpResponse.BodyHandlers.ofString());

                    Log.info("response headers-->: ", response.headers().toString());
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
                                        "https://api.binance.com/api/v3/klines?symbol=" + tradePair.toString('/') + "&interval=" + timeFrame
                                ))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    Log.info("BinanceUs response: ", response);
                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                        logger.info("Binance response: ", response);
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
            URL url = new URL("https://api.binance.us/api/v2/exchange-rates");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
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
    public @NotNull List<Currency> getAvailableSymbols() throws IOException, InterruptedException {
        requestBuilder.uri(URI.create("https://api.binance.us/api/v3/exchangeInfo"));
HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        System.out.println(response.body());
if (response.statusCode() == 200) {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    mapper.registerModule(new JavaTimeModule());
    JsonNode root = mapper.readTree(response.body());
    ArrayNode symbols = (ArrayNode) root.get("symbols");
    List<Currency> currencies = new ArrayList<>();
    for (int i = 0; i < symbols.size(); i++) {
        currencies.add(new Currency(CurrencyType.CRYPTO,
                symbols.get(i).get("baseAsset").asText(),
                symbols.get(i).get("quoteAsset").asText(),
                symbols.get(i).get("symbol").asText(),
                symbols.get(i).get("baseAssetPrecision").asInt(),
                symbols.get(i).get("filters").get(0).get("filterType").asText(),
                ""
                ) {
            @Override
            public int compareTo(@NotNull Currency o) {
                return 0;
            }

            @Override
            public int compareTo(@NotNull Currency o) {
                return 0;
            }
        });
    }
    return currencies;
} else {
    logger.info(
            response.statusCode() + " " + response.body()
    );
    return new ArrayList<>();
}


    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size,
                            @NotNull Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {
    }

    @Override
    public void closeAllOrders() {

    }

    @Override
    public List<String> getTradePair() throws IOException, InterruptedException, SQLException, ClassNotFoundException {

        Set<TradePair> tradePairs =
                new HashSet<>();
        requestBuilder.uri(URI.create("https://api.binance.us/api/v3/exchangeInfo"));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.body());
        if (response.statusCode() == 200) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
            mapper.registerModule(new JavaTimeModule());
            JsonNode root = mapper.readTree(response.body());
            ArrayNode symbols = (ArrayNode) root.get("symbols");
            List<Currency> currencies = new ArrayList<>();
            for (int i = 0; i < symbols.size(); i++) {
                currencies.add(new Currency(CurrencyType.CRYPTO,
                        symbols.get(i).get("baseAsset").asText(),
                        symbols.get(i).get("baseAsset").asText(),
                        symbols.get(i).get("symbol").asText(),
                        symbols.get(i).get("baseAssetPrecision").asInt(),
                        symbols.get(i).get("filters").get(0).get("filterType").asText(),
                        ""
                ) {
                    @Override
                    public int compareTo(@NotNull Currency o) {
                        return 0;
                    }

                    @Override
                    public int compareTo(@NotNull Currency o) {
                        return 0;
                    }
                });
                tradePairs.add(new TradePair(
                        symbols.get(i).get("baseAsset").asText(),
                        symbols.get(i).get("quoteAsset").asText()
                ));
            }
        }


        return
                null;
    }

    @Override
    public void connect(String text, String text1, String userIdText) {

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

    }

    @Override
    public void cancelAllOrders() {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.binance.com/" +
                "api/v3/allOrders";

        System.out.println(uriStr);

    }

    @Override
    public void cancelAllOpenOrders() {

    }

    @Override
    public ListView<Order> getOrderView() {
        return new ListView<>();
    }

    @Override
    public List<OrderBook> getOrderBook(TradePair tradePair) {
        return null;
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
                "&price=" +price+"&stopLoss=" +stopPrice+"&takeProfit=" +takeProfit;

        System.out.println(uriStr);


    }

    public void CancelOrder(long orderID) {
    }

    public void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE market, double quantity, int i, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) {
    }

    public void closeAll() {
    }

    public void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE stopLoss, Double quantity, double price, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) {

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

    public String getApi_key() {
        return api_key;
    }


    public static abstract class BinanceUsCandleDataSupplier extends CandleDataSupplier {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first trade

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

            String uriStr = "https://api.binance.com/api/v3/klines?symbol=" + tradePair.toString('/') + "&interval=" + timeFrame;

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
                        Log.info("Binance response: -->{}", response);
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


                            logger.info("Binance response: --> {}", res);
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

        public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt, boolean isTrade);
    }

}