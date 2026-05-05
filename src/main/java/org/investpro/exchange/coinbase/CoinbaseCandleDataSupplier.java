package org.investpro.exchange.coinbase;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import javafx.beans.property.SimpleIntegerProperty;
import org.investpro.data.CandleData;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.CandleDataSupplier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class CoinbaseCandleDataSupplier extends CandleDataSupplier {
    private static final Logger logger = LoggerFactory.getLogger(CoinbaseCandleDataSupplier.class);
    private static final String PUBLIC_PRODUCT_CANDLES_URL =
            "https://api.coinbase.com/api/v3/brokerage/market/products/%s/candles";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final int EARLIEST_DATA = 1422144000; // roughly the first trade

    public CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
    }

    @Override
    public Set<Integer> getSupportedGranularities() {
        return new TreeSet<>(Set.of(
                60,      // 1 minute
                300,     // 5 minutes
                900,     // 15 minutes
                1800,    // 30 minutes
                3600,    // 1 hour
                7200,    // 2 hours
                21600,   // 6 hours
                86400,    // 1 day
                108000 // 1 Month
        ));
    }

    @Override
    public List<CandleData> getCandleData() {
        return Collections.emptyList();
    }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(@NotNull TradePair tradePair, Instant currentCandleStartedAt, long secondsIntoCurrentCandle, int secondsPerCandle) {
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

        int startTime = Math.max(endTime.get() - (numCandles * secondsPerCandle), EARLIEST_DATA);

        String uriStr = "%s?granularity=%s&start=%d&end=%d&limit=%d".formatted(PUBLIC_PRODUCT_CANDLES_URL.formatted(encode(tradePair.toString('-').toUpperCase(Locale.ROOT))), encode(granularityName(secondsPerCandle)), startTime, endTime.get(), Math.min(350, Math.max(1, numCandles)));

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
                    logger.debug("Coinbase candles response: {}", response);

                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(response);
                    } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                    }

                    if (res.has("message")) {
                        logger.error("CoinbaseCandleDataSupplier.get() {}",
                                res.get("message").asText());
                        return Collections.emptyList();
                    }

                    JsonNode candles = res.has("candles") ? res.get("candles") : res;

                    if (candles != null && candles.isArray() && !candles.isEmpty()) {
                        List<CandleData> candleData = new ArrayList<>();
                        for (JsonNode candle : candles) {
                            long candleStart = parseLong(candle.path("start").asText("0"));
                            if (candleStart + secondsPerCandle > endTime.get()) {
                                continue;
                            }

                            candleData.add(new CandleData(
                                    parseDouble(candle.path("open").asText(null)),
                                    parseDouble(candle.path("close").asText(null)),
                                    parseDouble(candle.path("high").asText(null)),
                                    parseDouble(candle.path("low").asText(null)),
                                    (int) candleStart,
                                    parseDouble(candle.path("volume").asText(null))
                            ));
                        }

                        endTime.set(startTime);
                        candleData.sort(Comparator.comparingInt(CandleData::openTime));
                        return candleData;
                    } else {
                        logger.error("CoinbaseCandleDataSupplier.get(){}", response);
                        return Collections.emptyList();
                    }
                });
    }

    private String encode(String value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    @Contract(pure = true)
    private @NotNull String granularityName(int seconds) {
        return switch (seconds) {
            case 60 -> "ONE_MINUTE";
            case 300 -> "FIVE_MINUTES";
            case 900 -> "FIFTEEN_MINUTES";
            case 21600 -> "SIX_HOURS";
            case 86400 -> "ONE_DAY";
            default -> "ONE_HOUR";
        };
    }

    private double parseDouble(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }

        try {
            return Double.parseDouble(text);
        } catch (Exception exception) {
            return 0.0;
        }
    }

    private long parseLong(String text) {
        if (text == null || text.isBlank()) {
            return 0L;
        }

        try {
            return Long.parseLong(text);
        } catch (Exception exception) {
            return 0L;
        }
    }
}
