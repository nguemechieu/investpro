package org.investpro.exchange.bitfinex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.investpro.data.CandleData;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javafx.beans.property.SimpleIntegerProperty;

public class BitfinexCandleDataSupplier extends CandleDataSupplier {
    private static final Logger logger = LoggerFactory.getLogger(BitfinexCandleDataSupplier.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String API_BASE = "https://api-pub.bitfinex.com/v2";
    private final String pair;

    public BitfinexCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        this.pair = "t%s".formatted(tradePair.toString('/'));
    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        // Bitfinex supported timeframes
        return new TreeSet<>(Set.of(60, 300, 900, 1800, 3600, 10800, 21600, 43200, 86400, 604800));
    }

    @Override
    public List<CandleData> getCandleData() {
        return Collections.emptyList();
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new BitfinexCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair,
            Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    @Override
    public Future<List<CandleData>> get() {
        if (endTime.get() == -1) {
            endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
        }

        long endTimeMs = (long) endTime.get() * 1000;
        long startTimeMs = endTimeMs - (long) numCandles * secondsPerCandle * 1000;

        String timeframe = getTimeframeString(secondsPerCandle);
        String url = API_BASE + "/candles/trade:" + timeframe + ":" + pair + "/hist?start=" + startTimeMs + "&end="
                + endTimeMs + "&limit=" + numCandles;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .build();

        return HttpClient.newHttpClient().sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .thenApply(this::parseCandleData)
                .toCompletableFuture();
    }

    private String getTimeframeString(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "1m";
            case 300 -> "5m";
            case 900 -> "15m";
            case 1800 -> "30m";
            case 3600 -> "1h";
            case 10800 -> "3h";
            case 21600 -> "6h";
            case 43200 -> "12h";
            case 86400 -> "1d";
            case 604800 -> "1w";
            default -> "1h";
        };
    }

    private List<CandleData> parseCandleData(String responseBody) {
        List<CandleData> candleData = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            if (root.isArray()) {
                for (JsonNode candle : root) {
                    if (candle.isArray() && candle.size() >= 6) {
                        // Bitfinex format: [timestamp, open, close, high, low, volume]
                        long openTime = candle.get(0).asLong() / 1000;
                        double openPrice = candle.get(1).asDouble();
                        double closePrice = candle.get(2).asDouble();
                        double highPrice = candle.get(3).asDouble();
                        double lowPrice = candle.get(4).asDouble();
                        double volume = candle.get(5).asDouble();

                        CandleData data = new CandleData(openPrice, closePrice, highPrice, lowPrice, (int) openTime,
                                volume);
                        candleData.add(data);
                    }
                }
                if (candleData.isEmpty()) {
                    endTime.set(-1); // Signal that there's no more data
                } else {
                    endTime.set(candleData.get(candleData.size() - 1).openTime());
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing Bitfinex candle data", e);
        }
        return candleData;
    }
}
