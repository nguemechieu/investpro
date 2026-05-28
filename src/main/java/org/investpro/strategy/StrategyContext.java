package org.investpro.strategy;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import org.investpro.enums.MarketBehavior;
import org.investpro.enums.TradingSessionStatus;
import org.investpro.market.InstrumentTradingSession;
import org.investpro.models.trading.TradePair;
import org.investpro.enums.timeframe.Timeframe;

import java.time.Instant;
import java.util.List;

/**
 * Runtime context provided to strategies during signal generation.
 * Contains market data and analysis state.
 */
@Getter
@Builder
@Setter
public class StrategyContext {
    private  TradePair symbol;

    private  Timeframe timeframe;
    
    // Market data
    private  List<CandleData> candles; // Current candles, oldest to newest
    private  double currentPrice;
    private double bid;
    private double ask;
    
    // Market state
    private  MarketBehavior marketBehavior;
    private  double volatility; // Recent volatility measure
    private  double averageVolume;
    private  InstrumentTradingSession tradingSession;
    private  TradingSessionStatus tradingSessionStatus;
    
    // Timing
    @Builder.Default
    private final Instant timestamp = Instant.now();
    
    // Strategy state
    private  int barsAvailable;
    
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

    public CandleData getCurrentCandle() {
        return getLatestCandle();
    }
}
