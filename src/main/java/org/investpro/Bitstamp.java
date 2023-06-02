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
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import org.java_websocket.handshake.ServerHandshake;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static java.lang.System.out;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME;

public class Bitstamp extends Exchange {
    private final String apiKey;

    public Bitstamp(String bitstamp_key, String bitstamp_secret, String coinbaseSecret) {

        super( null);
        this.apiKey = bitstamp_key;


    }

    @Override
    public String getName() {
        return
                "Bitstamp";
    }





    public void createOrder( TradePair tradePair,double price,ENUM_ORDER_TYPE type, Side side, double quantity, double stopLoss, double takeProfit) {
        try {
            String url = "https://api.bitstamp.net/v2/order/new";
            String payload = String.format("{\"pair\": \"%s\", \"type\": \"%s\", \"side\": \"%s\", \"price\": %f, \"quantity\": %f, \"stop_loss\": %f, \"take_profit\": %f}",

                    type.toString(),
                    side.toString(),
                    price,
                    quantity,
                    stopLoss,
                    takeProfit);
            sendRequest(url, payload);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelOrder(long orderId) {}

    public void cancelAllOrders() {}

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


    private void sendRequest(String url, String payload) {

    }

    public void CancelOrder(long orderID) {
    }

    public void closeAll() {
    }


    @Override
    public BitstampCandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return
                new BitstampCandleDataSupplier(secondsPerCandle, tradePair) {
                    @Override
                    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
                        return null;
                    }

                    @Override
                    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
                        return
                                CompletableFuture.completedFuture(Optional.empty());
                    }

                    @Override
                    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
                        return null;
                    }

