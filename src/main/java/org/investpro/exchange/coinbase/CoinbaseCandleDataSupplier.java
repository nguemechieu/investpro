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

/**
 * Fetches OHLCV candles from the Coinbase Advanced Trade API and, for granularities
 * not natively supported by Coinbase, transparently aggregates smaller candles into
 * the exact requested timeframe.
 *
 * <h2>Supported Coinbase granularities</h2>
 * <pre>
 *   60s (1m) | 300s (5m) | 900s (15m) | 1800s (30m)
 *   3600s (1h) | 7200s (2h) | 21600s (6h) | 86400s (1d)
 * </pre>
 *
 * <h2>Aggregated granularities</h2>
 * <pre>
 *   14400s  (4h)  → fetch 7200s (2h)  candles, aggregate 2→1
 *   604800s (1w)  → fetch 86400s (1d) candles, aggregate 7→1 (calendar week)
 *   2592000s (1M) → fetch 86400s (1d) candles, aggregate to calendar month
 * </pre>
 *
 * <p>No silent substitution occurs – strategies always receive candles matching the
 * timeframe they requested, even when Coinbase does not offer that granularity directly.
 */
public class CoinbaseCandleDataSupplier extends CandleDataSupplier {

    private static final Logger logger = LoggerFactory.getLogger(CoinbaseCandleDataSupplier.class);

    private static final String PUBLIC_PRODUCT_CANDLES_URL =
            "https://api.coinbase.com/api/v3/brokerage/market/products/%s/candles";
    private static final int MAX_COINBASE_CANDLES_PER_REQUEST = 349;
    private static final int EARLIEST_DATA = 1_422_144_000; // ~Jan 2015

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    // ─── Supported Coinbase granularities (direct API support) ───────────────

    private enum CoinbaseGranularity {
        ONE_MINUTE   ("ONE_MINUTE",    60),
        FIVE_MINUTE  ("FIVE_MINUTE",   300),
        FIFTEEN_MINUTE("FIFTEEN_MINUTE", 900),
        THIRTY_MINUTE("THIRTY_MINUTE", 1800),
        ONE_HOUR     ("ONE_HOUR",      3600),
        TWO_HOUR     ("TWO_HOUR",      7200),
        SIX_HOUR     ("SIX_HOUR",      21600),
        ONE_DAY      ("ONE_DAY",       86400);

        final String apiName;
        final int    seconds;

        CoinbaseGranularity(String apiName, int seconds) {
            this.apiName = apiName;
            this.seconds = seconds;
        }

        /** Returns the granularity exactly matching {@code seconds}, or empty. */
        static Optional<CoinbaseGranularity> exact(int seconds) {
            for (CoinbaseGranularity g : values()) {
                if (g.seconds == seconds) return Optional.of(g);
            }
            return Optional.empty();
        }
    }

    // ─── Aggregation plan ─────────────────────────────────────────────────────

    /**
     * Describes how to satisfy a requested (unsupported) timeframe:
     * fetch from {@code base} and aggregate into {@code targetSeconds}.
     */
    private record AggregationPlan(CoinbaseGranularity base, int targetSeconds) {
        /** How many base candles to request so we get enough to fill the target. */
        int expandedRequestCount(int numCandles) {
            int ratio = targetSeconds / base.seconds;
            return Math.min(MAX_COINBASE_CANDLES_PER_REQUEST, numCandles * ratio);
        }
    }

    /**
     * Maps unsupported requested granularities to an aggregation plan.
     *
     * <p>Keys that are NOT in this map AND not a direct Coinbase granularity will
     * return an error instead of silently substituting.
     */
    private static final Map<Integer, AggregationPlan> AGGREGATION_PLANS;
    static {
        Map<Integer, AggregationPlan> plans = new LinkedHashMap<>();
        plans.put(14400,   new AggregationPlan(CoinbaseGranularity.TWO_HOUR, 14400));  // 4h
        plans.put(604800,  new AggregationPlan(CoinbaseGranularity.ONE_DAY,   604800)); // 1w
        plans.put(2592000, new AggregationPlan(CoinbaseGranularity.ONE_DAY,  2592000)); // 1M (~30d)
        AGGREGATION_PLANS = Collections.unmodifiableMap(plans);
    }

    // ─── Constructor ─────────────────────────────────────────────────────────

