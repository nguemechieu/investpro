package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.HttpResponseException;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.Alert;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import static org.investpro.BinanceUtils.HmacSHA256;
import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;
import static org.investpro.Currency.db1;

public class BinanceUS extends Exchange {

    static HttpClient client = HttpClient.newHttpClient();
    static HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private static final Logger logger = LoggerFactory.getLogger(BinanceUS.class);
    public static final String API_URL = "https://api.binance.us/api/v3";

    static long timestamp;

    static {
        try {
            timestamp = fetchServerTime();
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private static String apiKey;


    private TradePair tradePair;

    public BinanceUS(String apikey, String apiSecret) {
        super(apikey, apiSecret);


        BinanceUS.apiKey = apikey;
        BinanceUS.apiSecret = apiSecret;


        requestBuilder.setHeader(
                "X-MBX-APIKEY", apiKey
        );

        // Adjust timestamp with server time
        requestBuilder.setHeader("Content-Type", "application/json");




    }



    //GET /sapi/v1/asset/query/trading-fee (HMAC SHA256)

    // Method to fetch the current server time
    private static long fetchServerTime() throws IOException, InterruptedException {
        HttpRequest timeRequest = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + "/time"))
                .GET()
                .build();

        HttpResponse<String> timeResponse = client.send(timeRequest, HttpResponse.BodyHandlers.ofString());

        if (timeResponse.statusCode() != 200) {
            throw new RuntimeException("Error fetching server time: " + timeResponse.statusCode());
        }

        // Parse the server time from the response JSON
        JSONObject json = new JSONObject(timeResponse.body());
        return json.getLong("serverTime");
    }

