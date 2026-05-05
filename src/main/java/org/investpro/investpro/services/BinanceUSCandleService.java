package org.investpro.investpro.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import org.investpro.investpro.CandleDataSupplier;
import org.investpro.investpro.components.BinanceUSCandleDataSupplier;
import org.investpro.investpro.models.CandleData;
import org.investpro.investpro.models.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Getter
@Setter
public class BinanceUSCandleService {

    private static final Logger logger = LoggerFactory.getLogger(BinanceUSCandleService.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final HttpClient client = HttpClient.newHttpClient();
    private final String apiKey;
    private final String apiSecret;
    private final TradePair tradePair;
    private final int secondsPerCandle;
    private final AtomicInteger endTime = new AtomicInteger(-1);
    long numCandles = 1000;


    public BinanceUSCandleService(String apiKey, String apiSecret, TradePair tradePair, int secondsPerCandle) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.tradePair = tradePair;
        this.secondsPerCandle = secondsPerCandle;
    }

    public Set<Integer> getSupportedGranularity() {
        return new TreeSet<>(Set.of(60, 180, 300, 900, 1800, 3600, 14400, 86400));
    }

    public CompletableFuture<List<CandleData>> get() {
        if (endTime.get() == -1) {
            endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
        }

        long endTimeMillis = endTime.get() * 1000L;

        long startTimeMillis = Math.max(endTimeMillis - (numCandles * secondsPerCandle * 1000L), 1422144000000L);

        String url = String.format(
                "https://api.binance.us/api/v3/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                tradePair.toSymbol(),
                getBinanceInterval(secondsPerCandle),
                startTimeMillis,
                endTimeMillis,
                numCandles
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("X-MBX-APIKEY", apiKey)
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        logger.error("Failed to fetch candles. Status: {} Body: {}", response.statusCode(), response.body());
                        throw new RuntimeException("Error fetching candle data");
                    }

                    try {
                        JsonNode res = OBJECT_MAPPER.readTree(response.body());
                        List<CandleData> candles = new ArrayList<>();
                        for (JsonNode candle : res) {
                            candles.add(new CandleData(
                                    candle.get(1).asDouble(),
                                    candle.get(4).asDouble(),
                                    candle.get(2).asDouble(),
                                    candle.get(3).asDouble(),
                                    toEpochSeconds(candle.get(0).asLong()),
                                    candle.get(5).asLong()
                            ));
                        }

                        candles.sort(Comparator.comparingLong(CandleData::getOpenTime));
                        endTime.set((int) (startTimeMillis / 1000L));

                        return candles;
                    } catch (JsonProcessingException e) {
                        throw new RuntimeException("JSON parsing error", e);
                    }
                });
    }

    private String getBinanceInterval(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "1m";
            case 180 -> "3m";
            case 300 -> "5m";
            case 900 -> "15m";
            case 1800 -> "30m";
            case 3600 -> "1h";
            case 14400 -> "4h";
            case 86400 -> "1d";
            default -> throw new IllegalArgumentException("Unsupported interval: " + secondsPerCandle);
        };
    }

    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {

        return new BinanceUSCandleDataSupplier(secondsPerCandle, tradePair);
    }

    public List<CandleData> getHistoricalCandles(TradePair symbol, Instant startTime, Instant endTime, int interval) {
        try {
            long startTimeMillis = Math.max(0L, startTime.toEpochMilli());
            long endTimeMillis = Math.max(startTimeMillis, endTime.toEpochMilli());
            String url = String.format(
                    "https://api.binance.us/api/v3/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                    symbol.toSymbol(),
                    getBinanceInterval(interval),
                    startTimeMillis,
                    endTimeMillis,
                    numCandles
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                logger.warn("Failed to fetch Binance US historical candles: {}", response.body());
                return List.of();
            }

            JsonNode res = OBJECT_MAPPER.readTree(response.body());
            List<CandleData> candles = new ArrayList<>();
            for (JsonNode candle : res) {
                candles.add(new CandleData(
                        candle.get(1).asDouble(),
                        candle.get(4).asDouble(),
                        candle.get(2).asDouble(),
                        candle.get(3).asDouble(),
                        toEpochSeconds(candle.get(0).asLong()),
                        candle.get(5).asDouble()
                ));
            }

            candles.sort(Comparator.comparingLong(CandleData::getOpenTime));
            return candles;
        } catch (Exception e) {
            logger.error("Error fetching Binance US historical candles for {}", symbol, e);
            return List.of();
        }
    }

    public CompletableFuture<Optional<CandleData>> fetchCandleDataForInProgressCandle(TradePair tradePair, Instant start, long offset, int secondsPerCandle) {
        return CompletableFuture.supplyAsync(() -> getHistoricalCandles(
                        tradePair,
                        start.minusSeconds(secondsPerCandle),
                        start.plusSeconds(Math.max(offset, secondsPerCandle)),
                        secondsPerCandle
                ).stream()
                .filter(candle -> candle.getOpenTime() >= start.getEpochSecond())
                .max(Comparator.comparingInt(CandleData::getOpenTime)));
    }

    private int toEpochSeconds(long exchangeOpenTime) {
        long epochSeconds = exchangeOpenTime > Integer.MAX_VALUE ? exchangeOpenTime / 1000L : exchangeOpenTime;
        return Math.toIntExact(epochSeconds);
    }
}
