package org.investpro.backtesting.simulation;

/**
 * Performance counters captured during one simulation run.
 */
public record SimulationMetrics(
        long durationMillis,
        long candlesProcessed,
        double candlesPerSecond,
        long strategyExecutionNanos,
        long indicatorCalculationNanos,
        long executionSimulationNanos,
        long startMemoryBytes,
        long endMemoryBytes,
        long peakMemoryBytes,
        long gcPressureEstimateBytes
) {
}