    public CoinbaseCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        super(200, secondsPerCandle, tradePair, new SimpleIntegerProperty(-1));
    }

    // ─── CandleDataSupplier interface ─────────────────────────────────────────

    @Override
    public Set<Integer> getSupportedGranularities() {
        // Includes both direct AND aggregated timeframes this supplier can handle.
        Set<Integer> all = new TreeSet<>();
        for (CoinbaseGranularity g : CoinbaseGranularity.values()) all.add(g.seconds);
        all.addAll(AGGREGATION_PLANS.keySet());
        return all;
    }

    @Override
    public List<CandleData> getCandleData() { return Collections.emptyList(); }

    @Override
    public CandleDataSupplier getCandleDataSupplier(int secondsPerCandle, TradePair tradePair) {
        return new CoinbaseCandleDataSupplier(secondsPerCandle, tradePair);
    }

    @Override
    public CompletableFuture<Optional<?>> fetchCandleDataForInProgressCandle(
            @NotNull TradePair tradePair, Instant currentCandleStartedAt,
            long secondsIntoCurrentCandle, int secondsPerCandle) {
        return CompletableFuture.completedFuture(Optional.empty());
    }

    @Override
    public CompletableFuture<List<Trade>> fetchRecentTradesUntil(TradePair tradePair, Instant stopAt) {
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    // ─── Main fetch ──────────────────────────────────────────────────────────

    @Override
    public Future<List<CandleData>> get() {
        if (endTime.get() == -1) {
            endTime.set((int) (Instant.now().toEpochMilli() / 1000L));
        }

        // Check if Coinbase supports this granularity directly
        Optional<CoinbaseGranularity> directOpt = CoinbaseGranularity.exact(secondsPerCandle);
        if (directOpt.isPresent()) {
            return fetchDirect(directOpt.get());
        }

        // Check if we have an aggregation plan
        AggregationPlan plan = AGGREGATION_PLANS.get(secondsPerCandle);
        if (plan != null) {
            return fetchWithAggregation(plan);
        }

        // Truly unsupported – do not silently substitute
        logger.warn("Coinbase does not support {}s candles and no aggregation plan exists for {}. "
                        + "Returning empty result. Requested by {}.",
                secondsPerCandle, tradePair, tradePair);
        return CompletableFuture.completedFuture(Collections.emptyList());
    }

    // ─── Direct fetch (natively supported granularity) ────────────────────────

    private Future<List<CandleData>> fetchDirect(@NotNull CoinbaseGranularity granularity) {
        int requestCandles = Math.min(MAX_COINBASE_CANDLES_PER_REQUEST, Math.max(1, numCandles));
        int startTime      = Math.max(endTime.get() - (requestCandles * granularity.seconds), EARLIEST_DATA);

        if (startTime == EARLIEST_DATA) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String uriStr = buildUri(granularity.apiName, startTime, endTime.get(), requestCandles);
        logger.debug("Coinbase direct fetch: symbol={} granularity={} start={} end={} limit={}",
                tradePair, granularity.apiName, startTime, endTime.get(), requestCandles);

        return httpFetch(uriStr, granularity.seconds)
                .thenApply(candles -> {
                    endTime.set(startTime);
                    return candles;
                });
    }

    // ─── Aggregated fetch (unsupported granularity → smaller base + aggregate) ─

    private Future<List<CandleData>> fetchWithAggregation(@NotNull AggregationPlan plan) {
        int baseSeconds    = plan.base().seconds;
        int targetSeconds  = plan.targetSeconds();
        int requestCandles = plan.expandedRequestCount(numCandles);
        int startTime      = Math.max(endTime.get() - (requestCandles * baseSeconds), EARLIEST_DATA);

        if (startTime == EARLIEST_DATA) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        String uriStr = buildUri(plan.base().apiName, startTime, endTime.get(), requestCandles);

        logger.info("Coinbase candle aggregation: symbol={} requested={}s base={}({}) "
                        + "start={} end={} baseRequest={}",
                tradePair, targetSeconds,
                plan.base().apiName, baseSeconds,
                startTime, endTime.get(), requestCandles);

        return httpFetch(uriStr, baseSeconds)
                .thenApply(baseCandles -> {
                    if (baseCandles.isEmpty()) {
                        logger.debug("No base candles returned for {}/{} aggregation – returning empty",
                                tradePair, targetSeconds);
                        return Collections.<CandleData>emptyList();
                    }

                    List<CandleData> aggregated = CandleAggregator.aggregate(
                            baseCandles, baseSeconds, targetSeconds,
                            tradePair.toString('/'));

                    logger.info("Coinbase candle aggregation complete: symbol={} base={}s({} candles) "
                                    + "→ target={}s ({} candles)",
                            tradePair, baseSeconds, baseCandles.size(),
                            targetSeconds, aggregated.size());

                    endTime.set(startTime);
                    return aggregated;
                });
    }

    // ─── HTTP layer ──────────────────────────────────────────────────────────

    private CompletableFuture<List<CandleData>> httpFetch(String uriStr, int granularitySeconds) {
        return HttpClient.newHttpClient()
                .sendAsync(HttpRequest.newBuilder().uri(URI.create(uriStr)).GET().build(),
                        HttpResponse.BodyHandlers.ofString())
                .thenApply(response -> parseResponse(response, granularitySeconds));
    }

    @NotNull
    private List<CandleData> parseResponse(
            @NotNull HttpResponse<String> response, int granularitySeconds) {

        String body = response.body();

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            logger.warn("Coinbase candles request failed for {} ({}s): status={} body={}",
                    tradePair, granularitySeconds, response.statusCode(), abbreviate(body));
            return Collections.emptyList();
        }

        if (body == null || body.isBlank()) {
            logger.warn("Coinbase candles returned empty body for {} ({}s)", tradePair, granularitySeconds);
            return Collections.emptyList();
        }

        String trimmed = body.trim();
        if (!(trimmed.startsWith("{") || trimmed.startsWith("["))) {
            logger.warn("Coinbase candles returned non-JSON for {} ({}s): {}",
                    tradePair, granularitySeconds, abbreviate(trimmed));
            return Collections.emptyList();
        }

        JsonNode res;
        try {
            res = OBJECT_MAPPER.readTree(body);
        } catch (JsonProcessingException ex) {
            logger.warn("Unable to parse Coinbase candles for {} ({}s): {}",
                    tradePair, granularitySeconds, ex.getOriginalMessage());
            return Collections.emptyList();
        }

        if (res.has("error") || (res.has("message") && !res.has("candles"))) {
            String msg = res.has("error") ? res.path("error").asText()
                                          : res.path("message").asText();
            logger.warn("Coinbase candles API error for {} ({}s): {}", tradePair, granularitySeconds, msg);
            return Collections.emptyList();
        }

        JsonNode candleArray = res.has("candles") ? res.get("candles") : res;
        if (candleArray == null || !candleArray.isArray() || candleArray.isEmpty()) {
            logger.debug("Coinbase candles: empty payload for {} ({}s)", tradePair, granularitySeconds);
            return Collections.emptyList();
        }

        List<CandleData> result = new ArrayList<>(candleArray.size());
        for (JsonNode node : candleArray) {
            long start = parseLong(node.path("start").asText("0"));
            // Filter out partially-complete candle at boundary
            if (start + granularitySeconds > endTime.get()) {
                continue;
            }
            double open   = parseDouble(node.path("open").asText(null));
            double close  = parseDouble(node.path("close").asText(null));
            double high   = parseDouble(node.path("high").asText(null));
            double low    = parseDouble(node.path("low").asText(null));
            double volume = parseDouble(node.path("volume").asText(null));

            if (open <= 0 || close <= 0 || high <= 0 || low <= 0) {
                // Skip malformed candles (exchange sometimes returns zeroes)
                continue;
            }

            result.add(new CandleData(open, close, high, low, (int) start, volume));
        }

        result.sort(Comparator.comparingInt(CandleData::openTime));
        return result;
    }

    // ─── URI builder ─────────────────────────────────────────────────────────

    private String buildUri(String granularityApiName, int start, int end, int limit) {
        String product = encode(tradePair.toString('-').toUpperCase(Locale.ROOT));
        return ("%s?granularity=%s&start=%d&end=%d&limit=%d")
                .formatted(PUBLIC_PRODUCT_CANDLES_URL.formatted(product),
                        encode(granularityApiName), start, end, limit);
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private String encode(String value) {
        return URLEncoder.encode(String.valueOf(value), StandardCharsets.UTF_8);
    }

    private String abbreviate(String value) {
        if (value == null) return "";
        String t = value.trim().replaceAll("\\s+", " ");
        return t.length() <= 240 ? t : t.substring(0, 240) + "...";
    }

    private double parseDouble(String text) {
        if (text == null || text.isBlank()) return 0.0;
        try { return Double.parseDouble(text); } catch (Exception e) { return 0.0; }
    }

    private long parseLong(String text) {
        if (text == null || text.isBlank()) return 0L;
        try { return Long.parseLong(text); } catch (Exception e) { return 0L; }
    }
}

