package org.investpro.exchanges;

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
import lombok.Getter;
import lombok.Setter;
import org.investpro.Currency;
import org.investpro.*;
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
import java.util.function.Consumer;

import static org.investpro.BinanceUtils.HmacSHA256;
import static org.investpro.CoinbaseCandleDataSupplier.OBJECT_MAPPER;
import static org.investpro.exchanges.Oanda.numCandles;

@Getter
@Setter
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
    public CompletableFuture<List<OrderBook>> fetchOrderBook(TradePair tradePair) {
        Objects.requireNonNull(tradePair, "TradePair cannot be null");

        CompletableFuture<List<OrderBook>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            try {
                String uriStr = "https://api.binance.us/api/v3/depth?symbol=" + tradePair.toString().replace("/", "") + "&limit=100";
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
    public String getExchangeMessage() {
        return message;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new BinanceUsCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt, int secondsPerCandle,
                                                                 Consumer<List<Trade>> trades) {
        Objects.requireNonNull(tradePair);
        Objects.requireNonNull(stopAt);

        CompletableFuture<List<Trade>> futureResult = new CompletableFuture<>();

        CompletableFuture.runAsync(() -> {
            String uriStr = API_URL + "/api/v3/trades?symbol=" + tradePair.toString('-');

            try {
                HttpResponse<String> response = HttpClient.newHttpClient().send(
                        HttpRequest.newBuilder()
                                .uri(URI.create(uriStr))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString());

                JsonNode tradesResponse = new ObjectMapper().readTree(response.body());
                if (!tradesResponse.isArray() || tradesResponse.isEmpty()) {
                    futureResult.completeExceptionally(new RuntimeException("Binance trades response was empty or not an array"));
                } else {

                    for (JsonNode trade : tradesResponse) {
                        Instant time = Instant.ofEpochMilli(trade.get("time").asLong());
                        if (time.compareTo(stopAt) <= 0) {
                            futureResult.complete(Collections.emptyList());

                            break;
                        } else {
                            List<Trade> tr = new ArrayList<>();

                            Side side = Side.getSide(trade.get("isBuyerMaker").asBoolean() ? "SELL" : "BUY");
                            OrderBook prices = new OrderBook();
                            prices.getAskEntries().stream().toList().getLast().setPrice(trade.get("ask").get(0).asDouble());
                            prices.getBidEntries().stream().toList().getLast().setSize(trade.get("qty").asLong());
                            prices.getBidEntries().stream().toList().getLast().setPrice(trade.get("bid").get(0).asDouble());
                            prices.getAskEntries().stream().toList().getLast().setSize(trade.get("qty").asLong());

                            Trade tradex = new Trade(
                                    tradePair,

                                    prices.getAskEntries().stream().findFirst().get().getPrice(), trade.get("qty").asLong(),
                                    side,
                                    trade.get("id").asLong(),
                                    time
                            );
                            tr.add(tradex);
                            trades.accept(tr);
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
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
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
                            openTime.getEpochSecond(),
                            currCandle.get(1).asDouble(),
                            currCandle.get(2).asDouble(),
                            currCandle.get(3).asDouble(),
                            Instant.now().toEpochMilli(),
                            currCandle.get(4).asDouble(),

                            currCandle.get(5).asLong()
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
                account.setBrokered(String.valueOf(res.get("brokered").asBoolean()));
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
                throw new RuntimeException(
                        "Error fetching trade pairs: HTTP status code %s,/%s".formatted(response.statusCode(), response.body())
                );
            }



            JsonNode res = OBJECT_MAPPER.readTree(response.body());
            logger.info("Binance US response: %s".formatted(res));
        if (
                res.has("symbols") && !res.get("symbols").isNull() && res.get("symbols").isArray()

        ) {


            JsonNode symbols = res.get("symbols");
            for (JsonNode symbol : symbols) {
                String baseAsset = symbol.get("baseAsset").asText();
                String quoteAsset = symbol.get("quoteAsset").asText();
                TradePair tp = new TradePair(baseAsset, quoteAsset);
                tradePairs.add(tp);
                logger.info("Binance US trade pair: %s".formatted(tp));
                Currency.save(tp.getBaseCurrency());
                Currency.save(tp.getCounterCurrency());

            }

        } else {
            logger.error("Binance US response does not contain trade pairs");
        }

        return tradePairs;
    }

    @Override
    public void streamLiveTrades(@NotNull TradePair tradePair, LiveTradesConsumer liveTradesConsumer) {

        // WebSocket connection and handling for live trade streams
        String url = "wss://stream.binance.us:9443/ws/%s@trade".formatted(tradePair.toString('/'));
        String message = "{\"method\": \"SUBSCRIBE\",\"params\": [\"%s@trade\"],\"id\": 1}".formatted(tradePair.toString('-'));
        CompletableFuture<String> res = null;
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
        // No WebSocket connection to stop live trade streams
        // Implement WebSocket connection and handling for live trade streams
        String url = "wss://stream.binance.us:9443/ws/%s@trade".formatted(tradePair.toString('/'));
        String message = "{\"method\": \"UNSUBSCRIBE\",\"params\": [\"%s@trade\"],\"id\": 1}".formatted(tradePair.toString('-'));
        // Implement WebSocket connection and handling for live trade streams


    }

    @Override
    public List<PriceData> streamLivePrices(@NotNull TradePair symbol) {

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


    private static class BinanceUsCandleDataSupplier extends CandleDataSupplier {

        protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);


        BinanceUsCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
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
            try {
                // Set end time to current timestamp if not already set
                if (endTime.get() == -1) {
                    endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
                }

                long endTimeMillis = endTime.get() * 1000L;
                long startTimeMillis = Math.max(endTimeMillis - (numCandles * secondsPerCandle * 1000L), 1422144000000L); // Earliest timestamp

                // Determine Binance interval based on seconds per candle
                String interval = getBinanceInterval(secondsPerCandle);

                // Construct URL for the API request

                String url = "https://api.binance.us/api/v3/klines?symbol=" + tradePair.toString('/') + "&interval=" + getBinanceInterval(secondsPerCandle);

                logger.info("Fetching candle data for trade pair:{} {} from {} to {}",
                        tradePair.toString('/'), interval, startTimeMillis, endTimeMillis);


                // Generate signature for the query string
                String signature = HmacSHA256(apiSecret, url);

                // Append signature to the query string

                // Prepare HTTP request
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("X-MBX-APIKEY", apiKey)
                        .GET()
                        .build();

                // Send asynchronous request and process the response
                return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenApply(response -> {
                            // Handle non-200 HTTP responses
                            if (response.statusCode() != 200) {
                                String errorMessage = String.format("Failed to fetch candle data. Status code: %d, Response: %s",
                                        response.statusCode(), response.body());
                                logger.error(errorMessage);
                                throw new RuntimeException(errorMessage);
                            }

                            // Parse response body into JSON
                            JsonNode responseBody;
                            try {
                                responseBody = OBJECT_MAPPER.readTree(response.body());
                            } catch (JsonProcessingException e) {
                                throw new RuntimeException("Failed to parse candle data response: " + e.getMessage(), e);
                            }

                            // Process JSON response and map to CandleData
                            if (responseBody.isArray() && !responseBody.isEmpty()) {
                                List<CandleData> candleDataList = new ArrayList<>();
                                for (JsonNode candle : responseBody) {
                                    candleDataList.add(new CandleData(
                                            candle.get(1).asDouble(),  // Open price
                                            candle.get(4).asDouble(),  // Close price
                                            candle.get(2).asDouble(),  // High price
                                            candle.get(3).asDouble(),  // Low price
                                            (int) candle.get(0).asLong(),  // Open time (convert ms to seconds)
                                            0,
                                            candle.get(5).asLong()   // Volume
                                    ));
                                }

                                // Sort by open time
                                candleDataList.sort(Comparator.comparingLong(CandleData::getOpenTime));

                                // Update endTime for pagination
                                endTime.set((int) (startTimeMillis / 1000L));

                                return candleDataList;
                            } else {
                                logger.info("No candle data found for trade pair: {}", tradePair);
                                return Collections.emptyList();
                            }
                        });
            } catch (Exception e) {
                logger.error("An error occurred while fetching candle data: ", e);
                throw new RuntimeException("Failed to fetch candle data.", e);
            }
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
