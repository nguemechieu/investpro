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


    private static final Logger logger = LoggerFactory.getLogger(org.investpro.CoinbaseCandleDataSupplier.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int EARLIEST_DATA = 1422144000; // roughly the first trade

    OandaCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
    }

    public OandaCandleDataSupplier(int numCandles, int secondsPerCandle, TradePair tradePair, IntegerProperty endTime) {
        super(numCandles, secondsPerCandle, tradePair, endTime);
    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        // https://docs.pro.coinbase.com/#get-historic-rates
        return new TreeSet<>(Set.of(60, 300, 900, 3600, 21600, 86400));
    }

    @Override
    public List<CandleData> getCandleData() {
        return null;
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return null;
    }

    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return null;
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return null;
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