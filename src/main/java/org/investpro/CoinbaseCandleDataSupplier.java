package org.investpro;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;

import java.net.URI;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import static org.investpro.Coinbase.*;
import static org.investpro.Exchange.logger;

class CoinbaseCandleDataSupplier extends CandleDataSupplier {
    protected static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int EARLIEST_DATA = 1422144000; // roughly the first tra

    CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));

    }

    @Override
    public Set<Integer> getSupportedGranularity() {
        // https://docs.pro.coinbase.com/#get-historic-rates
        return new TreeSet<>(Set.of(60, 300, 900, 3600, 21600, 86400
                ));

    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
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

        if (startTime == EARLIEST_DATA) {
            // signal more data is false
            return CompletableFuture.completedFuture(Collections.emptyList());
        }


        return client.sendAsync(
                        requestBuilder
                                .uri(URI.create(
                                        "%s%s".formatted(API_URL, "/products/%s/candles?granularity=%d&start=%s&end=%s".formatted(tradePair.toString('-'), secondsPerCandle, startDateString, endDateString))))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {

                    if (response.statusCode() != 200) {

                        logger.error("Failed to fetch candle data: %s for trade pair: %s".formatted(response.body(), tradePair));


                         throw  new RuntimeException(
                                 "Failed to fetch candle data: %s for trade pair: %s".formatted(response.body(), tradePair)
                                 );



                    }


                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response.body());
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

                        logger.info(
                                "No candle data found for trade pair: %s from %s to display".formatted(tradePair, startDateString)
                        );
                        return Collections.emptyList();
                    }
                });
    }
}