    @Override
    public List<Fee> getTradingFee() throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "%s/sapi/v1/asset/query/trading-fee".formatted(API_URL)
        ));

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Error fetching trading fee: %d".formatted(response.statusCode()));
        }

        return OBJECT_MAPPER.readValue(response.body(), OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, Fee[].class));
    }

    @Override
    public List<Account> getAccounts() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        // Step 1: Fetch the current server time to ensure synchronized timestamp
        long serverTime = System.currentTimeMillis();  // Implement this method to get the current server time from the API

        // Step 2: Create query string with server timestamp and recvWindow
        String queryString = String.format("timestamp=%d&recvWindow=10000", serverTime);

        // Step 3: Generate HMAC SHA256 signature using the API secret and query string
        String signature = HmacSHA256(apiSecret, queryString);

        // Step 4: Append the signature to the query string
        queryString += String.format("&signature=%s", signature);

        // Step 5: Build the URI for the request, including the query string with the signature
        requestBuilder.uri(URI.create(String.format("%s/account?%s", API_URL, queryString)));

        // Step 6: Set the headers with the API key
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);

        // Step 7: Send the GET request and capture the response
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new HttpResponseException(response.statusCode(), ": " + response.body());
        }
        logger.info("Got response: {}", response.body());
        // Step 8: Parse the response into a list of Account objects
        return parseAccountResponse(response);
    }



    @Override
    public void createOrder(@NotNull TradePair tradePair, @NotNull Side side, @NotNull ENUM_ORDER_TYPE orderType, double price, double size, Date timestamp, double stopLoss, double takeProfit) throws IOException, InterruptedException {
        requestBuilder.uri(URI.create(
                "%s/api/v3/order".formatted(API_URL)
        ));
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        requestBuilder.setHeader("Content-Type", "application/json");

        CreateOrderRequest orderRequest = new CreateOrderRequest(
                tradePair.toString('/'),
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

            new Messages(Alert.AlertType.ERROR, "%d\n%s".formatted(response.statusCode(), response.body()));
        }
        logger.info("Order created: {}", response.body());
    }

    @Override
    public void cancelOrder(String orderId) throws IOException, InterruptedException {

        requestBuilder.uri(URI.create(
                API_URL + "/api/v3/order?orderId=" + orderId
        ));
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        requestBuilder.method("DELETE", HttpRequest.BodyPublishers.noBody());

        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {

            new Messages(Alert.AlertType.ERROR, "%d\n%s".formatted(response.statusCode(), response.body()));
        }
        logger.info("Order cancelled: {}", orderId);
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
            String uriStr = API_URL + "/api/v3/trades?symbol=" + tradePair.toString('/');

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
                                tradePair.toString('/'),
                                        getBinanceGranularity(secondsPerCandle),
                                        startDateString
                        ))).build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {

                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                    } catch (JsonProcessingException ex) {
                        logger.error("Error parsing JSON: %s".formatted(ex.getMessage()));

                        new Messages(Alert.AlertType.ERROR, ex.getMessage());
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
    public List<Order> getPendingOrders() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

        // Step 2: Create query string with timestamp and recvWindow
        String queryString = "timestamp=%d&recvWindow=10000".formatted(timestamp);  // Adjust timestamp and recvWindow as required

        // Step 3: Generate HMAC SHA256 signature using the API secret and query string
        String signature = HmacSHA256(apiSecret, queryString);

        // Step 4: Append the signature to the query string
        queryString += "&signature=%s".formatted(signature);


        // Step 6: Set the headers with the API key
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);

        String url0 = "%s/api/v3/order?symbol=%s".formatted(API_URL, tradePair.toString('/'));
        requestBuilder.uri(URI.create(
                "%s?%s".formatted(url0, queryString)
        ));
        HttpResponse<String> response = client.send(
                requestBuilder.build(),
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            new Messages(Alert.AlertType.ERROR, "%d\n%s".formatted(response.statusCode(), response.body()));
            throw new RuntimeException("Error fetching open orders: %d".formatted(response.statusCode()));
        }

        return List.of(OBJECT_MAPPER.readValue(response.body(),
                Order[].class));




    }

    @Override
    public List<OrderBook> getOrderBook(@NotNull TradePair tradePair) throws IOException, InterruptedException, ExecutionException, NoSuchAlgorithmException, InvalidKeyException {

        // Step 2: Create query string with timestamp and recvWindow
        String queryString = "timestamp=%d&recvWindow=10000".formatted(timestamp);  // Adjust timestamp and recvWindow as required

        // Step 3: Generate HMAC SHA256 signature using the API secret and query string
        String signature = HmacSHA256(apiSecret, queryString);

        // Step 4: Append the signature to the query string
        queryString += "&signature=%s".formatted(signature);


        // Step 6: Set the headers with the API key
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);

        String url0 = "%s/api/v3/depth?symbol=%s".formatted(API_URL, tradePair.toString('/'));
        requestBuilder.uri(URI.create(
                "%s?%s".formatted(url0, queryString)
        ));
        HttpResponse<String> response = client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString()).get();


        return Arrays.asList(OBJECT_MAPPER.readValue(response.body(), OrderBook[].class));

    }

    @Override
    public List<Position> getPositions() {
        return new ArrayList<>();
    }



    @Override
    public List<Order> getOpenOrder(@NotNull TradePair tradePair) throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

        // Step 2: Create query string with timestamp and recvWindow
        String queryString = "timestamp=%d&recvWindow=10000".formatted(timestamp);  // Adjust timestamp and recvWindow as required

        // Step 3: Generate HMAC SHA256 signature using the API secret and query string
        String signature = HmacSHA256(apiSecret, queryString);

        // Step 4: Append the signature to the query string
        queryString += "&signature=%s".formatted(signature);


        // Step 6: Set the headers with the API key
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);

        String url0 = "%s/api/v3/openOrders?symbol=%s".formatted(API_URL, tradePair.toString('/'));
        requestBuilder.uri(URI.create(
                "%s?%s".formatted(url0, queryString)
        ));
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {

            logger.error("Error fetching open orders:  %s".formatted(response.body()));
            new Messages(Alert.AlertType.ERROR, "Error fetching open orders: HTTP status code %d, %s".formatted(response.statusCode(), response.body()));

        }


        return Arrays.asList(OBJECT_MAPPER.readValue(response.body(), Order[].class));
    }

    @Override
    public List<Order> getOrders() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {

        // Fetch server timestamp if necessary or use system time
        long timestamp = System.currentTimeMillis();

        // Create the message for HMAC SHA256 signature
        String message = "timestamp=%d&recvWindow=5000".formatted(timestamp);

        // Generate the signature using the secret key and the message
        String signature = HmacSHA256(apiSecret, message);

        // Create the full URI with the signature
        String fullUri = "%s/openOrders?%s&signature=%s".formatted(API_URL, message, signature);

        // Set the request URI and method
        requestBuilder.uri(URI.create(fullUri));
        requestBuilder.header("X-MBX-APIKEY", apiKey);  // Include the API key in the header
        requestBuilder.GET();  // Use GET method for openOrders request

        // Send the request and capture the response
        HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        // Handle non-200 status codes by logging and throwing an error
        if (response.statusCode() != 200) {
            logger.error("Error fetching orders : %s".formatted(response.body()));
            throw new IllegalStateException("Error fetching orders: " + response.body());
        }

        // Parse the response body into JSON
        JsonNode rootNode = OBJECT_MAPPER.readTree(response.body());

        // Extract the 'orders' node and populate the order list
        List<Order> orders = FXCollections.observableArrayList();
        for (JsonNode orderNode : rootNode) {
            orders.add(new Order(
                    orderNode.get("symbol").asText(),  // Symbol of the trading pair
                    orderNode.get("orderId").asLong(),  // Order ID
                    orderNode.get("side").asText(),  // Side (BUY/SELL)
                    orderNode.get("type").asText(),  // Order type
                    orderNode.get("price").asDouble(),  // Price
                    orderNode.get("origQty").asDouble(),  // Quantity
                    orderNode.get("time").asLong(),  // Timestamp of the order
                    orderNode.get("status").asText(),  // Status of the order
                    orderNode.get("isWorking").asBoolean()  // Working status (true if the order is active)
            ));
        }

        // Log the fetched orders
        logger.info("Fetched %d orders\nOrders: %s".formatted(orders.size(), orders));

        return orders;
    }

    public List<Account> parseAccountResponse(HttpResponse<String> jsonResponse) throws IOException {

        List<Account> accounts = new ArrayList<>();
        try {
            // Parse the response body as a JsonNode
            JsonNode res = OBJECT_MAPPER.readTree(jsonResponse.body());

            // Loop through each account node
            for (JsonNode JsonNode : res) {

                // Get the index of the account in the response
                int index = 0;
                index++;
                JsonNode.get(index);

                // Create a new Account object for each account


                Account account = new Account();
                // Parse commission rates if available
                if (res.has("commissionRates") && !res.get("commissionRates").isNull()) {
                    JsonNode commissionRatesNode = res.get("commissionRates");
                    account.setMakerCommission(commissionRatesNode.has("maker") ? commissionRatesNode.get("maker").asDouble() : 0.0);
                    account.setTakerCommission(commissionRatesNode.has("taker") ? commissionRatesNode.get("taker").asDouble() : 0.0);
                    account.setBuyerCommission(commissionRatesNode.has("buyer") ? commissionRatesNode.get("buyer").asDouble() : 0.0);
                    account.setSellerCommission(commissionRatesNode.has("seller") ? commissionRatesNode.get("seller").asDouble() : 0.0);
                }

                // Set other account properties, checking for nulls
                account.setRequireSelfTradePrevention(res.get("requireSelfTradePrevention").asBoolean());
                account.setBrokered(res.get("brokered").asBoolean());
                account.setPermissions(res.has("permissions") && !res.get("permissions").isNull() ? res.get("permissions").asText() : "");
                account.setAccountType(res.has("accountType") && !res.get("accountType").isNull() ? res.get("accountType").asText() : "");
                account.setCanTrade(res.has("canTrade") && !res.get("canTrade").isNull() && res.get("canTrade").asBoolean());
                account.setCanWithdraw(res.has("canWithdraw") && !res.get("canWithdraw").isNull() && res.get("canWithdraw").asBoolean());
                account.setCanDeposit(res.has("canDeposit") && !res.get("canDeposit").isNull() && res.get("canDeposit").asBoolean());
                account.setUpdateTime(res.get("updateTime").asLong());//isNull() ? jsonNode.get("updateTime").asLong() : 0L);

                account.setMakerCommission(res.has("makerCommission") && !res.get("makerCommission").isNull() ? res.get("makerCommission").asDouble() : 0.0);
                account.setTakerCommission(res.has("takerCommission") && !res.get("takerCommission").isNull() ? res.get("takerCommission").asDouble() : 0.0);
                account.setBuyerCommission(res.has("buyerCommission") && !res.get("buyerCommission").isNull() ? res.get("buyerCommission").asDouble() : 0.0);
                account.setSellerCommission(res.has("sellerCommission") && !res.get("sellerCommission").isNull() ? res.get("sellerCommission").asDouble() : 0.0);

                // Parse balances if available
                if (res.has("balances") && !res.get("balances").isNull()) {
                    for (JsonNode balanceNode : res.get("balances")) {
                        String asset = balanceNode.has("asset") ? balanceNode.get("asset").asText() : "N/A";
                        double free = balanceNode.has("free") ? balanceNode.get("free").asDouble() : 0.0;
                        double locked = balanceNode.has("locked") ? balanceNode.get("locked").asDouble() : 0.0;

                        // Add balance information to the account
                        account.addBalance(asset, free, locked);  // Assume there's a method in Account to add balances
                    }
                }

                // Add the account to the list
                accounts.add(account);

                logger.info("Parsed {} account(s) successfully: {}", accounts.size(), accounts);


            }

        } catch (Exception e) {
            logger.error("Exception occurred while parsing account response: {}", e.getMessage(), e);
            throw e;
        }

        return accounts;
    }

    @Override
    public List<TradePair> getTradePairs() throws Exception {
        requestBuilder.uri(URI.create("https://api.binance.us/api/v3/exchangeInfo"));

        ArrayList<TradePair> tradePairs = new ArrayList<>();

        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.error("Error fetching trade pairs: HTTP status code %s{}", response.statusCode());
                new Messages(Alert.AlertType.ERROR, "Error fetching trade pairs: HTTP status code %d, %s".formatted(response.statusCode(), response.body()));
                throw new IllegalStateException(
                        "Error fetching trade pairs: HTTP status code %s,/%s".formatted(response.statusCode(), response.body())
                );
            }



            JsonNode res = OBJECT_MAPPER.readTree(response.body());
            logger.info("Binance US response: %s".formatted(res));

            JsonNode symbols = res.get("symbols");
            for (JsonNode symbol : symbols) {
                String baseAsset = symbol.get("baseAsset").asText();
                String quoteAsset = symbol.get("quoteAsset").asText();
                TradePair tp = new TradePair(baseAsset, quoteAsset);
                tradePairs.add(tp);
                logger.info("Binance US trade pair: %s".formatted(tp));


            }
        db1.save((ArrayList<Currency>) tradePairs.stream().map(
                TradePair::getCounterCurrency
        ).collect(Collectors.toList()));

        db1.save((ArrayList<Currency>) tradePairs.stream().map(
                TradePair::getBaseCurrency
        ).collect(Collectors.toList()));


        return tradePairs;
    }
