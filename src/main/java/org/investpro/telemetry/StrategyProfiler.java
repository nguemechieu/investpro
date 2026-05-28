package org.investpro.telemetry;

import org.investpro.strategy.provider.StrategyComplexity;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Thread-safe strategy profiler for Strategy Lab and live decisioning.
 */
public final class StrategyProfiler {
    private static final StrategyProfiler INSTANCE = new StrategyProfiler();

    private final Map<String, MutableProfile> profiles = new ConcurrentHashMap<>();

    private StrategyProfiler() {
    }

    public static StrategyProfiler getInstance() {
        return INSTANCE;
    }

    public void record(String strategyId, long candles, long elapsedNanos, StrategyComplexity complexity) {
        if (strategyId == null || strategyId.isBlank()) {
            return;
        }
        profiles.computeIfAbsent(strategyId, MutableProfile::new)
                .record(candles, elapsedNanos, complexity == null ? StrategyComplexity.MEDIUM : complexity);
    }

    public StrategyProfileSnapshot snapshot(String strategyId) {
        MutableProfile profile = profiles.get(strategyId);
        return profile == null
                ? new StrategyProfileSnapshot(strategyId, 0, 0, 0.0, 0.0, 0, 0,
                StrategyComplexity.MEDIUM, Instant.now())
                : profile.snapshot();
    }

    public List<StrategyProfileSnapshot> topByAverageLatency(int limit) {
        List<StrategyProfileSnapshot> snapshots = new ArrayList<>();
        for (MutableProfile profile : profiles.values()) {
            snapshots.add(profile.snapshot());
        }
        snapshots.sort(Comparator.comparingDouble(StrategyProfileSnapshot::averageExecutionMicros).reversed());
        if (limit > 0 && snapshots.size() > limit) {
            return List.copyOf(snapshots.subList(0, limit));
        }
        return List.copyOf(snapshots);
    }

    private static final class MutableProfile {
        private final String strategyId;
        private final LongAdder evaluations = new LongAdder();
        private final LongAdder candles = new LongAdder();
        private final LongAdder nanos = new LongAdder();
        private volatile long maxMicros;
        private volatile StrategyComplexity complexity = StrategyComplexity.MEDIUM;
        private final long startedNanos = System.nanoTime();

        private MutableProfile(String strategyId) {
            this.strategyId = strategyId;
        }

        private void record(long candleCount, long elapsedNanos, StrategyComplexity complexity) {
            evaluations.increment();
            candles.add(Math.max(0L, candleCount));
            nanos.add(Math.max(0L, elapsedNanos));
            this.complexity = complexity;
            long micros = elapsedNanos / 1_000L;
            if (micros > maxMicros) {
                maxMicros = micros;
            }
        }

        private StrategyProfileSnapshot snapshot() {
            long evals = evaluations.sum();
            long candleCount = candles.sum();
            long elapsed = Math.max(1L, System.nanoTime() - startedNanos);
            double cps = candleCount / (elapsed / 1_000_000_000.0);
            double avgMicros = evals == 0 ? 0.0 : (nanos.sum() / 1_000.0) / evals;
            return new StrategyProfileSnapshot(strategyId, evals, candleCount, cps, avgMicros,
                    maxMicros, 0L, complexity, Instant.now());
        }
    }
}
