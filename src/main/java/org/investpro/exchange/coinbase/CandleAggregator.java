package org.investpro.exchange.coinbase;

import org.investpro.data.CandleData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Aggregates a list of lower-resolution {@link CandleData} into a higher-resolution
 * requested timeframe using standard OHLCV rules:
 *
 * <ul>
 *   <li>open  = first candle open in the bucket</li>
 *   <li>high  = max high in the bucket</li>
 *   <li>low   = min low in the bucket</li>
 *   <li>close = last candle close in the bucket</li>
 *   <li>volume = sum of volumes in the bucket</li>
 *   <li>openTime = start timestamp of the bucket</li>
 * </ul>
 *
 * <p>Three aggregation modes are supported:
 * <ul>
 *   <li>{@link AggregationMode#FIXED_SECONDS} – group every N seconds (e.g. 2×2h → 4h)</li>
 *   <li>{@link AggregationMode#WEEKLY}         – group by ISO calendar week (Mon–Sun)</li>
 *   <li>{@link AggregationMode#MONTHLY}        – group by calendar month</li>
 * </ul>
 */
public final class CandleAggregator {

    private static final Logger log = LoggerFactory.getLogger(CandleAggregator.class);

    public enum AggregationMode { FIXED_SECONDS, WEEKLY, MONTHLY }

    private CandleAggregator() {}

    // ─── Public entry point ───────────────────────────────────────────────────

    /**
     * Aggregate {@code baseCandles} (already sorted ascending by openTime) into
     * the requested timeframe.
     *
     * @param baseCandles  raw candles from the Coinbase API (base granularity)
     * @param baseSeconds  granularity of base candles in seconds
     * @param targetSeconds requested (final) granularity in seconds
     * @param tradePair    symbol being fetched – used for logging only
     * @return aggregated candles in ascending order
     */
    @NotNull
    public static List<CandleData> aggregate(
            @NotNull List<CandleData> baseCandles,
            int baseSeconds,
            int targetSeconds,
            @NotNull String tradePair) {

        if (baseCandles.isEmpty()) {
            return List.of();
        }

        // Sort defensively – caller should already have sorted these
        List<CandleData> sorted = baseCandles.stream()
                .sorted(Comparator.comparingInt(CandleData::openTime))
                .toList();

        AggregationMode mode = modeFor(targetSeconds);
        List<CandleData> result;

        switch (mode) {
            case FIXED_SECONDS -> {
                int ratio = targetSeconds / baseSeconds;
                result = aggregateFixed(sorted, ratio, targetSeconds);
            }
            case WEEKLY  -> result = aggregateWeekly(sorted);
            case MONTHLY -> result = aggregateMonthly(sorted);
            default      -> result = List.copyOf(sorted); // should not happen
        }

        log.debug("Candle aggregation complete: symbol={} base={}s target={}s mode={} "
                  + "baseCandles={} aggregatedCandles={}",
                tradePair, baseSeconds, targetSeconds, mode,
                sorted.size(), result.size());

        return result;
    }

    // ─── Mode selection ───────────────────────────────────────────────────────

    private static AggregationMode modeFor(int targetSeconds) {
        if (targetSeconds == 604_800) return AggregationMode.WEEKLY;
        if (targetSeconds == 2_592_000 || targetSeconds == 2_678_400) return AggregationMode.MONTHLY;
        return AggregationMode.FIXED_SECONDS;
    }

    // ─── Fixed-size aggregation ───────────────────────────────────────────────

    /**
     * Groups exactly {@code ratio} consecutive base candles into one target candle.
     * Incomplete tail groups (fewer than {@code ratio} candles) are silently dropped
     * since they represent an in-progress candle.
     */
    @NotNull
    private static List<CandleData> aggregateFixed(
            @NotNull List<CandleData> sorted, int ratio, int targetSeconds) {

        if (ratio <= 1) {
            return List.copyOf(sorted);
        }

        List<CandleData> result = new ArrayList<>(sorted.size() / ratio + 1);
        int i = 0;
        while (i + ratio <= sorted.size()) {
            List<CandleData> bucket = sorted.subList(i, i + ratio);
            result.add(mergeBucket(bucket, targetSeconds));
            i += ratio;
        }
        return result;
    }

    // ─── Weekly aggregation ───────────────────────────────────────────────────

    /**
     * Groups daily candles into ISO calendar weeks (Monday → Sunday).
     */
    @NotNull
    private static List<CandleData> aggregateWeekly(@NotNull List<CandleData> sorted) {
        return aggregateByKey(sorted, CandleAggregator::weekKey, 604_800);
    }

    private static long weekKey(CandleData c) {
        ZonedDateTime dt = Instant.ofEpochSecond(c.openTime()).atZone(ZoneOffset.UTC);
        // ISO week starts Monday; compute Monday midnight of this week
        int dow = dt.getDayOfWeek().getValue(); // Mon=1 … Sun=7
        return dt.toLocalDate().minusDays(dow - 1L)
                 .atStartOfDay(ZoneOffset.UTC)
                 .toEpochSecond();
    }

    // ─── Monthly aggregation ─────────────────────────────────────────────────

    /**
     * Groups daily candles into calendar months.
     */
    @NotNull
    private static List<CandleData> aggregateMonthly(@NotNull List<CandleData> sorted) {
        // Use a nominal 30-day month for the stored secondsPerCandle
        return aggregateByKey(sorted, CandleAggregator::monthKey, 2_592_000);
    }

    private static long monthKey(CandleData c) {
        ZonedDateTime dt = Instant.ofEpochSecond(c.openTime()).atZone(ZoneOffset.UTC);
        return dt.toLocalDate().withDayOfMonth(1)
                 .atStartOfDay(ZoneOffset.UTC)
                 .toEpochSecond();
    }

    // ─── Generic key-based grouping ───────────────────────────────────────────

    @FunctionalInterface
    private interface BucketKeyFn { long key(CandleData c); }

    @NotNull
    private static List<CandleData> aggregateByKey(
            @NotNull List<CandleData> sorted,
            @NotNull BucketKeyFn keyFn,
            int targetSeconds) {

        List<CandleData> result   = new ArrayList<>();
        List<CandleData> bucket   = new ArrayList<>();
        long             bucketKey = Long.MIN_VALUE;

        for (CandleData candle : sorted) {
            long k = keyFn.key(candle);
            if (k != bucketKey) {
                if (!bucket.isEmpty()) {
                    result.add(mergeBucket(bucket, targetSeconds));
                    bucket.clear();
                }
                bucketKey = k;
            }
            bucket.add(candle);
        }
        if (!bucket.isEmpty()) {
            result.add(mergeBucket(bucket, targetSeconds));
        }
        return result;
    }

    // ─── OHLCV merge ─────────────────────────────────────────────────────────

    @NotNull
    private static CandleData mergeBucket(@NotNull List<CandleData> bucket, int targetSeconds) {
        double open   = bucket.getFirst().openPrice();
        double close  = bucket.getLast().closePrice();
        double high   = bucket.stream().mapToDouble(CandleData::highPrice).max().orElse(open);
        double low    = bucket.stream().mapToDouble(CandleData::lowPrice).min().orElse(open);
        double volume = bucket.stream().mapToDouble(CandleData::volume).sum();
        int    ts     = bucket.getFirst().openTime();

        return new CandleData(open, close, high, low, ts, volume);
    }

    // ─── Validation helpers (for tests) ──────────────────────────────────────

    /**
     * Validates that 2 consecutive 2-hour candles produce exactly 1 four-hour candle.
     * @return true if the aggregation is correct
     */
    public static boolean validateFourHourAggregation() {
        CandleData h2a = new CandleData(100, 110, 115, 98,  0,    500);
        CandleData h2b = new CandleData(110, 105, 120, 104, 7200, 300);

        List<CandleData> result = aggregate(List.of(h2a, h2b), 7200, 14400, "TEST/USD");

        if (result.size() != 1) return false;
        CandleData h4 = result.getFirst();
        return h4.openPrice()  == 100
            && h4.closePrice() == 105
            && h4.highPrice()  == 120
            && h4.lowPrice()   == 98
            && h4.volume()     == 800
            && h4.openTime()   == 0;
    }

    /**
     * Validates that 7 consecutive 1-day candles produce exactly 1 weekly candle.
     * @return true if the aggregation is correct
     */
    public static boolean validateWeeklyAggregation() {
        // Monday 2024-01-01 00:00 UTC = 1704067200
        long mon = 1704067200L;
        List<CandleData> days = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            int ts = (int)(mon + (long)i * 86400);
            days.add(new CandleData(100 + i, 101 + i, 110 + i, 99 + i, ts, 100));
        }

        List<CandleData> result = aggregate(days, 86400, 604800, "TEST/USD");

        if (result.size() != 1) return false;
        CandleData week = result.getFirst();
        return week.openPrice()  == days.getFirst().openPrice()
            && week.closePrice() == days.getLast().closePrice()
            && week.highPrice()  == days.stream().mapToDouble(CandleData::highPrice).max().orElse(0)
            && week.volume()     == days.stream().mapToDouble(CandleData::volume).sum();
    }

    /**
     * Validates that daily candles within a calendar month produce exactly 1 monthly candle.
     * @return true if the aggregation is correct
     */
    public static boolean validateMonthlyAggregation() {
        // 2024-01-01 = 1704067200
        long jan1 = 1704067200L;
        List<CandleData> days = new ArrayList<>();
        for (int i = 0; i < 31; i++) {
            int ts = (int)(jan1 + (long)i * 86400);
            days.add(new CandleData(200 + i, 201 + i, 210 + i, 199 + i, ts, 50));
        }

        List<CandleData> result = aggregate(days, 86400, 2592000, "TEST/USD");

        if (result.size() != 1) return false;
        CandleData month = result.getFirst();
        return month.openPrice()  == days.getFirst().openPrice()
            && month.closePrice() == days.getLast().closePrice()
            && month.volume()     == days.stream().mapToDouble(CandleData::volume).sum();
    }
}