                    @Override
                    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt, boolean autoTrade) {
                        return
                                CompletableFuture.completedFuture(Collections.emptyList());
                    }
                };
    }

    @Override
    public CompletableFuture<Optional<InProgressCandleData>> fetchCandleDataForInProgressCandle() {
        return null;
    }


    private @Nullable String timestampSignature(
            String apiKey,
            String passphrase
    ) throws NoSuchAlgorithmException {
        Objects.requireNonNull(apiKey);
        Objects.requireNonNull(passphrase);

        String timestamp = new Date().toString();
        String stringToSign = timestamp + "\n" + apiKey + "\n" + passphrase;

//        try {
//            byte[] hash = MessageDigest.getInstance("SHA-256").digest(stringToSign.getBytes());
//            return Base64.getEncoder().encodeToString(hash);
//        } catch (Exception e) {
//            e.printStackTrace();
//            return null;
//        }

        return Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(stringToSign.getBytes()));
    }


    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return null;
    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        return Set.of(60, 60 * 5, 60 * 15, 3600, 3600 * 6, 3600 * 24, 3600 * 24 * 7, 3600 * 24 * 30, 3600 * 24 * 30 * 7, 3600 * 24 * 30 * 365);
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
                String uriStr = "https://api.pro.coinbase.com/";
                uriStr += "products/" + tradePair.toString('-') + "/trades";

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
                                "Coinbase trades response did not contain header \"CB-AFTER\": " + response));
                        return;
                    }

                    afterCursor.setValue(Integer.valueOf((response.headers().firstValue("CB-AFTER").get())));

                    ObjectMapper mapper = new ObjectMapper();
                    JsonNode tradesResponse = mapper.readTree(response.body());

                    if (!tradesResponse.isArray()) {
                        futureResult.completeExceptionally(new RuntimeException("coinbase trades response was not an array!"));


                    } else if (tradesResponse.isEmpty()) {
                        futureResult.completeExceptionally(new IllegalArgumentException("tradesResponse was empty"));
                    } else if (tradesResponse.has("message")) {


                        Alert alert = new Alert(Alert.AlertType.WARNING);
                        alert.setTitle("Coinbase Error");
                        alert.setHeaderText("Coinbase Error");
                        alert.setContentText(tradesResponse.get("message").asText());
                        alert.showAndWait();


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
            @NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        String startDateString1 = ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
                currentCandleStartedAt, ZoneOffset.UTC));
        long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
        // Get the closest supported granularity to the ideal granularity.
        int actualGranularity = getSupportedGranularities().stream()
                .min(Comparator.comparingInt(i -> (int) Math.abs(i - idealGranularity)))
                .orElseThrow(() -> new NoSuchElementException("Supported granularities was empty!"));

        return HttpClient.newHttpClient().sendAsync(
                        HttpRequest.newBuilder()
                                .uri(URI.create(String.format(
                                        "https://api.pro.coinbase.com/products/%s/candles?granularity=%s&start=%s",
                                        tradePair.toString('-'), actualGranularity, startDateString1)))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    Log.info("Coinbase response: ", response);
                    JsonNode res;
                    try {
                        ObjectMapper mapper = new ObjectMapper();
                        res = mapper.readTree(response);
                        if (res.has("message")) {


                            Alert alert = new Alert(Alert.AlertType.WARNING);
                            alert.setTitle("Coinbase Error");
                            alert.setHeaderText("Coinbase Error");
                            alert.setContentText(res.get("message").asText());
                            alert.showAndWait();


                        }

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


    public JSONObject getJSON() {

        JSONObject jsonObject = new JSONObject();
        try {
            var url = new URL("https://api.coinbase.com/v2/exchange-rates");
            HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("charset", "utf-8");
            conn.setRequestProperty("Accept-Charset", "utf-8");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10)");
            conn.setRequestProperty("CB-ACCESS-KEY", apiKey);//    API key as a string
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
        return new ArrayList<>();
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, @NotNull Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {

    }


    @Override
    public void closeAllOrders() {

    }

    @Override
    public List<String> getTradePair() throws IOException, InterruptedException {

        requestBuilder.uri(URI.create("https://api.coinbase.com/v2/exchange-rates"));

        return null;
    }

    @Override
    public void connect(String text, String text1, String userIdText) {

    }

    @Override
    public boolean isConnected() {
        return false;
    }

    HttpClient client =  HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
    // Get all orders
    //       GET
    //https://api.exchange.coinbase.com/orders

    public Node getAllOrders() throws IOException, InterruptedException {
        String uriStr = "https://api.bitstamp.com/orders";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        requestBuilder.uri(URI.create(uriStr));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());

        if (response.statusCode() == 200) {
            JSONObject jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
        }
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


    //  Get single order
    //      GET
    //https://api.exchange.coinbase.com/orders/{order_id}


    public void getOrder(String orderId) throws IOException, InterruptedException {
        String uriStr = "https://api.bitstamp.com/orders/" + orderId;

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        requestBuilder.uri(URI.create(uriStr));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());

        if (response.statusCode() == 200) {
            JSONObject jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
        }

    }
    // Cancel an order
    //       DELETE
    //https://api.exchange.coinbase.com/orders/{order_id}
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    public void cancelOrder(String orderId) throws IOException, InterruptedException {

        String uriStr = "https://api.bitstamp.com/orders/" + orderId;
        requestBuilder.DELETE();
        requestBuilder.uri(URI.create(uriStr));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());
        if (response.statusCode() == 200) {
            JSONObject jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
        }
    }

    public void getOrderHistory(TradePair tradePair) throws IOException, InterruptedException {
        String symbol = tradePair.toString('-');

        String uriStr = "https://api.bitstamp.com/api/v3/brokerage/orders";

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        requestBuilder.uri(URI.create(uriStr));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());

        if (response.statusCode() == 200) {
            JSONObject jsonObject = new JSONObject(response.body());
            System.out.println(jsonObject.toString(4));
        } else {
            System.out.println(response.statusCode());
            System.out.println(response.body());
        }
    }

    @Override
    public List<Order> getPendingOrders() {
        return null;
    }

    @Override
    public @NotNull List<Account> getAccount() throws IOException, InterruptedException {
        return null;
    }


    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size,
                            @NotNull Instant timestamp, double stopLoss, double takeProfit, double takeProfitPrice) throws IOException, InterruptedException {
        // JSONObject jsonObject = getJSON();
        //   System.out.println(jsonObject.toString(4));

        String symbol = tradePair.toString('-');

        String uriStr = "https://api.coinbase.com/api/v3/brokerage/orders";

        String data=
                String.format(
                        "{\"product_id\": \"%s\", \"side\": \"%s\", \"type\": \"%s\", \"quantity\": %f, \"price\": %f, \"stop-loss\": %f, \"take-profit\": %f, \"take-profit-price\": %f, \"timestamp\": \"%s\"}",
                        symbol, side, orderType, size, price, stopLoss, takeProfit, takeProfitPrice,
                        timestamp.toEpochMilli() / 1000L);

        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
        data = String.format(data, orderType, side, price);

        System.out.println(uriStr);
        requestBuilder.POST(HttpRequest.BodyPublishers.ofString(
                data
        ));
        requestBuilder.uri(URI.create(uriStr));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());

        if (response.statusCode() != 200) {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(response.body());
            alert.showAndWait();

        }else {
            JSONObject jsonObject = new JSONObject(response.body());


            System.out.println(jsonObject.toString(4));
        }





    }
    public void CloseAllOrders() throws IOException, InterruptedException {
        String uriStr ="https://api.coinbase.com/api/v3/brokerage/orders/batch_cancel"
                ;

        System.out.println(uriStr);
        HttpRequest.Builder request = HttpRequest.newBuilder();
        requestBuilder.uri(URI.create(uriStr));
        requestBuilder.DELETE();
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());
        if (response.statusCode()!= 200) {
            System.out.println(response.statusCode());
            System.out.println(response.body());
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText(null);
            alert.setContentText(response.body());
            alert.showAndWait();

        }
        else {
            JSONObject jsonObject = new JSONObject(response.body());


            System.out.println(jsonObject.toString(4));
        }

    }

    public void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE trailingStopSell, double quantity, int i, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) throws IOException, InterruptedException {
        JSONObject jsonObject = getJSON();
        System.out.println(jsonObject.toString(4));

        String uriStr = "https://api.pro.coinbase.com/" +
                "products/" + tradePair.toString('_') + "/orders" +
                "?side=" + buy +
                "&type=" + trailingStopSell +
                "&quantity=" + quantity +
                "&price=" + i +
                "&stop-loss=" + stopPrice +
                "&take-profit=" + takeProfitPrice
                ;
        System.out.println(uriStr);
        HttpRequest.Builder request = HttpRequest.newBuilder();
        requestBuilder.uri(URI.create(uriStr));
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        System.out.println(response.statusCode());
        System.out.println(response.body());
    }

    public static abstract class BitstampCandleDataSupplier extends CandleDataSupplier {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first trade

        BitstampCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
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

            String endDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    .format(LocalDateTime.ofEpochSecond(endTime.get(), 0, ZoneOffset.UTC));

            int startTime = Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA);
            String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                    .format(LocalDateTime.ofEpochSecond(startTime, 0, ZoneOffset.UTC));

            String uriStr = "https://api.pro.coinbase.com/" +
                    "products/" + tradePair.toString('-') + "/candles" +
                    "?granularity=" + secondsPerCandle +
                    "&start=" + startDateString +
                    "&end=" + endDateString;

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
                        Log.info("coinbase response: ", response);
                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response);




                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (!res.isEmpty()) {



                            if (res.has("message")) {
                                Alert alert = new Alert(Alert.AlertType.WARNING);
                                alert.setTitle("Coinbase Error");
                                alert.setHeaderText("Coinbase Error");
                                alert.setContentText(res.get("message").asText());
                                alert.showAndWait();


                                return Collections.emptyList();
                            }
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

        public abstract CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt, boolean isAutoTrade);
    }
    public void createOrder(TradePair tradePair, Side buy, ENUM_ORDER_TYPE trailingStopBuy, Double quantity, double price, Instant timestamp, long orderID, double stopPrice, double takeProfitPrice) {
    }
}
