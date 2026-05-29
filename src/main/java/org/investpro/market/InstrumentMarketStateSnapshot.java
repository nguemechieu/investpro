package org.investpro.market;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.ToString;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.models.trading.InstrumentMetadata;
import org.investpro.models.trading.MarketQuote;
import org.investpro.models.trading.OrderBook;
import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Immutable read-only snapshot of InstrumentMarketState at a point in time.
 * Used for thread-safe snapshot reads without locking.
 */
@Builder
@Data
@ToString
public class InstrumentMarketStateSnapshot {
    @NotNull
    private final TradePair tradePair;

    @Nullable
    private final InstrumentMetadata metadata;

    @Nullable
    private final MarketQuote quote;

    @Nullable
    private final OrderBook orderBook;

    @Nullable
    private final TradingSessionStatus tradingSessionStatus;

    private final int candleCount;

    @NotNull
    private final Instant updatedAt;

    /**
     * Get current price from quote.
     */
    public double currentPrice() {
        if (quote != null && quote.hasValidPrices()) {
            return quote.midPrice();
        }
        return 0.0;
    }

    /**
     * Get current spread from quote.
     */
    public double currentSpread() {
        if (quote != null) {
            return quote.spread();
        }
        return 0.0;
    }

    /**
     * Check if quote is available and fresh.
     */
    public boolean hasQuote() {
        return quote != null && quote.hasValidPrices();
    }

    /**
     * Check if order book is available.
     */
    public boolean hasOrderBook() {
        return orderBook != null && orderBook.getBids() != null && !orderBook.getBids().isEmpty();
    }
}
