package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;

public class BinanceUS extends Exchange {

    HttpClient client = HttpClient.newHttpClient();
    HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static final Logger logger = LoggerFactory.getLogger(BinanceUS.class);
    public static final String API_URL = "https://api.binance.us/api/v3";
    String apiKey;

    public BinanceUS(String apikey, String apiSecret) {
        super(apikey, apiSecret);
        Exchange.apiSecret = apiSecret;
        this.apiKey = apikey;



        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);






    }



    private @NotNull String HmacSHA256(@NotNull String apiSecret, @NotNull String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(apiSecret.getBytes(), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(payload.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : rawHmac) {
                String hex = String.format("%02x", b);
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }
//    Get Trade Fee
//            Example
//
//    timestamp=`date +%s000`
//
//    api_key=<your_api_key>
//    secret_key=<your_secret_key>
//
//    api_url="https://api.binance.us"
//
//    signature=`echo -n "timestamp=$timestamp" | openssl dgst -sha256 -hmac $secret_key`
//
//    curl -X GET "$api_url/sapi/v1/asset/query/trading-fee?timestamp=$timestamp&signature=$signature" \
//            -H "X-MBX-APIKEY: $api_key"
//    Response
//
//[
//    {
//        "symbol": "1INCHUSD",
//            "makerCommission": "0.004",
//            "takerCommission": "0.006"
//    },
//    {
//        "symbol": "1INCHUSDT",
//            "makerCommission": "0.004",
//            "takerCommission": "0.006"
//    }
//]
    //GET /sapi/v1/asset/query/trading-fee (HMAC SHA256)

    @Override
    public CompletableFuture<List<Fee>> getTradingFee() throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "%s/sapi/v1/asset/query/trading-fee".formatted(API_URL)
        ));

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error fetching trading fee: %d".formatted(response.statusCode()));
        }
        ObjectMapper objectMapper = new ObjectMapper();
        List<Fee> fees = objectMapper.readValue(response.body(), objectMapper.getTypeFactory().constructCollectionType(List.class, Fee.class));
        return CompletableFuture.completedFuture(fees);
    }

    @Override
    public CompletableFuture<List<Account>> getAccounts() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        long timestamp = Instant.now().getNano();
        String queryString = "timestamp=" + timestamp/1000 + "&recvWindow=10000";  // Binance US requires a timestamp and recvWindow (optional)

        // Generate the signature
        String signature = BinanceUtils.generateSignature(queryString, apiSecret);

        // Append the signature to the query string
        queryString += "&signature=%s".formatted(signature);

        // Build the URI for the request, including the query string with the signature
        requestBuilder.uri(URI.create("%s/account?%s".formatted(API_URL, queryString)));

        // Set the headers with the API key
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);

        // Send the GET request and capture the response
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        // Handle non-200 status codes by showing an error message and throwing a RuntimeException
        if (response.statusCode() != 200) {
            new Messages("Error", "%d\n%s".formatted(response.statusCode(), response.body()));
            return CompletableFuture.completedFuture(Collections.singletonList(new Account()));  // Return empty list on error
        }

        // Parse the response into Account objects
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode rootNode = objectMapper.readTree(response.body());
        JsonNode balancesNode = rootNode.get("balances");

        List<Account> accounts = new ArrayList<>();

        if (balancesNode != null && balancesNode.isArray()) {
            for (JsonNode balanceNode : balancesNode) {
                Account account = new Account();
                account.setCurrency(balanceNode.get("asset").asText());
                account.setBalance(balanceNode.get("free").asDouble());
                account.setLockedBalance(balanceNode.get("locked").asDouble());
                accounts.add(account);
            }
        }

        // Return the list of accounts (balances)
        return CompletableFuture.completedFuture(accounts);
    }



    @Override
    public String getSymbol() {
        return tradePair.toString('/');
    }

    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException, ExecutionException {

        requestBuilder.uri(URI.create(
                "%s/api/v3/order".formatted(API_URL)
        ));
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        requestBuilder.setHeader("Content-Type", "application/json");

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                tradePair.toString('-'),
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
        if (response.statusCode() != 200) {

            new Messages("Error", "%d\n%s".formatted(response.statusCode(), response.body()));
            throw new RuntimeException("Error creating order: %d".formatted(response.statusCode()));
        }
        logger.info("Order created: {}", response.body());
    }

    @Override
    public CompletableFuture<String> cancelOrder(String orderId) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

        requestBuilder.uri(URI.create(
                API_URL + "/api/v3/order?orderId=" + orderId
        ));
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        requestBuilder.method("DELETE", HttpRequest.BodyPublishers.noBody());

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {

            new Messages("Error", "%d\n%s".formatted(response.statusCode(), response.body()));
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
        return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);

        CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            String uriStr = API_URL + "/api/v3/trades?symbol=" + tradePair.toString('-');

            try {
                HttpResponse<String> response = client.send(
                        requestBuilder
                                .uri(URI.create(uriStr))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());

                JsonNode tradesResponse = new ObjectMapper().readTree(response.body());
                if (!tradesResponse.isArray() || tradesResponse.isEmpty()) {
                    futureResult.completeExceptionally(new RuntimeException("Binance trades response was empty or not an array"));
                } else {
                    List<Trade> trades = new ArrayList<>();
                    for (JsonNode trade : tradesResponse) {
                        Instant time = Instant.ofEpochMilli(trade.get("time").asLong());
                        if (time.compareTo(stopAt) <= 0) {
                            futureResult.complete(trades);
                            break;
                        } else {
                            trades.add(new Trade(
                                    tradePair,
                                    DefaultMoney.ofFiat(trade.get("price").asText(), tradePair.getCounterCurrency()),
                                    DefaultMoney.ofCrypto(trade.get("qty").asText(), tradePair.getBaseCurrency()),
                                    Side.getSide(trade.get("isBuyerMaker").asBoolean() ? "SELL" : "BUY"),
                                    trade.get("id").asLong(),
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
                                        "%s/api/v3/klines?symbol=%s&interval=%s&startTime=%s", API_URL,
                                        tradePair.toString('-'),
                                        getBinanceGranularity(secondsPerCandle),
                                        startDateString
                                )))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    logger.info("Binance response: " + response);
                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                    } catch (JsonProcessingException ex) {
                        logger.error("Error parsing JSON: %s".formatted(ex.getMessage()));

                        new  Messages("Error parsing JSON: " , ex.getMessage());
                        return Optional.empty();

                    }


                    JsonNode currCandle = res.get(0);
                    Instant openTime = Instant.ofEpochMilli(currCandle.get(0).asLong());

                    return Optional.of(new InProgressCandleData(
                            (int) openTime.getEpochSecond(),
                            currCandle.get(1).asDouble(),
                            currCandle.get(2).asDouble(),
                            currCandle.get(3).asDouble(),
                            (int) currCandle.get(6).asLong(),
                            currCandle.get(4).asDouble(),
                            currCandle.get(5).asDouble()
                    ));
                });
    }

    @Override
    public List<Order> getPendingOrders() throws IOException, InterruptedException {
        return List.of(); // Binance US doesn't have a specific endpoint for pending orders
    }

    @Override
    public CompletableFuture<OrderBook> getOrderBook(TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        requestBuilder.uri(URI.create(API_URL + "/api/v3/depth?symbol=" + tradePair.toString('-')));
        HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("Binance response: " + response.body());

        OrderBook orderBook = OBJECT_MAPPER.readValue(response.body(), OrderBook.class);
        return CompletableFuture.completedFuture(orderBook);
    }

    @Override
    public Position getPositions() throws IOException, InterruptedException, ExecutionException {
        return null;
    }



    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException {
        requestBuilder.uri(URI.create(API_URL + "/api/v3/openOrders?symbol=" + tradePair.toString('-')));
        HttpResponse<String> response = client.sendAsync(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString()).get();
        logger.info("Binance response: " + response.body());

        return Arrays.asList(OBJECT_MAPPER.readValue(response.body(), Order[].class));
    }

    @Override
    public ObservableList<Order> getOrders() throws IOException, InterruptedException,  NoSuchAlgorithmException, InvalidKeyException {

        requestBuilder.uri(
                URI.create("%s/openOrders".formatted(API_URL)));
        message= "timestamp: " + System.currentTimeMillis() ;

        requestBuilder.method("GET",
        HttpRequest.BodyPublishers.ofString(
                "{\"signature\": \"%s\"}".formatted(BinanceUtils.generateSignature( message,apiSecret))));





        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if ( response.statusCode()!= 200 ) {
            logger.error("Error fetching orders: HTTP status code %s".formatted(response.body()));
            new Messages("Error", "Error fetching orders: HTTP status code %d, %s".formatted( response.statusCode(), response.body()));
            return FXCollections.observableArrayList();
        }

        JsonNode dat = OBJECT_MAPPER.readTree(
                response.body()
        );

        ObservableList<Order> orders = FXCollections.observableArrayList();

        for ( JsonNode orderNode : dat.get("orders") ) {
            Order order = new Order(
                    orderNode.get("symbol").asText(),
                    orderNode.get("orderId").asLong(),
                    orderNode.get("side").asText(),
                    orderNode.get("type").asText(),
                    orderNode.get("price").asDouble(),
                    orderNode.get("qty").asDouble(),
                    orderNode.get("time").asLong(),

                    orderNode.get("isWorking").asBoolean()
            );
            orders.add(order);
        }


        return orders;
    }

    @Override
    public CompletableFuture<ArrayList<TradePair>> getTradePairs() {
        requestBuilder.uri(URI.create("%s/exchangeInfo".formatted(API_URL)));

        ArrayList<TradePair> tradePairs = new ArrayList<>();
        try {
            HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();
            JsonNode res = new ObjectMapper().readTree(response.body());
            logger.info("Binance US response: %s".formatted(res));

            JsonNode symbols = res.get("symbols");
            for (JsonNode symbol : symbols) {
                String baseAsset = symbol.get("baseAsset").asText();
                String quoteAsset = symbol.get("quoteAsset").asText();
                TradePair tp = new TradePair(baseAsset, quoteAsset);
                tradePairs.add(tp);
                logger.info("Binance US trade pair: %s".formatted(tp));
            }

        } catch (Exception e) {
            logger.error("Error fetching trade pairs: %s".formatted(e.getMessage()));
            new Messages("Error fetching BINANCE US  trade pairs: ", e.getMessage());

        }
        return CompletableFuture.completedFuture(tradePairs);
    }
