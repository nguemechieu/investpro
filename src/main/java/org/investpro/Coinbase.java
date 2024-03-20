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
import java.util.*;
import java.util.Currency;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

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


    public Coinbase(String apiKey, String apiSecret, TradePair tradePair) {
        super(apiKey, apiSecret);
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.websocketClient = new CoinbaseWebSocketClient(URI.create("wss://ws-feed.pro.coinbase.com"), new Draft_6455());
        websocketClient.addHeader(
                "Accept",
                "application/vnd.pro.coinbase.com+json"
        );
        websocketClient.addHeader(
                "Content-Type",
                "application/vnd.pro.coinbase.com+json"
        );
        websocketClient.addHeader(
                "CB-ACCESS-KEY",
                apiKey
        );
        websocketClient.addHeader(
                "CB-ACCESS-SIGN",
                apiSecret
        );
        this.websocketClient.setTradePair(tradePair);
        this.tradePair = tradePair;
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



    /**
     * Fetches the recent trades for the given trade pair from  {@code stopAt} till now (the current time).
     * <p>
     * This method only needs to be implemented to support live syncing.
     */

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

    @Override
    public TradePair getSelecTradePair() throws SQLException, ClassNotFoundException {
        return null;
    }

    @Override
    public ExchangeWebSocketClient getWebsocketClient() {
        return (ExchangeWebSocketClient) websocketClient;
    }

    /**
     * This method only needs to be implemented to support live syncing.
     */

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
        return HttpClient.newHttpClient().sendAsync(
                        HttpRequest.newBuilder()
                                .uri(URI.create(String.format(
                                        "https://api.pro.coinbase.com/products/%s/candles?granularity=%s&start=%s",
                                        tradePair.toString('-'), actualGranularity, startDateString)))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {
                    logger.info(STR."coinbase response: \{response}");
                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);

                        if (res.has("message") && res.get("message").asText().equals("not_found")) {

                            new Message("ERROR", tradePair.toString('-'));
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
    public List<TradePair> getTradePairSymbol() {

        //COINBASE URL TO GET ALL TRADES PAIRS
        String url = "https://api.pro.coinbase.com/products";
        HttpClient.Builder builder = HttpClient.newBuilder();
        HttpClient client = builder.build();
        HttpRequest.Builder request = HttpRequest.newBuilder();
        request.uri(URI.create(url));
        HttpResponse<String> response;
        ArrayList<TradePair> tradePairs = new ArrayList<>();
        try {
            response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode res;
            try {
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
            } catch (JsonProcessingException | ClassNotFoundException | SQLException ex) {
                throw new RuntimeException(ex);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }

        return tradePairs;

    }

    public TradePair getTradePair() {
        return tradePair;
    }

    public void setTradePair(TradePair tradePair) {
        this.tradePair = tradePair;
    }

    public static class CoinbaseCandleDataSupplier extends CandleDataSupplier {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        private static final int EARLIEST_DATA = 1422144000; // roughly the first trade

        CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        }

        @Override
        public Set<Integer> getSupportedGranularities() {
            // https://docs.pro.coinbase.com/#get-historic-rates
            return new TreeSet<>(Set.of(60, 300, 900, 3600, 21600, 86400));
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

            String uriStr = STR."https://api.pro.coinbase.com/products/\{tradePair.toString('-')}/candles?granularity=\{secondsPerCandle}&start=\{startDateString}&end=\{endDateString}";

            if (startTime == EARLIEST_DATA) {
                // signal more data is false
                return CompletableFuture.completedFuture(Collections.emptyList());
            }

            return HttpClient.newHttpClient().sendAsync(
                            HttpRequest.newBuilder()
                                    .uri(URI.create(uriStr))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {
                        logger.info(STR."coinbase response: \{response}");
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
                            logger.error(STR."CoinbaseCandleDataSupplier.get()\{response}");
                            return Collections.emptyList();
                        }
                    });
        }
    }
}