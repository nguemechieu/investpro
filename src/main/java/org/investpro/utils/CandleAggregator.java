package org.investpro.utils;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;

/**
 * Aggregates individual trades into candlestick data for various timeframes.
 * Supports standard trading intervals: 1m, 5m, 15m, 30m, 1h, 2h, 4h, 1d, 1w,
 * 1M.
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
public class CandleAggregator {
    // Standard trading timeframes in seconds
    public static final Map<String, Integer> TIMEFRAME_SECONDS = Collections
            .unmodifiableMap(new LinkedHashMap<>() {
                {
                    put("1m", 60);
                    put("5m", 300);
                    put("15m", 900);
                    put("30m", 1800);
                    put("1h", 3600);
                    put("2h", 7200);
                    put("4h", 14400);
                    put("1d", 86400);
                    put("1w", 604800);
                    put("1M", 2592000); // 30 days
                }
            });

    /**
     * Calculates the bucket time (start of the time period) for a given timestamp.
     *
     * @param timestamp        the trade timestamp
     * @param secondsPerCandle the candle duration in seconds
     * @return the bucket time in epoch seconds
     */
    private static long getBucketTime(Instant timestamp, int secondsPerCandle) {
        long epochSeconds = timestamp.getEpochSecond();
        return (epochSeconds / secondsPerCandle) * secondsPerCandle;
    }

    /**
     * Checks if a timeframe string is valid.
     *
     * @param timeframe the timeframe to validate
     * @return true if the timeframe is supported
     */
    public static boolean isValidTimeframe(String timeframe) {
        return TIMEFRAME_SECONDS.containsKey(timeframe);
    }

    /**
     * Gets all supported timeframes.
     *
     * @return a list of supported timeframe keys
     */
    public static List<String> getSupportedTimeframes() {
        return new ArrayList<>(TIMEFRAME_SECONDS.keySet());
    }
}
