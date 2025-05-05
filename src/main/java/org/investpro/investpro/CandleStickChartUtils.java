package org.investpro.investpro;

import javafx.util.Pair;
import org.investpro.investpro.model.CandleData;
import org.jetbrains.annotations.NotNull;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.ErrorManager;


/**
 * @author Noel Nguemechieu
 */
public final class CandleStickChartUtils {
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
    private static final int SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;
    private static final int SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY;
    private static final int SECONDS_PER_MONTH = 30 * SECONDS_PER_DAY; // Corrected to avoid overflow
    private static final int SECONDS_PER_YEAR = 12 * SECONDS_PER_MONTH;
    static ErrorManager logger = new ErrorManager();

    private CandleStickChartUtils() {
    }

    /**
     * Adds the sliding-window extrema (maps candle x-values to a pair of extrema for volume and high-low candle price)
     * to the given {@code extrema} map.
     */

    public static void putSlidingWindowExtrema(Map<Integer, Pair<Extrema, Extrema>> extrema,
                                               List<CandleData> candleData, int windowSize) {
        Objects.requireNonNull(extrema, "extrema map must not be null");
        Objects.requireNonNull(candleData, "candleData must not be null");

        if (candleData.isEmpty()) {
            throw new RuntimeException("candleData must not be empty");
        }
        if (windowSize > candleData.size()) {
            logger.error("windowSize ({}) must be less than size of candleData ({})" + candleData.size(), new Exception(), 500);
            return;
        }

        final Deque<Integer> candleMinWindow = new ArrayDeque<>(windowSize);
        final Deque<Integer> candleMaxWindow = new ArrayDeque<>(windowSize);
        final Deque<Integer> volumeMinWindow = new ArrayDeque<>(windowSize);
        final Deque<Integer> volumeMaxWindow = new ArrayDeque<>(windowSize);

        for (int i = 0; i < windowSize; i++) {
            updateDeques(candleData, volumeMinWindow, volumeMaxWindow, candleMinWindow, candleMaxWindow, i);
        }

        for (int i = windowSize; i < candleData.size(); i++) {
            // Store extrema for previous window
            if (!volumeMinWindow.isEmpty() && !volumeMaxWindow.isEmpty() && !candleMinWindow.isEmpty() && !candleMaxWindow.isEmpty()) {


                extrema.put(candleData.get(i - windowSize).getOpenTime(), new Pair<>(
                        new Extrema(candleData.get(volumeMinWindow.peekFirst()).getVolume(),
                                Math.ceil(candleData.get(volumeMaxWindow.peekFirst()).getVolume())),
                        new Extrema(candleData.get(candleMinWindow.peekFirst()).getLowPrice(),
                                Math.ceil(candleData.get(candleMaxWindow.peekFirst()).getHighPrice()))));
            }

            removeOutdatedElements(volumeMinWindow, i - windowSize);
            removeOutdatedElements(volumeMaxWindow, i - windowSize);
            removeOutdatedElements(candleMinWindow, i - windowSize);
            removeOutdatedElements(candleMaxWindow, i - windowSize);

            updateDeques(candleData, volumeMinWindow, volumeMaxWindow, candleMinWindow, candleMaxWindow, i);
        }

        // Store extrema for the last window
        if (!volumeMinWindow.isEmpty() && !volumeMaxWindow.isEmpty() && !candleMinWindow.isEmpty() && !candleMaxWindow.isEmpty()) {
            extrema.put(candleData.get(candleData.size() - windowSize).getOpenTime(), new Pair<>(
                    new Extrema(candleData.get(volumeMinWindow.peekFirst()).getVolume(),
                            Math.ceil(candleData.get(volumeMaxWindow.peekFirst()).getVolume())),
                    new Extrema(candleData.get(candleMinWindow.peekFirst()).getLowPrice(),
                            Math.ceil(candleData.get(candleMaxWindow.peekFirst()).getHighPrice()))));
        }
    }


    private static void updateDeques(List<CandleData> candleData, @NotNull Deque<Integer> volumeMinWindow,
                                     Deque<Integer> volumeMaxWindow, Deque<Integer> candleMinWindow,
                                     Deque<Integer> candleMaxWindow, int index) {
        while (!volumeMinWindow.isEmpty() && candleData.get(index).getVolume() <=
                candleData.get(volumeMinWindow.peekLast()).getVolume()) {
            volumeMinWindow.pollLast();
        }
        volumeMinWindow.addLast(index);

        while (!volumeMaxWindow.isEmpty() && candleData.get(index).getVolume() >=
                candleData.get(volumeMaxWindow.peekLast()).getVolume()) {
            volumeMaxWindow.pollLast();
        }
        volumeMaxWindow.addLast(index);

        while (!candleMinWindow.isEmpty() && candleData.get(index).getLowPrice() <=
                candleData.get(candleMinWindow.peekLast()).getLowPrice()) {
            candleMinWindow.pollLast();
        }
        candleMinWindow.addLast(index);

        while (!candleMaxWindow.isEmpty() && candleData.get(index).getHighPrice() >=
                candleData.get(candleMaxWindow.peekLast()).getHighPrice()) {
            candleMaxWindow.pollLast();
        }
        candleMaxWindow.addLast(index);
    }

    private static void removeOutdatedElements(@NotNull Deque<Integer> deque, int limit) {
        while (!deque.isEmpty() && deque.peekFirst() <= limit) {
            deque.pollFirst();
        }
    }

    public static @NotNull InstantAxisFormatter getXAxisFormatterForRange(final double rangeInSeconds) {
        if (rangeInSeconds > SECONDS_PER_YEAR) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("MMM yy"));
        } else if (rangeInSeconds > 6 * SECONDS_PER_MONTH) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("MMMM ''yy"));
        } else if (rangeInSeconds > 6 * SECONDS_PER_WEEK) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("'Week' w 'of' y"));
        } else if (rangeInSeconds > 10 * SECONDS_PER_DAY) {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("dd MMM"));
        } else {
            return new InstantAxisFormatter(DateTimeFormatter.ofPattern("HH:mm"));
        }
    }
}