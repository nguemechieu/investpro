package org.investpro.strategy;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * StrategyBacktestResult encapsulates the results of a strategy backtest
 * on a specific symbol/timeframe pair.
 *
 * Used for strategy ranking and performance comparison.
 */
@Value
@Builder
public class StrategyBacktestResult {
     String backtestId;
     String strategyId;
    String symbol;
     String timeframe;
     double profitLoss;
     double profitLossPercent;
    int totalTrades;
    int winningTrades;
    int losingTrades;
    double winRate;
     double sharpeRatio;
     double maxDrawdown;
 double profitFactor;
    Instant backtestTime;
     String period; // e.g., "2024-01-01 to 2024-03-31"
     String notes;
}
