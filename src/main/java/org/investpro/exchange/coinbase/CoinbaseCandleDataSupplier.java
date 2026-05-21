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
import java.util.Locale;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

public class CoinbaseCandleDataSupplier extends CandleDataSupplier {
    private static final Logger logger = LoggerFactory.getLogger(CoinbaseCandleDataSupplier.class);
    private static final String PUBLIC_PRODUCT_CANDLES_URL =
            "https://api.coinbase.com/api/v3/brokerage/market/products/%s/candles";
    private static final int MAX_COINBASE_CANDLES_PER_REQUEST = 349;
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
                86400    // 1 day
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

        CoinbaseGranularity granularity = CoinbaseGranularity.fromSeconds(secondsPerCandle);
        int requestCandles = Math.min(MAX_COINBASE_CANDLES_PER_REQUEST, Math.max(1, numCandles));
        int startTime = Math.max(endTime.get() - (requestCandles * granularity.seconds()), EARLIEST_DATA);

        String uriStr = "%s?granularity=%s&start=%d&end=%d&limit=%d".formatted(
                PUBLIC_PRODUCT_CANDLES_URL.formatted(encode(tradePair.toString('-').toUpperCase(Locale.ROOT))),
                encode(granularity.apiName()),
                startTime,
                endTime.get(),
                requestCandles);

        if (startTime == EARLIEST_DATA) {
            // signal more data is false
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        return HttpClient.newHttpClient().sendAsync(
                        HttpRequest.newBuilder()
                                .uri(URI.create(uriStr))
                                .GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> {
                    String body = response.body();

                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        logger.warn("Coinbase candles request failed for {} {}: status={}, body={}",
                                tradePair,
                                granularity.apiName(),
                                response.statusCode(),
                                abbreviate(body));
                        return Collections.emptyList();
                    }

                    if (body == null || body.isBlank()) {
                        logger.warn("Coinbase candles request returned an empty body for {} {}", tradePair, granularity.apiName());
                        return Collections.emptyList();
                    }

                    String trimmed = body.trim();
                    if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
                        logger.warn("Coinbase candles request returned non-JSON data for {} {}: {}",
                                tradePair,
                                granularity.apiName(),
                                abbreviate(trimmed));
                        return Collections.emptyList();
                    }

                    logger.debug("Coinbase candles response: {}", body);

                    JsonNode res;
                    try {
                        res = OBJECT_MAPPER.readTree(body);
                    } catch (JsonProcessingException ex) {
                        logger.warn("Unable to parse Coinbase candles response for {} {}: {}",
                                tradePair,
                                granularity.apiName(),
                                ex.getOriginalMessage());
                        return Collections.emptyList();
                    }

                    if (res.has("message") || res.has("error")) {
                        logger.error("CoinbaseCandleDataSupplier.get() {}",
                                res.has("message") ? res.get("message").asText() : res.get("error").asText());
                        return Collections.emptyList();
                    }

                    JsonNode candles = res.has("candles") ? res.get("candles") : res;

                    if (candles != null && candles.isArray() && !candles.isEmpty()) {
                        List<CandleData> candleData = new ArrayList<>();
                        for (JsonNode candle : candles) {
                            long candleStart = parseLong(candle.path("start").asText("0"));
                            if (candleStart + granularity.seconds() > endTime.get()) {
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
                        logger.error("CoinbaseCandleDataSupplier.get() empty candle payload for {} {}: {}",
                                tradePair,
                                granularity.apiName(),
                                abbreviate(body));
                        return Collections.emptyList();
                    }
                });
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "";
        }

        String trimmed = value.trim().replaceAll("\\s+", " ");
        if (trimmed.length() <= 240) {
            return trimmed;
        }

        return trimmed.substring(0, 240) + "...";
    }

    private String encode(String value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    @Contract(pure = true)
    private @NotNull String granularityName(int seconds) {
        return CoinbaseGranularity.fromSeconds(seconds).apiName();
    }

    private record CoinbaseGranularity(String apiName, int seconds) {
        private static final List<CoinbaseGranularity> SUPPORTED = List.of(
                new CoinbaseGranularity("ONE_MINUTE", 60),
                new CoinbaseGranularity("FIVE_MINUTE", 300),
                new CoinbaseGranularity("FIFTEEN_MINUTE", 900),
                new CoinbaseGranularity("THIRTY_MINUTE", 1800),
                new CoinbaseGranularity("ONE_HOUR", 3600),
                new CoinbaseGranularity("TWO_HOUR", 7200),
                new CoinbaseGranularity("SIX_HOUR", 21600),
                new CoinbaseGranularity("ONE_DAY", 86400)
        );

        private static CoinbaseGranularity fromSeconds(int requestedSeconds) {
            return SUPPORTED.stream()
                    .filter(granularity -> granularity.seconds() == requestedSeconds)
                    .findFirst()
                    .orElseGet(() -> nearestSupported(requestedSeconds));
        }

        private static CoinbaseGranularity nearestSupported(int requestedSeconds) {
            CoinbaseGranularity nearest = SUPPORTED.stream()
                    .min(Comparator.comparingInt(granularity -> Math.abs(granularity.seconds() - requestedSeconds)))
                    .orElse(SUPPORTED.get(0));

            logger.warn(
                    "Coinbase does not support {}s candles; using {} ({}s) instead.",
                    requestedSeconds,
                    nearest.apiName(),
                    nearest.seconds());
            return nearest;
        }
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
