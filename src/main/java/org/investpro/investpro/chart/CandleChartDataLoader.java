package org.investpro.investpro.chart;


import org.investpro.investpro.Exchange;
import org.investpro.investpro.model.Candle;

import java.time.Instant;
import java.util.List;

public class CandleChartDataLoader {

    private final Exchange exchange;

    public CandleChartDataLoader(Exchange exchange) {
        this.exchange = exchange;
    }

    public List<Candle> getCandleData(String symbol, Instant startTime, Instant endTime, String interval) {
        // Call the Exchange to get the candles
        return exchange.getHistoricalCandles(symbol, startTime, endTime, interval);
    }

    public List<Candle> fetchHistoricalData(String symbol, Instant from, Instant to, String interval) {
        return getCandleData(symbol, from, to, interval);
    }

    public List<Candle> loadMoreCandles(String symbol, Instant olderThan, String interval, int limit) {
        Instant from = olderThan.minusSeconds(calculateSecondsForInterval(interval) * limit);
        return fetchHistoricalData(symbol, from, olderThan, interval);
    }

    private long calculateSecondsForInterval(String interval) {
        return switch (interval) {
            case "1m" -> 60;
            case "5m" -> 300;
            case "15m" -> 900;
            case "1h" -> 3600;
            case "1d" -> 86400;
            default -> 60; // Default fallback to 1-minute
        };
    }

    public Candle fetchLatestCandle(String currentSymbol, String currentInterval) {

        return null;
    }
}
