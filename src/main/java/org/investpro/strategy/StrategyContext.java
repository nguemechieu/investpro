package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import org.investpro.data.CandleData;
import org.investpro.trading.MarketBehavior;
import org.investpro.models.trading.TradePair;
import org.investpro.timeframe.Timeframe;

import java.time.Instant;
import java.util.List;

/**
 * Runtime context provided to strategies during signal generation.
 * Contains market data and analysis state.
 */
@Getter
@Builder
public class StrategyContext {
    private final TradePair symbol;

    private final Timeframe timeframe;
    
    // Market data
    private final List<CandleData> candles; // Current candles, oldest to newest
    private final double currentPrice;
    private final double bid;
    private final double ask;
    
    // Market state
    private final MarketBehavior marketBehavior;
    private final double volatility; // Recent volatility measure
    private final double averageVolume;
    
    // Timing
    @Builder.Default
    private final Instant timestamp = Instant.now();
    
    // Strategy state
    private final int barsAvailable;
    
    public CandleData getLatestCandle() {
        if (candles == null || candles.isEmpty()) {
            return null;
        }
        return candles.getLast();
    }

    public CandleData getPreviousCandle(int barsBack) {
        int index = candles.size() - 1 - barsBack;
        if (index < 0 || index >= candles.size()) {
            return null;
        }
        return candles.get(index);
    }

    public boolean hasEnoughBars(int required) {
        return candles != null && candles.size() >= required;
    }

    public double getSpread() {
        return ask - bid;
    }

    public double getSpreadPercent() {
        return (ask - bid) / currentPrice * 100.0;
    }

    public boolean isHighVolatility() {
        return volatility > 0.02; // Adjust threshold as needed
    }

    public boolean isLowVolatility() {
        return volatility < 0.005;
    }

    public boolean isHighVolume() {
        return averageVolume > 1000000; // Adjust threshold
    }
}