CustomWebSocketClient customWebSocketClient = new CustomWebSocketClient();
    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

        // WebSocket connection and handling for live trade streams
        String url = "wss://stream.binance.us:9443/ws/" + tradePair.toString('/') + "@trade";
        message =
                "{\"method\": \"SUBSCRIBE\",\"params\": [\"%s@trade\"],\"id\": 1}".formatted(tradePair.toString('-'));
        CompletableFuture<String> res = customWebSocketClient.sendWebSocketRequest(url, message);
        res.thenAccept(message -> {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                LiveTrade liveTrade = objectMapper.readValue(message, LiveTrade.class);
                liveTradesConsumer.accept(liveTrade.getTrade());
            } catch (JsonProcessingException e) {
                logger.error("Error parsing JSON: %s".formatted(e.getMessage()));
                new Messages("Error parsing JSON: ", e.getMessage());
            }
        });

        // To close the WebSocket connection after some time (optional)
        // Example: close after 10 seconds (can be adjusted as per requirement)
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            logger.error("Error sleeping: %s".formatted(e.getMessage()));

        }webSocketClient.close();
    }

    @Override
    public void stopStreamLiveTrades(TradePair tradePair) {
        // Implement WebSocket closing if needed for live trade streams
         customWebSocketClient.closeWebSocket();
    }

    @Override
    public List<PriceData> streamLivePrices(@NotNull TradePair symbol) {
      // WebSocket streaming for live prices not implemented here

        String urls=
                "wss://stream.binance.us:9443/ws/" + symbol.toString('/') + "@depth";
        message =
                "{\"method\": \"SUBSCRIBE\",\"params\": [\"%s@depth\"],\"id\": 1}".formatted(symbol.toString('-'));
        CompletableFuture<String> dat = customWebSocketClient.sendWebSocketRequest(urls, message);
        return (List<PriceData>) dat.thenApply(message -> {
            PriceData pricedata;
            try {
                pricedata = OBJECT_MAPPER.readValue(message, PriceData.class);


            } catch (JsonProcessingException e) {
                logger.error("Error parsing JSON: %s".formatted(e.getMessage()));
                new Messages("Error parsing JSON: ", e.getMessage());
                return List.of();
            }
            return  new ArrayList< >((Collection) pricedata);

        });

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
        return null; // Implement if Binance US supports cancelling all orders at once
    }

    @Override
    public boolean supportsStreamingTrades(TradePair tradePair) {
        return false; // WebSocket support for trades can be added if needed
    }



    /**
     * Returns the closest supported granularity (time interval) for Binance US.
     *
     * @param secondsPerCandle the candle duration in seconds
     * @return the granularity in Binance format (e.g., "1m", "5m")
     */
    public String getBinanceGranularity(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "1m";
            case 300 -> "5m";
            case 900 -> "15m";
            case 3600 -> "1h";
            case 21600 -> "6h";
            case 86400 -> "1d";
            default -> throw new IllegalArgumentException("Unsupported granularity: " + secondsPerCandle);
        };
    }

  //  Get Crypto Deposit History  GET /sapi/v1/capital/deposit/hisrec (HMAC SHA256)
  @Override
    ArrayList<CryptoDeposit> getCryptosDeposit() throws IOException, InterruptedException {

        requestBuilder.uri(
                URI.create(API_URL + "/sapi/v1/capital/deposit/hisrec")
        );

        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if ( response.statusCode()!=200 ){
            new  Messages("Error", response.body());
            throw new RuntimeException("HTTP error response: " + response.statusCode());
        }
        logger.info("Binance response: " + response.body());


        ArrayList<CryptoDeposit> deposits = new ArrayList<>();
        CryptoDeposit deposit = OBJECT_MAPPER.readValue(response.body(), CryptoDeposit.class);
        deposits.add(deposit);

        return deposits;

  }

    //  Get Crypto Withdraw History  GET /sapi/v1/capital/withdraw/history (HMAC SHA256)
    @Override
    ArrayList<CryptoWithdraw> getCryptosWithdraw() throws IOException, InterruptedException {

        requestBuilder.uri(
                URI.create("%s/sapi/v1/capital/withdraw/history".formatted(API_URL))
        );

        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if ( response.statusCode()!=200 ){
            new  Messages("Error", response.body());
            throw new RuntimeException("HTTP error response: " + response.statusCode());
        }
        logger.info("Binance response: " , response.body());

        ArrayList<CryptoWithdraw> withdraws = new ArrayList<>();
        CryptoWithdraw withdraw = OBJECT_MAPPER.readValue(response.body(), CryptoWithdraw.class);
        withdraws.add(withdraw);

        return withdraws;
    }

}
