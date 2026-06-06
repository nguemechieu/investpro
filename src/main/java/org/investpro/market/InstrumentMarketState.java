package org.investpro.market;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.InstrumentMetadata;
import org.investpro.models.trading.MarketQuote;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Complete market state for a single instrument at a point in time.
 * Thread-safe aggregation of quote, order book, candles, and session status.
 * Updated by MarketDataEngine from live market feeds.
 */
@Getter
@Setter
@ToString
@Slf4j
public class InstrumentMarketState implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    private final TradePair tradePair;

    @Nullable
    private InstrumentMetadata metadata;

    @Nullable
    private MarketQuote quote;

    @Nullable
    private OrderBook orderBook;

    /**
     * Candles indexed by Timeframe for multi-timeframe analysis.
     */
    @NotNull
    private final Map<Timeframe, List<CandleData>> candlesByTimeframe = new HashMap<>();

    @Nullable
    private TradingSessionStatus tradingSessionStatus;

    @NotNull
    private Instant updatedAt = Instant.now();

    /**
     * Thread-safe access to mutable state.
     */
    private final transient ReadWriteLock lock = new ReentrantReadWriteLock();

    public InstrumentMarketState(@NotNull TradePair tradePair) {
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair must not be null");
    }

    @Builder
    public InstrumentMarketState(
            @NotNull TradePair tradePair,
            @Nullable InstrumentMetadata metadata,
            @Nullable MarketQuote quote,
            @Nullable OrderBook orderBook,
            @Nullable Map<Timeframe, List<CandleData>> candlesByTimeframe,
            @Nullable TradingSessionStatus tradingSessionStatus,
            @NotNull Instant updatedAt) {
        this.tradePair = Objects.requireNonNull(tradePair, "tradePair must not be null");
        this.metadata = metadata;
        this.quote = quote;
        this.orderBook = orderBook;
        if (candlesByTimeframe != null) {
            this.candlesByTimeframe.putAll(candlesByTimeframe);
        }
        this.tradingSessionStatus = tradingSessionStatus;
        this.updatedAt = updatedAt;
    }

    /**
     * Update market quote and refresh timestamp.
     */
    public void updateQuote(@Nullable MarketQuote newQuote) {
        lock.writeLock().lock();
        try {
            this.quote = newQuote;
            this.updatedAt = Instant.now();
            if (newQuote != null) {
                log.debug("Updated quote for {}: bid={}, ask={}", tradePair, newQuote.getBid(), newQuote.getAsk());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update order book and refresh timestamp.
     */
    public void updateOrderBook(@Nullable OrderBook newOrderBook) {
        lock.writeLock().lock();
        try {
            this.orderBook = newOrderBook;
            this.updatedAt = Instant.now();
            if (newOrderBook != null) {
                log.debug("Updated order book for {}: bids={}, asks={}",
                        tradePair, newOrderBook.getBids().size(), newOrderBook.getAsks().size());
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Update candles for a specific timeframe and refresh timestamp.
     */
    public void updateCandles(@NotNull Timeframe timeframe, @NotNull List<CandleData> candles) {
        Objects.requireNonNull(timeframe, "timeframe must not be null");
        Objects.requireNonNull(candles, "candles must not be null");

        lock.writeLock().lock();
        try {
            candlesByTimeframe.put(timeframe, new ArrayList<>(candles));
            this.updatedAt = Instant.now();
            log.debug("Updated {} candles for {}: {} bars loaded",
                    timeframe, tradePair, candles.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get candles for a specific timeframe (read-safe).
     */
    @NotNull
    public List<CandleData> getCandles(@NotNull Timeframe timeframe) {
        Objects.requireNonNull(timeframe, "timeframe must not be null");

        lock.readLock().lock();
        try {
            return new ArrayList<>(candlesByTimeframe.getOrDefault(timeframe, List.of()));
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if enough candles are loaded for a timeframe.
     */
    public boolean hasEnoughCandles(@NotNull Timeframe timeframe, int required) {
        Objects.requireNonNull(timeframe, "timeframe must not be null");

        lock.readLock().lock();
        try {
            List<CandleData> candles = candlesByTimeframe.get(timeframe);
            return candles != null && candles.size() >= required;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get current trading session status.
     */
    @NotNull
    public TradingSessionStatus getSessionStatus() {
        lock.readLock().lock();
        try {
            return tradingSessionStatus != null ? tradingSessionStatus : TradingSessionStatus.UNKNOWN;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Update trading session status.
     */
    public void updateSessionStatus(@Nullable TradingSessionStatus status) {
        lock.writeLock().lock();
        try {
            this.tradingSessionStatus = status;
            this.updatedAt = Instant.now();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Check if instrument is tradable now based on session status and liquidity.
     */
    public boolean isTradableNow() {
        lock.readLock().lock();
        try {
            // If no session info, assume tradable
            if (metadata == null || metadata.getTradingSession() == null) {
                return true;
            }

            ZonedDateTime now = ZonedDateTime.now();
            return metadata.getTradingSession().isTradableNow(now);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Check if market data is fresh (not older than maxAge).
     */
    public boolean isDataFresh(@NotNull Duration maxAge) {
        Objects.requireNonNull(maxAge, "maxAge must not be null");

        lock.readLock().lock();
        try {
            if (quote == null) {
                return false;
            }
            return quote.isFresh(maxAge);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get current mid-price from quote.
     */
    public double currentPrice() {
        lock.readLock().lock();
        try {
            if (quote != null && quote.hasValidPrices()) {
                return quote.midPrice();
            }
            return 0.0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Get current spread from quote.
     */
    public double currentSpread() {
        lock.readLock().lock();
        try {
            if (quote != null) {
                return quote.spread();
            }
            return 0.0;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clear all candles for housekeeping.
     */
    public void clearCandles() {
        lock.writeLock().lock();
        try {
            candlesByTimeframe.clear();
            this.updatedAt = Instant.now();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Clear candles for a specific timeframe.
     */
    public void clearCandles(@NotNull Timeframe timeframe) {
        Objects.requireNonNull(timeframe, "timeframe must not be null");

        lock.writeLock().lock();
        try {
            candlesByTimeframe.remove(timeframe);
            this.updatedAt = Instant.now();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Get a snapshot of current state (for thread-safe reading).
     */
    public InstrumentMarketStateSnapshot snapshot() {
        lock.readLock().lock();
        try {
            return InstrumentMarketStateSnapshot.builder()
                    .tradePair(tradePair)
                    .metadata(metadata)
                    .quote(quote)
                    .orderBook(orderBook)
                    .tradingSessionStatus(tradingSessionStatus)
                    .candleCount(candlesByTimeframe.values().stream().mapToInt(List::size).sum())
                    .updatedAt(updatedAt)
                    .build();
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        InstrumentMarketState that = (InstrumentMarketState) o;
        return Objects.equals(tradePair, that.tradePair);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradePair);
    }
}