CustomWebSocketClient customWebSocketClient = new CustomWebSocketClient();
    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

        // WebSocket connection and handling for live trade streams
        String url = "wss://stream.binance.us:9443/ws/%s@trade".formatted(tradePair.toString('/'));
        String message = "{\"method\": \"SUBSCRIBE\",\"params\": [\"%s@trade\"],\"id\": 1}".formatted(tradePair.toString('-'));
        CompletableFuture<String> res = customWebSocketClient.sendWebSocketRequest(url, message);
        res.thenAccept(msg -> {
            try {

                LiveTrade liveTrade = OBJECT_MAPPER.readValue(msg, LiveTrade.class);
                liveTradesConsumer.accept(liveTrade.getTrade());
            } catch (JsonProcessingException e) {
                logger.error("Error parsing JSON: %s".formatted(e.getMessage()));
                new Messages(Alert.AlertType.ERROR, e.getMessage());
                throw new IllegalStateException(
                        "Error parsing JSON: %s".formatted(e.getMessage())
                );
            }
        });
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
        customWebSocketClient.sendWebSocketRequest(urls, message);


        return new ArrayList<>();

    }


    @Override
    public List<CandleData> streamLiveCandlestick(@NotNull TradePair symbol, int intervalSeconds) {

        return new ArrayList<>();
    }

    @Override
    public void cancelAllOrders() {

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
  public List<Deposit> Deposit() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
      // Step 2: Create query string with timestamp and recvWindow
      String queryString = "timestamp=%d&recvWindow=10000".formatted(timestamp);  // Adjust timestamp and recvWindow as required

      // Step 3: Generate HMAC SHA256 signature using the API secret and query string
      String signature = HmacSHA256(apiSecret, queryString);

      // Step 4: Append the signature to the query string
      queryString += "&signature=%s".formatted(signature);


      // Step 6: Set the headers with the API key
      requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
      String url0 = API_URL + "/sapi/v1/capital/deposit/hisrec";
      requestBuilder.uri(URI.create(url0 + "?" + queryString));


      HttpResponse<String> response = client.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if ( response.statusCode()!=200 ){
            new Messages(Alert.AlertType.ERROR, response.body());
            throw new RuntimeException("HTTP error response: " + response.body());
        }


      ArrayList<Deposit> deposits = new ArrayList<>();
      Deposit deposit = OBJECT_MAPPER.readValue(response.body(), Deposit.class);
        deposits.add(deposit);

        return deposits;

  }

    //  Get Crypto Withdraw History  GET /sapi/v1/capital/withdraw/history (HMAC SHA256)
    @Override
    public List<Withdrawal> Withdraw() throws IOException, InterruptedException, NoSuchAlgorithmException, InvalidKeyException {
        // Step 2: Create query string with timestamp and recvWindow
        String queryString = "timestamp=%d&recvWindow=10000".formatted(timestamp);  // Adjust timestamp and recvWindow as required

        // Step 3: Generate HMAC SHA256 signature using the API secret and query string
        String signature = HmacSHA256(apiSecret, queryString);

        // Step 4: Append the signature to the query string
        queryString += "&signature=%s".formatted(signature);


        // Step 6: Set the headers with the API key
        requestBuilder.setHeader("X-MBX-APIKEY", apiKey);
        String url0 = "%s/sapi/v1/capital/withdraw/history".formatted(API_URL);
        requestBuilder.uri(URI.create(url0 + "?" + queryString));


        HttpResponse<String> response = client.send(requestBuilder.GET().build(), HttpResponse.BodyHandlers.ofString());

        if ( response.statusCode()!=200 ){
            new Messages(Alert.AlertType.ERROR, response.body());
            throw new RuntimeException("HTTP error response: " + response.body());
        }
        logger.info("Binance response: " + response.body());

        ArrayList<Withdrawal> withdraws = new ArrayList<>();
        Withdrawal withdraw = OBJECT_MAPPER.readValue(response.body(), Withdrawal.class);
        withdraws.add(withdraw);

        return withdraws;
    }

    @Override
    public List<Trade> getLiveTrades(List<TradePair> tradePairs) {
        return List.of();
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }


    private static class BinanceUsCandleDataSupplier extends CandleDataSupplier {

        protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


        BinanceUsCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        }

        @Contract(" -> new")
        @Override
        public @NotNull Set<Integer> getSupportedGranularity() {
            // Binance uses fixed time intervals (1m, 3m, 5m, etc.)
            // Here we map them to seconds
            return new TreeSet<>(Set.of(60, 180, 300, 900, 1800, 3600, 14400, 86400));
        }

        @Contract("_, _ -> new")
        @Override
        public @NotNull CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair);
        }

        @Override
        public CompletableFuture<List<CandleData>> get() {
            if (endTime.get() == -1) {
                endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
            }

            long endTimeMillis = (long) endTime.get() * 1000;
            long startTimeMillis = Math.max(endTimeMillis - (numCandles * secondsPerCandle * 1000L), 1422144000000L); // earliest timestamp

            // Binance uses string intervals for granularity like "1m", "5m", etc.
            String interval = getBinanceInterval(secondsPerCandle);

            // Construct the URL
            String url = String.format("%s/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                    API_URL, tradePair.toString('/'), interval, startTimeMillis, endTimeMillis, numCandles);

            logger.info("Fetching candle data for trade pair: {} from {} to {}", tradePair.toString('/'), startTimeMillis, endTimeMillis);

            // Generate the correct signature for the request

            String queryString = String.format("symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d&timestamp=%d",
                    tradePair.toString('/'), interval, startTimeMillis, endTimeMillis, numCandles, timestamp);

            @NotNull String signature;
            try {
                signature = HmacSHA256(apiSecret, queryString); // Generate the signature using HmacSHA256
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                throw new RuntimeException(e);
            }

            // Append signature to the query string
            String signedUrl = "%s&signature=%s".formatted(url, signature);

            // Prepare the request with headers and API key
            requestBuilder.uri(URI.create(signedUrl));
            requestBuilder.setHeader("X-MBX-APIKEY", apiKey); // Set API key in the header
            requestBuilder.GET(); // Use GET method as required for klines

            return client.sendAsync(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() != 200) {
                            try {
                                throw new HttpResponseException(response.statusCode(), "Failed to fetch candle data:" + response.body());
                            } catch (HttpResponseException e) {
                                throw new RuntimeException(e);
                            }
                        }

                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response.body());
                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (!res.isEmpty()) {
                            List<CandleData> candleData = new ArrayList<>();
                            for (JsonNode candle : res) {
                                candleData.add(new CandleData(
                                        candle.get(1).asDouble(),  // open price
                                        candle.get(4).asDouble(),  // close price
                                        candle.get(2).asDouble(),  // high price
                                        candle.get(3).asDouble(),  // low price
                                        (int) (candle.get(0).asLong() / 1000L),  // open time (convert ms to seconds)
                                        candle.get(5).asDouble()   // volume
                                ));
                            }
                            candleData.sort(Comparator.comparingLong(CandleData::getOpenTime));
                            endTime.set((int) (startTimeMillis / 1000));  // Update endTime for pagination
                            return candleData;
                        } else {
                            logger.info("No candle data found for trade pair: %s".formatted(tradePair));
                            return Collections.emptyList();
                        }
                    });
        }


        // Helper method to convert secondsPerCandle to Binance interval strings
        private @NotNull String getBinanceInterval(int secondsPerCandle) {
            return switch (secondsPerCandle) {
                case 60 -> "1m";
                case 180 -> "3m";
                case 300 -> "5m";
                case 900 -> "15m";
                case 1800 -> "30m";
                case 3600 -> "1h";
                case 14400 -> "4h";
                case 86400 -> "1d";
                default -> throw new IllegalArgumentException("Unsupported granularity: " + secondsPerCandle);
            };
        }
    }
}
