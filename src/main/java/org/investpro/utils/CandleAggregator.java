package org.investpro.utils;

import org.investpro.data.CandleData;
import org.investpro.models.trading.Trade;
import org.investpro.models.trading.TradePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aggregates individual trades into candlestick data for various timeframes.
 * Supports standard trading intervals: 1m, 5m, 15m, 1h, 4h, 1d.
 *
 * @author NOEL NGUEMECHIEU
 */
public class CandleAggregator {
    private static final Logger logger = LoggerFactory.getLogger(CandleAggregator.class);

    // Standard trading timeframes in seconds
    public static final Map<String, Integer> TIMEFRAME_SECONDS = Collections.unmodifiableMap(new LinkedHashMap<String, Integer>() {{
        put("1m", 60);
        put("5m", 300);
        put("15m", 900);
        put("1h", 3600);
        put("4h", 14400);
        put("1d", 86400);
    }});

    /**
     * Aggregates trades into candlestick data for the specified timeframe.
     *
     * @param trades the list of trades to aggregate
     * @param timeframe the timeframe key (e.g., "1h", "1d")
     * @return a list of aggregated CandleData sorted by open time
     * @throws IllegalArgumentException if the timeframe is not supported
     */
    public static List<CandleData> aggregateCandles(List<Trade> trades, String timeframe) {
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyList();
        }

        Integer secondsPerCandle = TIMEFRAME_SECONDS.get(timeframe);
        if (secondsPerCandle == null) {
            throw new IllegalArgumentException("Unsupported timeframe: " + timeframe + 
                    ". Supported: " + String.join(", ", TIMEFRAME_SECONDS.keySet()));
        }

        return aggregateCandles(trades, secondsPerCandle);
    }

    /**
     * Aggregates trades into candlestick data for the specified time interval in seconds.
     *
     * @param trades the list of trades to aggregate
     * @param secondsPerCandle the duration of each candle in seconds
     * @return a list of aggregated CandleData sorted by open time
     */
    public static List<CandleData> aggregateCandles(List<Trade> trades, int secondsPerCandle) {
        if (trades == null || trades.isEmpty()) {
            return Collections.emptyList();
        }

        // Group trades by candle time bucket
        Map<Long, List<Trade>> candleBuckets = new TreeMap<>();
        
        for (Trade trade : trades) {
            long bucketTime = getBucketTime(trade.getTimestamp(), secondsPerCandle);
            candleBuckets.computeIfAbsent(bucketTime, k -> new ArrayList<>()).add(trade);
        }

        // Convert each bucket into a CandleData
        List<CandleData> candles = new ArrayList<>();
        for (Map.Entry<Long, List<Trade>> entry : candleBuckets.entrySet()) {
            long bucketTime = entry.getKey();
            List<Trade> bucketTrades = entry.getValue();
            
            CandleData candle = createCandleFromTrades(bucketTrades, (int) bucketTime);
            candles.add(candle);
        }

        return candles;
    }

    /**
     * Aggregates trades into candlestick data, automatically selecting the optimal timeframe
     * based on the number of trades and desired visible candles.
     *
     * @param trades the list of trades to aggregate
     * @param desiredVisibleCandles target number of visible candles (typically 40-100)
     * @return a map of timeframe -> aggregated CandleData list
     */
    public static Map<String, List<CandleData>> aggregateCandlesAllTimeframes(
            List<Trade> trades, int desiredVisibleCandles) {
        
        Map<String, List<CandleData>> result = new LinkedHashMap<>();
        
        for (String timeframe : TIMEFRAME_SECONDS.keySet()) {
            result.put(timeframe, aggregateCandles(trades, timeframe));
        }
        
        return result;
    }

    /**
     * Selects the optimal timeframe based on the number of available candles.
     * Tries to maintain 40-100 visible candles for optimal viewing.
     *
     * @param trades the available trades
     * @return the recommended timeframe
     */
    public static String selectOptimalTimeframe(List<Trade> trades) {
        if (trades == null || trades.isEmpty()) {
            return "1h";
        }

        // Calculate time span of trades
        long minTime = trades.stream()
                .map(t -> t.getTimestamp().getEpochSecond())
                .min(Long::compareTo)
                .orElse(0L);
        long maxTime = trades.stream()
                .map(t -> t.getTimestamp().getEpochSecond())
                .max(Long::compareTo)
                .orElse(0L);
        
        long timeSpanSeconds = Math.max(1, maxTime - minTime);
        
        // Find the timeframe that results in 40-100 candles
        String optimalTimeframe = "1h";
        int closestDiff = Integer.MAX_VALUE;
        
        for (String timeframe : TIMEFRAME_SECONDS.keySet()) {
            int secondsPerCandle = TIMEFRAME_SECONDS.get(timeframe);
            long numCandles = Math.max(1, timeSpanSeconds / secondsPerCandle);
            
            int targetCount = 60; // Aim for roughly 60 candles
            int diff = Math.abs((int)numCandles - targetCount);
            
            if (diff < closestDiff) {
                closestDiff = diff;
                optimalTimeframe = timeframe;
            }
        }
        
        return optimalTimeframe;
    }

    /**
     * Creates a single CandleData from a list of trades within the same time bucket.
     *
     * @param trades trades within the same candle period
     * @param openTime the opening time of the candle in epoch seconds
     * @return aggregated CandleData
     */
    private static CandleData createCandleFromTrades(List<Trade> trades, int openTime) {
        if (trades.isEmpty()) {
            return new CandleData(0, 0, 0, 0, openTime, 0);
        }

        // Sort by timestamp to get proper OHLC
        List<Trade> sortedTrades = trades.stream()
                .sorted(Comparator.comparing(Trade::getTimestamp))
                .collect(Collectors.toList());

        double open = sortedTrades.get(0).getPrice();
        double close = sortedTrades.get(sortedTrades.size() - 1).getPrice();
        
        double high = sortedTrades.stream()
                .mapToDouble(t -> t.getPrice())
                .max()
                .orElse(open);
        
        double low = sortedTrades.stream()
                .mapToDouble(t -> t.getPrice())
                .min()
                .orElse(open);

        double volume = sortedTrades.stream()
                .mapToDouble(t -> t.getAmount())
                .sum();

        double volumeWeightedPrice = sortedTrades.stream()
                .mapToDouble(t -> t.getPrice() * t.getAmount())
                .sum() / Math.max(1, volume);

        double averagePrice = (high + low + open + close) / 4.0;

        return new CandleData(open, close, high, low, openTime, volume, averagePrice, volumeWeightedPrice, false);
    }

    /**
     * Calculates the bucket time (start of the time period) for a given timestamp.
     *
     * @param timestamp the trade timestamp
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
