
package org.investpro.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.investpro.investpro.BinanceUs.BinanceUs;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static java.lang.System.out;
import static java.time.format.DateTimeFormatter.ISO_INSTANT;

public class BinanceUS {
    TradePair BTC_USD = TradePair.of(Currency.ofCrypto("BTC"), Currency.ofFiat("USD"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public CandleStickChartContainer start() throws URISyntaxException, IOException {

        CandleStickChartContainer candleStickChartContainer = new CandleStickChartContainer(new BinanceU(), BTC_USD, true);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return candleStickChartContainer;
    }

    public static class BinanceU extends Exchange {   // private static final URI urO=URI.create("wss://stream.binance.us:9443");


        private static String x;
        private static String str;

        BinanceU() {
            super(null); // This argument is for creating a WebSocket client for live trading data.
        }

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new BinanceUCandleDataSupplier(secondsPerCandle, tradePair);
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
                IntegerProperty afterCursor = new SimpleIntegerProperty(-1);
                List<Trade> tradesBeforeStopTime = new ArrayList<>();

                // For Public Endpoints, our rate limit is 3 requests per second, up to 6 requests per second in
                // burst.
                // We will know if we get rate limited if we get a 429 response code.
                // FIXME: We need to address this!
                for (int i = 0; !futureResult.isDone(); i++) {
                    String uriStr = "https://api.binance.us/api/v3/trades?symbol=" + String.valueOf(tradePair).replace("/", "");

                    if (i != 0) {
                        uriStr += "?after=" + afterCursor.get();
                    }

                    try {
                        HttpRequest.Builder req = HttpRequest.newBuilder();
                        req.header("Accept", "application/json");
                        req.header("Authorization", BinanceUs.getApiKey());
                        HttpResponse<String> response = HttpClient.newHttpClient().send(
                                req.build(),
                                HttpResponse.BodyHandlers.ofString());

                        Log.info("response headers: " + response.headers());
                        if (response.headers().firstValue("cb-after").isEmpty()) {
                            futureResult.completeExceptionally(new RuntimeException(
                                    "Binance Us trades response did not contain header \"cb-after\": " + response));
                            return;
                        }

                        afterCursor.setValue(Integer.valueOf((response.headers().firstValue(" cb-after").get())));

                        JsonNode tradesResponse = OBJECT_MAPPER.readTree(response.body());

                        if (!tradesResponse.isArray()) {
                            futureResult.completeExceptionally(new RuntimeException(
                                    "Trades response was not an array!"));
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
                                            DefaultMoney.ofCrypto(trade.get("qty").asText(), tradePair.getBaseCurrency()),
                                            Side.getSide(trade.get("isBuyerMaker").asText()), trade.get("id").asLong(), time));
// fvjnbskl
//
//                                    "id": 981492,
//                                            "price": "0.00380100",
//                                            "qty": "0.22000000",
//                                            "quoteQty": "0.00083622",
//                                            "time": 1637128016269,
//                                            "isBuyerMaker": false,
//                                            "isBestMatch": true


                                }
                            }
                        }
                    } catch (IOException | InterruptedException ex) {
                        Log.error("ex: " + ex);
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
            //  String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
            //        currentCandleStartedAt, ZoneOffset.UTC));
            //  long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
            // Get the closest supported granularity to the ideal granularity.
            //  int actualGranularity = getCandleDataSupplier(secondsPerCandle, tradePair).getSupportedGranularities().stream()
            //     .min(Comparator.comparingInt(i -> (int) Math.abs(i - idealGranularity)))
            //   .orElseThrow(() -> new NoSuchElementException("Supported granularities was empty!"));
            // TODO: If actualGranularity = secondsPerCandle there are no sub-candles to fetch and we must get all the
            //  data for the current live syncing candle from the raw trades method.
            String timeFrame = x + str;
            return HttpClient.newHttpClient().sendAsync(

                            HttpRequest.newBuilder()
                                    .uri(URI.create("https://api.binance.us/api/v3/klines?symbol=" + String.valueOf(tradePair).replace("/", "") + "&interval=" + timeFrame))
                                    .GET().build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {
                        Log.info("Binance us Response: " + response);
                        JsonNode res;
                        try {
                            res = OBJECT_MAPPER.readTree(response);
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

                                currentTill = currCandle.get(0).asInt();
                                lastTradePrice = currCandle.get(4).asDouble();
                                foundFirst = true;

                                continue;

                            } else {
                                if (!foundFirst) {
                                    // FIXME: Why are we only using the first sub-candle here?
                                    currentTill = currCandle.get(0).asInt();
                                    lastTradePrice = currCandle.get(4).asDouble();
                                    foundFirst = true;
                                }
                            }

                            openPrice = currCandle.get(1).asDouble();

                            if (currCandle.get(2).asDouble() > highSoFar) {
                                highSoFar = currCandle.get(2).asDouble();
                            }

                            if (currCandle.get(3).asDouble() < lowSoFar) {
                                lowSoFar = currCandle.get(3).asDouble();
                            }

                            volumeSoFar += currCandle.get(5).asDouble();
                        }

                        int openTime = (int) (currentCandleStartedAt.toEpochMilli() / 1000L);

                        return Optional.of(new InProgressCandleData(openTime, openPrice, highSoFar, lowSoFar,
                                currentTill, lastTradePrice, volumeSoFar));
                    });

        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {

        }

