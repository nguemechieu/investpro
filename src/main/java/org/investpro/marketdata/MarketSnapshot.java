package org.investpro.marketdata;

import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.Ticker;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MarketSnapshot(
        String exchangeId,
        String symbol,
        String timeframe,
        MarketTick tick,
        Ticker ticker,
        List<Candle> recentCandles,
        OrderBook orderBook,
        MarketDataConnectionStatus connectionStatus,
        Instant lastUpdate,
        Duration staleAfter,
        Map<String, Object> metadata) {

    public MarketSnapshot {
        exchangeId = safe(exchangeId);
        symbol = safe(symbol);
        timeframe = safe(timeframe);
        recentCandles = recentCandles == null ? List.of() : List.copyOf(recentCandles);
        connectionStatus = connectionStatus == null ? MarketDataConnectionStatus.UNKNOWN : connectionStatus;
        lastUpdate = lastUpdate == null ? Instant.EPOCH : lastUpdate;
        staleAfter = staleAfter == null ? Duration.ofSeconds(30) : staleAfter;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Candle latestCandle() {
        return recentCandles.isEmpty() ? null : recentCandles.getLast();
    }

    public boolean stale() {
        return lastUpdate.plus(staleAfter).isBefore(Instant.now());
    }

    public static MarketSnapshot empty(String exchangeId, String symbol, String timeframe) {
        return new MarketSnapshot(exchangeId, symbol, timeframe, null, null, List.of(), null,
                MarketDataConnectionStatus.UNKNOWN, Instant.EPOCH, Duration.ofSeconds(30), Map.of());
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
