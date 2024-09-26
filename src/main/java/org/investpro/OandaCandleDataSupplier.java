package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
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


public class OandaCandleDataSupplier extends CandleDataSupplier {

    private static final Logger logger = LoggerFactory.getLogger(OandaCandleDataSupplier.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int EARLIEST_DATA = 1422144000; // roughly the first trade
    private final HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
    private final String apiSecret;

    OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair, String apiSecret) {
        super(240, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        this.apiSecret = apiSecret;
        requestBuilder.header(
                "Authorization",
                "Bearer %s".formatted(apiSecret)
        );
    }



    @Override
    public Set<Integer> getSupportedGranularity() {
        // https://docs.pro.coinbase.com/#get-historic-rates
        return new TreeSet<>(Set.of(60, 300, 900, 3600, 21600, 86400));
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }

    public static int getTimeFromString(@NotNull String timeString) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(
                "yyyy-MM-dd'T'HH:mm:ss"
        );
        int tim = (int) Instant.parse(timeString).getEpochSecond();
        logger.info(STR."Created timestamp: \{timeString + tim}");
        return tim;
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
       // "https://api-fxtrade.oanda.com/v3/instruments/USD_CAD/candles?price=BA&from=2016-10-17T15%3A00%3A00.000000000Z&granularity=M1"
        String uriStr = STR."https://api-fxtrade.oanda.com/v3/instruments/\{tradePair.toString('_')}/candles?count=200&price=M&from="+startDateString+"&granularity="+granularityToString(secondsPerCandle);

        if (startTime == EARLIEST_DATA) {
            // signal more data is false
            return CompletableFuture.completedFuture(Collections.emptyList());
        }


        requestBuilder.header("Authorization", STR."Bearer \{apiSecret}");
        return HttpClient.newHttpClient().sendAsync(
                        requestBuilder
                                .uri(URI.create(uriStr))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(response -> {

                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                        logger.info(STR."OANDA response: \{res}");
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }
                    List<CandleData> candleDatas = new ArrayList<>();

                    if (!res.get("candles").isEmpty()) {


                        logger.info(STR."Response\{res.get("candles")}");
                        // Remove the current in-progress candle

                        for (JsonNode candle : res.get("candles")) {

                            int time;
                            if (candle.has("time")) {

                                time = getTimeFromString(candle.get("time").asText());

                                if (time + secondsPerCandle > endTime.get()) {
                                    ((ArrayNode) candle.get("candles")).remove(0);
                                }
                                endTime.set(startTime);
                                int closeTime = 0;

                                if ((-time + (System.currentTimeMillis() / 1000)) == secondsPerCandle) {
                                    closeTime = (candle.get("time").asInt());
                            }
                                candleDatas.add(new CandleData(
                                        candle.get("mid").get("o").asDouble(),  // open price
                                        candle.get("mid").get("c").asDouble(),  // close price
                                        candle.get("mid").get("h").asDouble(),  // high price
                                        candle.get("mid").get("l").asDouble(),  // low price
                                        candle.get("mid").get("o").asInt(),     // open time.
                                    closeTime,  // close time
                                        candle.get("volume").asDouble()   // volume
                            ));


                            }
                            candleDatas.sort(Comparator.comparingInt(CandleData::getOpenTime));

                            logger.info(candleDatas.toString());
                            return candleDatas;
                        }
                    }


                        return Collections.emptyList();

                });
    }

    private @NotNull String granularityToString(int actualGranularity) {

        String x;
        String str;
        if (actualGranularity < 3600) {
            x = String.valueOf(actualGranularity / 60);
            str = "M";
        } else if (actualGranularity < 86400) {
            x = String.valueOf((actualGranularity / 3600));
            str = "H";
        } else if (actualGranularity < 604800) {
            x = "";//String.valueOf(secondsPerCandle / 86400);
            str = "D";
        } else if (actualGranularity < 2592000) {
            x = String.valueOf((actualGranularity / 604800));
            str = "W";
        } else {
            x = String.valueOf((actualGranularity * 7 / 2592000 / 7));
            str = "M";
        }
        return str + x;

    }
}