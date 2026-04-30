package org.investpro.ui.charts;

import javafx.util.Pair;
import org.investpro.data.CandleData;
import org.investpro.ui.tools.InstantAxisFormatter;
import org.investpro.utils.Extrema;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Utility methods used by InvestPro candlestick charts.
 *
 * Responsibilities:
 * - calculate sliding-window extrema for visible candle windows
 * - calculate trailing extrema for the remaining right-side chart area
 * - choose readable x-axis date/time label formatters
 */
public final class CandleStickChartUtils {

    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
    private static final int SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;
    private static final int SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY;
    private static final int SECONDS_PER_MONTH = 30 * SECONDS_PER_DAY;
    private static final int SECONDS_PER_YEAR = 365 * SECONDS_PER_DAY;

    private CandleStickChartUtils() {
        // Utility class
    }

    /**
     * Adds sliding-window extrema to the supplied map.
     *
     * Map key:
     * - candle open time at the start of the window
     *
     * Pair value:
     * - first: volume extrema
     * - second: candle price extrema using low/high
     *
     * Runtime:
     * - O(n), using monotonic deques
     */
    public static void putSlidingWindowExtrema(
            Map<Integer, Pair<Extrema<Double>, Extrema<Double>>> extrema,
            List<CandleData> candleData,
            int windowSize
    ) {
        Objects.requireNonNull(extrema, "extrema must not be null");
        Objects.requireNonNull(candleData, "candleData must not be null");


        if (candleData.isEmpty() || windowSize <= 0) {
            return;
        }

        //Sorting candles by open time
        candleData=sortedByOpenTime(candleData);
        int safeWindowSize = Math.min(windowSize, candleData.size());

        Deque<Integer> volumeMinDeque = new ArrayDeque<>(safeWindowSize);
        Deque<Integer> volumeMaxDeque = new ArrayDeque<>(safeWindowSize);
        Deque<Integer> priceMinDeque = new ArrayDeque<>(safeWindowSize);
        Deque<Integer> priceMaxDeque = new ArrayDeque<>(safeWindowSize);

        for (int i = 0; i < candleData.size(); i++) {


            pushMinIndex(volumeMinDeque, candleData, i, CandleStickChartUtils::volume);
            pushMaxIndex(volumeMaxDeque, candleData, i, CandleStickChartUtils::volume);
            pushMinIndex(priceMinDeque, candleData, i, CandleStickChartUtils::lowPrice);
            pushMaxIndex(priceMaxDeque, candleData, i, CandleStickChartUtils::highPrice);

            int expiredIndex = i - safeWindowSize;

            removeExpired(volumeMinDeque, expiredIndex);
            removeExpired(volumeMaxDeque, expiredIndex);
            removeExpired(priceMinDeque, expiredIndex);
            removeExpired(priceMaxDeque, expiredIndex);

            if (i >= safeWindowSize - 1) {
                int windowStartIndex = i - safeWindowSize + 1;
                putWindowExtrema(
                        extrema,
                        candleData,
                        windowStartIndex,
                        volumeMinDeque,
                        volumeMaxDeque,
                        priceMinDeque,
                        priceMaxDeque
                );
            }
        }
    }

    /**
     * Adds extrema for trailing/right-side visible windows.
     *
     * This is useful when scrolling near the newest candles and the chart window
     * contains fewer than the normal number of visible candles.
     */
    public static void putExtremaForRemainingElements(
            Map<Integer, Pair<Extrema<Double>, Extrema<Double>>> extrema,
            List<CandleData> candleData
    ) {
        Objects.requireNonNull(extrema, "extrema must not be null");
        Objects.requireNonNull(candleData, "candleData must not be null");

        if (candleData.isEmpty()) {
            return;
        }

        double minVolume = Double.POSITIVE_INFINITY;
        double maxVolume = Double.NEGATIVE_INFINITY;
        double minPrice = Double.POSITIVE_INFINITY;
        double maxPrice = Double.NEGATIVE_INFINITY;

        for (int i = candleData.size() - 1; i >= 0; i--) {
            CandleData candle = candleData.get(i);

            minVolume = Math.min(minVolume, volume(candle));
            maxVolume = Math.max(maxVolume, volume(candle));
            minPrice = Math.min(minPrice, lowPrice(candle));
            maxPrice = Math.max(maxPrice, highPrice(candle));

            extrema.put(
                    candle.getOpenTime(),
                    new Pair<>(
                            new Extrema<>(minVolume, maxVolume),
                            new Extrema<>(minPrice, maxPrice)
                    )
            );
        }
    }

