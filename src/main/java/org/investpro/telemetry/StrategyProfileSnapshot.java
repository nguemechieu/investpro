package org.investpro.telemetry;

import org.investpro.strategy.provider.StrategyComplexity;

import java.time.Instant;

/**
 * Performance profile for one strategy over a rolling execution window.
 */
public record StrategyProfileSnapshot(
        String strategyId,
        long evaluations,
        long candlesProcessed,
        double candlesPerSecond,
        double averageExecutionMicros,
        long maxExecutionMicros,
        long estimatedAllocationBytes,
        StrategyComplexity complexity,
        Instant capturedAt) {
}
