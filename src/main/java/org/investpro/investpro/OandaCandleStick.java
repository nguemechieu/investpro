
package org.investpro.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.beans.property.SimpleIntegerProperty;

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

import static java.time.format.DateTimeFormatter.ISO_INSTANT;
import static org.investpro.investpro.OandaClient.accountID;


public class OandaCandleStick {
    private static final TradePair BTC_USD = TradePair.of(Currency.ofFiat("EUR"), Currency.ofFiat("USD"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public CandleStickChartContainer start() throws URISyntaxException, IOException {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> Log.error("[" + thread + "]: " + exception));
        return new CandleStickChartContainer(
                new Oanda(), BTC_USD, true)

                ;
    }

    public static class Oanda extends Exchange {
        String api_key = "7e0018e5e2e0d287c854c5bd8a509712-2c4ed485f470ed2db68159fb308272a8";
        private ExchangeWebSocketClient webSocket;

        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new OandaCandleDataSupplier(secondsPerCandle, tradePair);
        }

        Oanda() {
            super(null);
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

                List<Trade> tradesBeforeStopTime = new ArrayList<>();

                Trade response = null;
                try {
                    response = OandaClient.getTrade(BTC_USD.toString('_'));
                } catch (OandaException e) {
                    throw new RuntimeException(e);
                }
                JsonNode tradesResponse = null;
                try {
                    tradesResponse = OBJECT_MAPPER.readTree(String.valueOf(response));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

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
                                    DefaultMoney.ofCrypto(trade.get("size").asText(), tradePair.getBaseCurrency()),
                                    Side.getSide(trade.get("side").asText()), trade.get("trade_id").asLong(), time));

                        }

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
            long idealGranularity = Math.max(10, secondsIntoCurrentCandle / 200);
            // Get the closest supported granularity to the ideal granularity.
            int actualGranularity = getCandleDataSupplier(secondsPerCandle, tradePair).getSupportedGranularities().stream()
                    .min(Comparator.comparingInt(i -> (int) Math.abs(i - idealGranularity)))
                    .orElseThrow(() -> new NoSuchElementException("Supported granularities was empty!"));
            // TODO: If actualGranularity = secondsPerCandle there are no sub-candles to fetch and we must get all the
            //  data for the current live syncing candle from the raw trades method.

            //"https://api-fxtrade.oanda.com/v3/instruments/USD_JPY/candles?count=10&price=A&from=2016-01-01T00%3A00%3A00.000000000Z&granularity=D"

            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(String.format(
                            "https://api-fxtrade.oanda.com/v3/accounts/" + accountID + "/trades", actualGranularity, startDateString)))
                    .GET().build();
            request.headers().allValues("Authorization").add("Bearer " + api_key);


            return HttpClient.newHttpClient().sendAsync(
                            request,
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {
                        Log.info("Response: " + response);
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
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {

        }

        public static class OandaCandleDataSupplier extends CandleDataSupplier {
            private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            private static final int EARLIEST_DATA = 1422144000; // roughly the first trade
            private static final Set<Integer> GRANULARITIES = Set.of(60, 60 * 2, 60 * 3, 60 * 5, 60 * 15, 60 * 30, 3600, 3600 * 2, 3600 * 3, 3600 * 4, 3600 * 6, 3600 * 8, 3600 * 12, 3600 * 24, 3600 * 24 * 7, 3600 * 24 * 7 * 4, 3600 * 24 * 365);

            OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
                super(300, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
            }

            @Override
            public Set<Integer> getSupportedGranularities() {
                // https://docs.pro.coinbase.com/#get-historic-rates
                return new TreeSet<>(GRANULARITIES);
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
                String uriStr = "https://api-fxtrade.oanda.com/v3/instruments/" + tradePair.toString('_') +
                        "/candles?=count=" + 300 +
                        "?granularity=" + secondsPerCandle +
                        "&start=" + startDateString +
                        "&end=" + endDateString;

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
                            Log.info(" Response: " + response);
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
                                            candle.get(0).asInt(),
                                            // close time
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
        }


    }
}

