package org.investpro.investpro;

import javafx.util.Pair;
import org.investpro.investpro.models.CandleData;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * @author Noel Nguemechieu
 */
public final class CandleStickChartUtils {
    private static final int SECONDS_PER_MINUTE = 60;
    private static final int SECONDS_PER_HOUR = 60 * SECONDS_PER_MINUTE;
    private static final int SECONDS_PER_DAY = 24 * SECONDS_PER_HOUR;
    private static final int SECONDS_PER_WEEK = 7 * SECONDS_PER_DAY;
    private static final int SECONDS_PER_MONTH = 30 * SECONDS_PER_DAY;
    private static final int SECONDS_PER_YEAR = 12 * SECONDS_PER_MONTH;
    private static final Logger logger = LoggerFactory.getLogger(CandleStickChartUtils.class);

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
        if (windowSize <= 0) {
            throw new IllegalArgumentException("windowSize must be positive");
        }

        int effectiveWindowSize = Math.min(windowSize, candleData.size());
        if (effectiveWindowSize != windowSize) {
            logger.debug(
                    "Clamping windowSize from {} to {} for candleData size {}",
                    windowSize,
                    effectiveWindowSize,
                    candleData.size()
            );
        }

        final Deque<Integer> candleMinWindow = new ArrayDeque<>(effectiveWindowSize);
        final Deque<Integer> candleMaxWindow = new ArrayDeque<>(effectiveWindowSize);
        final Deque<Integer> volumeMinWindow = new ArrayDeque<>(effectiveWindowSize);
        final Deque<Integer> volumeMaxWindow = new ArrayDeque<>(effectiveWindowSize);

        for (int i = 0; i < effectiveWindowSize; i++) {
            updateDeques(candleData, volumeMinWindow, volumeMaxWindow, candleMinWindow, candleMaxWindow, i);
        }

        for (int i = effectiveWindowSize; i < candleData.size(); i++) {
            if (!volumeMinWindow.isEmpty() && !volumeMaxWindow.isEmpty()
                    && !candleMinWindow.isEmpty() && !candleMaxWindow.isEmpty()) {
                extrema.put(candleData.get(i - effectiveWindowSize).getOpenTime(), new Pair<>(
                        new Extrema(
                                candleData.get(volumeMinWindow.peekFirst()).getVolume(),
                                Math.ceil(candleData.get(volumeMaxWindow.peekFirst()).getVolume())
                        ),
                        new Extrema(
                                candleData.get(candleMinWindow.peekFirst()).getLowPrice(),
                                Math.ceil(candleData.get(candleMaxWindow.peekFirst()).getHighPrice())
                        ))); 
            }

            removeOutdatedElements(volumeMinWindow, i - effectiveWindowSize);
            removeOutdatedElements(volumeMaxWindow, i - effectiveWindowSize);
            removeOutdatedElements(candleMinWindow, i - effectiveWindowSize);
            removeOutdatedElements(candleMaxWindow, i - effectiveWindowSize);

            updateDeques(candleData, volumeMinWindow, volumeMaxWindow, candleMinWindow, candleMaxWindow, i);
        }

        if (!volumeMinWindow.isEmpty() && !volumeMaxWindow.isEmpty()
                && !candleMinWindow.isEmpty() && !candleMaxWindow.isEmpty()) {
            extrema.put(candleData.getLast().getOpenTime(), new Pair<>(
                    new Extrema(
                            candleData.get(volumeMinWindow.peekFirst()).getVolume(),
                            Math.ceil(candleData.get(volumeMaxWindow.peekFirst()).getVolume())
                    ),
                    new Extrema(
                            candleData.get(candleMinWindow.peekFirst()).getLowPrice(),
                            Math.ceil(candleData.get(candleMaxWindow.peekFirst()).getHighPrice())
                    )));
        }
    }

    private static void updateDeques(List<CandleData> candleData, @NotNull Deque<Integer> volumeMinWindow,
                                     Deque<Integer> volumeMaxWindow, Deque<Integer> candleMinWindow,
                                     Deque<Integer> candleMaxWindow, int index) {
        while (true) {
            Integer lastIndex = volumeMinWindow.peekLast();
            if (lastIndex == null || candleData.get(index).getVolume() >
                    candleData.get(lastIndex).getVolume()) {
                break;
            }
            volumeMinWindow.pollLast();
        }
        volumeMinWindow.addLast(index);

        while (true) {
            Integer lastIndex = volumeMaxWindow.peekLast();
            if (lastIndex == null || candleData.get(index).getVolume() <
                    candleData.get(lastIndex).getVolume()) {
                break;
            }
            volumeMaxWindow.pollLast();
        }
        volumeMaxWindow.addLast(index);

        while (true) {
            Integer lastIndex = candleMinWindow.peekLast();
            if (lastIndex == null || candleData.get(index).getLowPrice() >
                    candleData.get(lastIndex).getLowPrice()) {
                break;
            }
            candleMinWindow.pollLast();
        }
        candleMinWindow.addLast(index);

        while (true) {
            Integer lastIndex = candleMaxWindow.peekLast();
            if (lastIndex == null || candleData.get(index).getHighPrice() <
                    candleData.get(lastIndex).getHighPrice()) {
                break;
            }
            candleMaxWindow.pollLast();
        }
        candleMaxWindow.addLast(index);
    }

    private static void removeOutdatedElements(@NotNull Deque<Integer> deque, int limit) {
        while (true) {
            Integer firstIndex = deque.peekFirst();
            if (firstIndex == null || firstIndex > limit) {
                break;
            }
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