    /**
     * Chooses an x-axis label formatter based on the visible time range.
     */
    public static InstantAxisFormatter getXAxisFormatterForRange(double rangeInSeconds) {
        double safeRange = Double.isFinite(rangeInSeconds)
                ? Math.max(0.0, rangeInSeconds)
                : 0.0;

        if (safeRange > SECONDS_PER_YEAR) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("MMM yyyy"));
        }

        if (safeRange > 6.0 * SECONDS_PER_MONTH) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("MMM yy"));
        }

        if (safeRange > 6.0 * SECONDS_PER_WEEK) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("dd MMM"));
        }

        if (safeRange > 10.0 * SECONDS_PER_DAY) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("dd MMM"));
        }

        if (safeRange > SECONDS_PER_DAY) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("dd MMM HH:mm"));
        }

        if (safeRange > SECONDS_PER_HOUR) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("HH:mm"));
        }

        return new InstantAxisFormatter(DateTimeFormatter.ofPattern("HH:mm:ss"));
    }

    /**
     * Safe helper when callers want sorted candle data before extrema calculation.
     */
    public static List<CandleData> sortedByOpenTime(List<CandleData> candleData) {
        if (candleData == null || candleData.isEmpty()) {
            return Collections.emptyList();
        }

        return candleData.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingInt(CandleData::getOpenTime))
                .toList();
    }

    private static void putWindowExtrema(
            Map<Integer, Pair<Extrema<Double>, Extrema<Double>>> extrema,
            List<CandleData> candleData,
            int windowStartIndex,
            @NotNull Deque<Integer> volumeMinDeque,
            Deque<Integer> volumeMaxDeque,
            Deque<Integer> priceMinDeque,
            Deque<Integer> priceMaxDeque
    ) {
        if (volumeMinDeque.isEmpty()
                || volumeMaxDeque.isEmpty()
                || priceMinDeque.isEmpty()
                || priceMaxDeque.isEmpty()) {
            return;
        }

        CandleData windowStartCandle = candleData.get(windowStartIndex);

        extrema.put(
                windowStartCandle.getOpenTime(),
                new Pair<>(
                        new Extrema<>(
                                volume(candleData.get(volumeMinDeque.peekFirst())),
                                volume(candleData.get(volumeMaxDeque.peekFirst()))
                        ),
                        new Extrema<>(
                                lowPrice(candleData.get(priceMinDeque.peekFirst())),
                                highPrice(candleData.get(priceMaxDeque.peekFirst()))
                        )
                )
        );
    }

    private static void removeExpired(@NotNull Deque<Integer> deque, int expiredIndex) {
        while (!deque.isEmpty() && deque.peekFirst() <= expiredIndex) {
            deque.pollFirst();
        }
    }

    private static void pushMinIndex(
            @NotNull Deque<Integer> deque,
            @NotNull List<CandleData> data,
            int index,
            @NotNull CandleValueExtractor extractor
    ) {
        double value = extractor.value(data.get(index));

        while (!deque.isEmpty() && value <= extractor.value(data.get(deque.peekLast()))) {
            deque.pollLast();
        }

        deque.addLast(index);
    }

    private static void pushMaxIndex(
            @NotNull Deque<Integer> deque,
            @NotNull List<CandleData> data,
            int index,
            @NotNull CandleValueExtractor extractor
    ) {
        double value = extractor.value(data.get(index));

        while (!deque.isEmpty() && value >= extractor.value(data.get(deque.peekLast()))) {
            deque.pollLast();
        }

        deque.addLast(index);
    }

    private static double volume(CandleData candle) {
        if (candle == null || !Double.isFinite(candle.getVolume())) {
            return 0.0;
        }

        return candle.getVolume();
    }

    private static double lowPrice(CandleData candle) {
        if (candle == null || !Double.isFinite(candle.getLowPrice())) {
            return 0.0;
        }

        return candle.getLowPrice();
    }

    private static double highPrice(CandleData candle) {
        if (candle == null || !Double.isFinite(candle.getHighPrice())) {
            return 0.0;
        }

        return candle.getHighPrice();
    }

    @FunctionalInterface
    private interface CandleValueExtractor {
        double value(CandleData candle);
    }
}