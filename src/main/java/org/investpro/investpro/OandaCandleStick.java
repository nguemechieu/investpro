package org.investpro.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.application.Platform;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.scene.layout.AnchorPane;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static java.lang.System.out;

public class OandaCandleStick {
    private static final TradePair BTC_USD = TradePair.of(Currency.ofFiat("EUR"), Currency.ofFiat("USD"));
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public CandleStickChartContainer start() throws URISyntaxException, IOException {
        Platform.setImplicitExit(false);
        Thread.setDefaultUncaughtExceptionHandler((thread, exception) -> Log.error("[" + thread + "]: \n" + exception));
        CandleStickChartContainer candleStickChartContainer =
                new CandleStickChartContainer(
                        new Oandas("https://api-fxtrade.oanda.com/", OandaClient.getApi_key(), OandaClient.getAccountID()), BTC_USD, true);
        AnchorPane.setTopAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setLeftAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setRightAnchor(candleStickChartContainer, 30.0);
        AnchorPane.setBottomAnchor(candleStickChartContainer, 30.0);
        candleStickChartContainer.setMaxSize(Double.MAX_VALUE,
                Double.MAX_VALUE);
        return candleStickChartContainer;
    }

    class Oandas extends OandaClient {
        public Oandas(String host, String api_key, String accountID) {
            super(host, api_key, accountID);
        }


        @Override
        public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
            return new OandaCandleDataSupplier(secondsPerCandle, tradePair);
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
                IntegerProperty afterCursor = new SimpleIntegerProperty(0);
                List<Trade> tradesBeforeStopTime = new ArrayList<>();