        public static class BinanceUCandleDataSupplier extends CandleDataSupplier {
            private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            private static final int EARLIEST_DATA = 1422144000; // roughly the first trade
            private static final Set<Integer> GRANULARITIES = Set.of(60, 60 * 5, 60 * 15, 60 * 30, 3600, 3600 * 2, 3600 * 3, 3600 * 4, 3600 * 6, 3600 * 24, 3600 * 24 * 7, 3600 * 24 * 7 * 4, 3600 * 24 * 365);

            BinanceUCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
                super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
            }

            @Override
            public Set<Integer> getSupportedGranularities() {

                return new TreeSet<>(GRANULARITIES);
            }

            @Override
            public Future<List<CandleData>> get() {
                if (endTime.get() == -1) {
                    endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
                }

                String endDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        .format(LocalDateTime.ofEpochSecond(endTime.get(), 0, ZoneOffset.UTC));

                final int[] startTime = {Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA)};
                String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                        .format(LocalDateTime.ofEpochSecond(startTime[0], 0, ZoneOffset.UTC));

//
                Log.info("Start date: " + startDateString)
                ;//
//                ;
                Log.info("End date: " + endDateString);

                Log.info("TradePair " + String.valueOf(tradePair
                ).replace("/", ""));
                Log.info("Second per Candle: " + secondsPerCandle);

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
                out.println("timeframe: " + timeFrame);
                String uriStr = "https://api.binance.us/api/v3/klines?symbol=" +
                        String.valueOf(tradePair
                        ).replace("/", "") +
                        "&interval=" + timeFrame;


                if (startTime[0] == EARLIEST_DATA) {
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
                            Log.info("Binance us Response: " + response);
                            JsonNode res;
                            try {
                                res = OBJECT_MAPPER.readTree(response);
                            } catch (JsonProcessingException ex) {
                                throw new RuntimeException(ex);
                            }

                            if (!res.isEmpty()) {
                                // Remove the current in-progress candle
                                if (res.get(0).asInt() + secondsPerCandle > endTime.get()) {
                                    ((ArrayNode) res).remove(0);
                                }


                                ArrayList<CandleData> candleData = new ArrayList<>();

                                for (JsonNode candle : res) {

                                    candleData.add(
                                            new CandleData(candle.get(1).asDouble(),  // open price
                                                    candle.get(4).asDouble(),  // close price
                                                    candle.get(2).asDouble(),  // high price
                                                    candle.get(3).asDouble(),  // low price
                                                    candle.get(0).asInt(),     // open time
                                                    candle.get(5).asDouble())   // volume
                                    );

                                    endTime.set(candle.get(0).asInt());
                                    Log.info("candle : Open time" + new Date(candleData.get(0).getOpenTime()) + "close time" + new Date(candle.get(0).asInt()) + "\n" + "candle:" + candleData);
                                }


                                candleData.sort(Comparator.comparingInt(CandleData::getOpenTime));
                                return candleData;
                            } else {
                                return Collections.emptyList();
                            }

                        });
            }
        }
    }
}