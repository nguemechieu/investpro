package org.investpro.backtesting.simulation;

final class SimulationClock {
    private long startedNanos;
    private long startMemoryBytes;
    private long peakMemoryBytes;
    private long strategyNanos;
    private long indicatorNanos;
    private long executionNanos;
    private long candlesProcessed;

    void start() {
        startedNanos = System.nanoTime();
        startMemoryBytes = usedMemory();
        peakMemoryBytes = startMemoryBytes;
    }

    void candleProcessed() {
        candlesProcessed++;
        peakMemoryBytes = Math.max(peakMemoryBytes, usedMemory());
    }

    void addStrategyNanos(long nanos) {
        strategyNanos += Math.max(0L, nanos);
    }

    void addIndicatorNanos(long nanos) {
        indicatorNanos += Math.max(0L, nanos);
    }

    void addExecutionNanos(long nanos) {
        executionNanos += Math.max(0L, nanos);
    }

    SimulationMetrics finish() {
        long durationMillis = Math.max(1L, (System.nanoTime() - startedNanos) / 1_000_000L);
        long endMemory = usedMemory();
        double candlesPerSecond = candlesProcessed * 1000.0 / durationMillis;
        long gcEstimate = Math.max(0L, peakMemoryBytes - Math.min(startMemoryBytes, endMemory));
        return new SimulationMetrics(
                durationMillis,
                candlesProcessed,
                candlesPerSecond,
                strategyNanos,
                indicatorNanos,
                executionNanos,
                startMemoryBytes,
                endMemory,
                peakMemoryBytes,
                gcEstimate);
    }

    private static long usedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }
}