                // For Public Endpoints, our rate limit is 3 requests per second, up to 6 requests per second in
                // burst.
                // We will know if we get rate limited if we get a 429 response code.
                // FIXME: We need to address this!
                for (int i = 0; !futureResult.isDone(); i++) {
                    String uriStr = "https://api-fxtrade.oanda.com/v3/accounts/" + OandaClient.accountID;
                    uriStr += "/trades/instruments" + tradePair.toString('-');

                    if (i != 0) {
                        uriStr += "?date=" + afterCursor.get();
                    }

                    try {
                        HttpRequest.Builder re = HttpRequest.newBuilder();
                        re.header("Authorization", "Bearer " + OandaClient.getApi_key());
                        re.uri(URI.create(uriStr));

                        HttpResponse<String> response = HttpClient.newHttpClient().send(re.build(), HttpResponse.BodyHandlers.ofString());

                        Log.info("response headers: " + response.headers());
                        if (response.headers().firstValue("date").isEmpty()) {
                            futureResult.completeExceptionally(new RuntimeException(
                                    "OANDA trades response did not contain header \"date\": " + response));
                            return;
                        }

                        afterCursor.setValue(Integer.valueOf((response.headers().firstValue("date").get())));

                        JsonNode tradesResponse = OBJECT_MAPPER.readTree(response.body());

                        if (!tradesResponse.isArray()) {
                            futureResult.completeExceptionally(new RuntimeException(
                                    "Oanda trades response was not an array!"));
                        }
                        if (tradesResponse.isEmpty()) {
                            futureResult.completeExceptionally(new IllegalArgumentException("tradesResponse was empty"));
                        } else {
                            for (int j = 0; j < tradesResponse.size(); j++) {
                                JsonNode trade = tradesResponse.get(j);

                                Instant time = Instant.from(Instant.ofEpochSecond(Trade.candle.getOpenTime()));
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

            Log.info("TradePair " + String.valueOf(tradePair
            ).replace("/", ""));
            Log.info("Second per Candle: " + secondsPerCandle);

            String x;
            String strs;
            if (secondsPerCandle < 3600) {
                x = String.valueOf(secondsPerCandle / 60);
                strs = "M";
            } else if (secondsPerCandle < 86400) {
                x = String.valueOf((secondsPerCandle / 3600));
                strs = "H";
            } else if (secondsPerCandle < 604800) {
                x = "";//String.valueOf(secondsPerCandle / 86400);
                strs = "D";
            } else if (secondsPerCandle < 2592000) {
                x = "";// String.valueOf((secondsPerCandle / 604800));
                strs = "W";
            } else {
                x = "";//String.valueOf((secondsPerCandle * 7 / 2592000 / 7));
                strs = "M";
            }
            String timeFrame = strs + x;
            out.println("Timeframe: " + timeFrame);
            //  String startDateString = DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.ofInstant(
            //      currentCandleStartedAt, ZoneOffset.UTC));
            String urlw = "https://api-fxtrade.oanda.com/v3/instruments/" + tradePair.toString('_') + "/candles?count=300&price=M&granularity=" + timeFrame;
            HttpRequest.Builder re = HttpRequest.newBuilder();

            re.header("Authorization", "Bearer " + OandaClient.getApi_key());
            re.uri(URI.create(urlw));

            return HttpClient.newHttpClient().sendAsync(
                            re.build(),
                            HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body)
                    .thenApply(response -> {
                        Log.info("Oanda response: " + response);

                        JsonNode res;
                        try {


                            res = OBJECT_MAPPER.readTree(response);
                        } catch (JsonProcessingException ex) {
                            throw new RuntimeException(ex);
                        }

                        if (res.isEmpty()) {
                            return Optional.empty();
                        }


                        int currentTill = -1;
                        double openPrice = -1;
                        double highSoFar = -1;
                        double lowSoFar = Double.MAX_VALUE;
                        double volumeSoFar = 0;
                        double lastTradePrice = -1;

                        currentTill = Trade.candle.getOpenTime();
                        lastTradePrice = Trade.candle.getClosePrice();

                        openPrice = Trade.candle.getOpenPrice();

                        if (Trade.candle.getHighPrice() > highSoFar) {
                            highSoFar = Trade.candle.getHighPrice();
                        }

                        if (Trade.candle.getLowPrice() < lowSoFar) {
                            lowSoFar = Trade.candle.getLowPrice();
                        }

                        volumeSoFar += Trade.candle.getVolume();

                        int openTime = (int) (currentCandleStartedAt.toEpochMilli() / 1000L);

                        return Optional.of(new InProgressCandleData(openTime, openPrice, highSoFar, lowSoFar,
                                currentTill, lastTradePrice, volumeSoFar));
                    });

        }

        public static class OandaCandleDataSupplier extends CandleDataSupplier {
            private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
                    .registerModule(new JavaTimeModule())
                    .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            private static final int EARLIEST_DATA = 1422144000; // roughly the first trade

            OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
                super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
            }

            @Override
            public Set<Integer> getSupportedGranularities() {
                // https://docs.pro.coinbase.com/#get-historic-rates
                return new TreeSet<>(Set.of(60, 300, 60 * 30, 900, 3600 * 4, 3600 * 2, 3600, 21600, 3600 * 24 * 7, 3600 * 12, 86400));
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


                String actualGranularity = "D";
                String uriStr = String.format("https://api-fxtrade.oanda.com/v3/instruments/" + tradePair.toString('_') + "/candles?count=10&price=M&from=" + startDateString + "&granularity=" + actualGranularity);

                if (startTime == EARLIEST_DATA) {
                    // signal more data is false
                    return CompletableFuture.completedFuture(Collections.emptyList());
                }

                HttpRequest.Builder re = HttpRequest.newBuilder();
                re.header("Authorization", "Bearer " + OandaClient.getApi_key());
                re.uri(URI.create(uriStr));


                return HttpClient.newHttpClient().sendAsync(
                                re.build(),
                                HttpResponse.BodyHandlers.ofString())
                        .thenApply(HttpResponse::body)
                        .thenApply(response -> {
                            Log.info(" Oanda candle response: " + response);


                            JSONObject res = new JSONObject(response);
                            List<CandleData> candleData = null;
                            if (!res.isEmpty()) {
                                // Remove the current in-progress candle

                                endTime.set(startTime);

                                Log.info("res " + res);
                                candleData = new ArrayList<>();


                                if (res.has("candles")) {
                                    JSONArray candles = res.getJSONArray("candles");
                                    for (int i = 0; i < candles.length(); i++) {
                                        JSONObject jsonObject = candles.getJSONObject(i).getJSONObject("mid");
                                        String time = candles.getJSONObject(i).getString("time");


                                        double volume = candles.getJSONObject(i).getInt("volume");
                                        System.out.println("Candle " + Trade.candle);
                                        try {
                                            Trade.candle = new CandleData(

                                                    Double.parseDouble(jsonObject.getString("o")),
                                                    Double.parseDouble(jsonObject.getString("c")),

                                                    Double.parseDouble(jsonObject.getString("h")),
                                                    Double.parseDouble(jsonObject.getString("l")),
                                                    (int) StringToDate(time).getTime(),
                                                    Double.parseDouble(String.valueOf(volume)));

                                            candleData.add(Trade.candle);

                                        } catch (Exception e) {
                                            Log.error("Error parsing" + e);
                                        }
                                    }

                                    candleData.sort(Comparator.comparingInt(CandleData::getOpenTime));

                                } else {
                                    Log.info("Candle data is not sorted");
                                    return Collections.emptyList();
                                }
                            }

                            return candleData;
                        });
            }

            private Date StringToDate(@NotNull String time) throws ParseException {
                // "2017-05-05T21:00:00.000000000Z
                return new SimpleDateFormat("dd-MM-yyyy")
                        .parse(time.replace("0000000Z", ""));

            }
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {

        }
    }
}
