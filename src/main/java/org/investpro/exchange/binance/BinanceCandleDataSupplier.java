package org.investpro.exchange.binance;

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

public class BinanceCandleDataSupplier extends CandleDataSupplier {
    private static final Logger logger = LoggerFactory.getLogger(BinanceCandleDataSupplier.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String DEFAULT_API_BASE = "https://api.binance.com/api/v3";
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .build();
    private final String apiBase;
    private final String symbol;
    @FunctionalInterface
    public interface RequestSender {
        HttpResponse<String> send(HttpRequest request) throws java.io.IOException, InterruptedException;
    }
    private final RequestSender requestSender;
    private final java.util.concurrent.Executor requestExecutor;

    public BinanceCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        this(secondsPerCandle, tradePair, DEFAULT_API_BASE);
    }

    public BinanceCandleDataSupplier(int secondsPerCandle, TradePair tradePair, String apiBase) {
        this(secondsPerCandle, tradePair, apiBase, null, null);
    }

    public BinanceCandleDataSupplier(int secondsPerCandle, TradePair tradePair, String apiBase,
                                   RequestSender requestSender, java.util.concurrent.Executor requestExecutor) {
        super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
        this.requestSender = requestSender;
        this.requestExecutor = requestExecutor;
        this.apiBase = apiBase == null || apiBase.isBlank() ? DEFAULT_API_BASE : apiBase;
        this.symbol = tradePair.toCompactSymbol().toUpperCase(Locale.ROOT);
    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        // Binance supported intervals: 1m 3m 5m 15m 30m 1h 2h 4h 6h 8h 12h 1d 3d 1w 1M
        return new TreeSet<>(Set.of(60, 180, 300, 900, 1800, 3600, 7200, 14400, 21600, 28800, 43200, 86400, 259200,
                604800, 2592000));
    }

    @Override
    public List<CandleData> getCandleData() {
        return Collections.emptyList();
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new BinanceCandleDataSupplier(secondsPerCandle, tradePair, apiBase, requestSender, requestExecutor);
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

        String interval = getIntervalString(secondsPerCandle);
        String url = "%s/klines?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d".formatted(apiBase, symbol,
                interval, startTimeMs, endTimeMs, numCandles);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(java.time.Duration.ofSeconds(15))
                .GET()
                .build();

        CompletableFuture<HttpResponse<String>> responseFuture;
        if (requestSender == null) {
            responseFuture = HTTP_CLIENT.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } else {
            try {
                responseFuture = CompletableFuture.supplyAsync(() -> {
                    try { return requestSender.send(request); }
                    catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new java.util.concurrent.CompletionException(exception);
                    } catch (java.io.IOException exception) { throw new java.util.concurrent.CompletionException(exception); }
                }, requestExecutor);
            } catch (java.util.concurrent.RejectedExecutionException exception) {
                responseFuture = CompletableFuture.failedFuture(exception);
            }
        }
        return responseFuture
                .thenApply(response -> {
                    if (response.statusCode() != 200) {
                        throw new java.util.concurrent.CompletionException(new java.io.IOException(
                                "Binance candles returned HTTP " + response.statusCode()));
                    }
                    return response.body();
                })
                .thenApply(this::parseCandleData)
                .toCompletableFuture();
    }

    private String getIntervalString(int secondsPerCandle) {
        return switch (secondsPerCandle) {
            case 60 -> "1m";
            case 180 -> "3m";
            case 300 -> "5m";
            case 900 -> "15m";
            case 1800 -> "30m";
            case 3600 -> "1h";
            case 7200 -> "2h";
            case 14400 -> "4h";
            case 21600 -> "6h";
            case 28800 -> "8h";
            case 43200 -> "12h";
            case 86400 -> "1d";
            case 259200 -> "3d";
            case 604800 -> "1w";
            case 2592000 -> "1M";
            default -> "";
        };
    }

    private List<CandleData> parseCandleData(String responseBody) {
        List<CandleData> candleData = new ArrayList<>();
        try {
            JsonNode root = OBJECT_MAPPER.readTree(responseBody);
            if (root.isArray()) {
                for (JsonNode candle : root) {
                    if (candle.isArray() && candle.size() >= 6) {
                        long openTime = candle.get(0).asLong() / 1000;
                        double openPrice = Double.parseDouble(candle.get(1).asText());
                        double highPrice = Double.parseDouble(candle.get(2).asText());
                        double lowPrice = Double.parseDouble(candle.get(3).asText());
                        double closePrice = Double.parseDouble(candle.get(4).asText());
                        double volume = Double.parseDouble(candle.get(7).asText());

                        CandleData data = new CandleData(openPrice, closePrice, highPrice, lowPrice, (int) openTime,
                                volume);
                        candleData.add(data);
                    }
                }
                if (candleData.isEmpty()) {
                    endTime.set(-1); // Signal that there's no more data
                } else {
                    endTime.set(candleData.getFirst().openTime());
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing Binance candle data", e);
        }
        return candleData;
    }
}
