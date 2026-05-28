package org.investpro.backtesting.simulation;

import java.time.Instant;

/**
 * Lightweight snapshot for future resume/recovery and operations-board display.
 */
public record BacktestSessionSnapshot(
        String sessionId,
        String strategyName,
        int candlesProcessed,
        int totalCandles,
        double progress,
        double cash,
        double equity,
        int completedTrades,
        String status,
        SimulationMetrics metrics,
        Instant updatedAt
) {
}
