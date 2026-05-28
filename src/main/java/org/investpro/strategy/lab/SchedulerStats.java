package org.investpro.strategy.lab;

import java.time.Instant;

/**
 * Immutable point-in-time snapshot of the {@link BacktestScheduler}'s metrics.
 *
 * <p>Designed to be published to the JavaFX UI thread via a throttled updater
 * so the control panel can render scheduler health without any shared-state risk.
 */
public final class SchedulerStats {

    /** Number of jobs waiting in the priority queue. */
    private final int queued;

    /** Number of jobs currently executing on worker threads. */
    private final int running;

    /** Total jobs completed successfully since scheduler start. */
    private final long completed;

    /** Total jobs that failed since scheduler start. */
    private final long failed;

    /** Total jobs cancelled since scheduler start. */
    private final long cancelled;

    /** Number of active worker threads (≤ maxWorkers). */
    private final int activeWorkers;

    /** Configured maximum concurrent workers. */
    private final int maxWorkers;

    /** Rolling average execution time in milliseconds. */
    private final double avgExecTimeMs;

    /** Jobs completed per second (rolling window throughput). */
    private final double throughputPerSec;

    /** JVM heap used at snapshot time (bytes). */
    private final long heapUsedBytes;

    /** JVM heap max at snapshot time (bytes). */
    private final long heapMaxBytes;

    /** System CPU load at snapshot time (0.0–1.0), or -1 if unavailable. */
    private final double systemCpuLoad;

    /** When this snapshot was captured. */
    private final Instant capturedAt;

    public SchedulerStats(
            int queued, int running, long completed, long failed, long cancelled,
            int activeWorkers, int maxWorkers,
            double avgExecTimeMs, double throughputPerSec,
            long heapUsedBytes, long heapMaxBytes, double systemCpuLoad,
            Instant capturedAt) {
        this.queued = queued;
        this.running = running;
        this.completed = completed;
        this.failed = failed;
        this.cancelled = cancelled;
        this.activeWorkers = activeWorkers;
        this.maxWorkers = maxWorkers;
        this.avgExecTimeMs = avgExecTimeMs;
        this.throughputPerSec = throughputPerSec;
        this.heapUsedBytes = heapUsedBytes;
        this.heapMaxBytes = heapMaxBytes;
        this.systemCpuLoad = systemCpuLoad;
        this.capturedAt = capturedAt;
    }

    public static SchedulerStats empty(int maxWorkers) {
        Runtime rt = Runtime.getRuntime();
        return new SchedulerStats(0, 0, 0L, 0L, 0L, 0, maxWorkers,
                0.0, 0.0, rt.totalMemory() - rt.freeMemory(), rt.maxMemory(), -1.0, Instant.now());
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    public int getQueued() { return queued; }
    public int getRunning() { return running; }
    public long getCompleted() { return completed; }
    public long getFailed() { return failed; }
    public long getCancelled() { return cancelled; }
    public int getActiveWorkers() { return activeWorkers; }
    public int getMaxWorkers() { return maxWorkers; }
    public double getAvgExecTimeMs() { return avgExecTimeMs; }
    public double getThroughputPerSec() { return throughputPerSec; }
    public long getHeapUsedBytes() { return heapUsedBytes; }
    public long getHeapMaxBytes() { return heapMaxBytes; }
    public double getSystemCpuLoad() { return systemCpuLoad; }
    public Instant getCapturedAt() { return capturedAt; }

    /** Worker utilisation as a fraction (0.0–1.0). */
    public double getWorkerUtilization() {
        return maxWorkers > 0 ? (double) activeWorkers / maxWorkers : 0.0;
    }

    /** Heap utilisation as a fraction (0.0–1.0). */
    public double getHeapUtilization() {
        return heapMaxBytes > 0 ? (double) heapUsedBytes / heapMaxBytes : 0.0;
    }

    /** Heap used in mebibytes. */
    public double getHeapUsedMiB() { return heapUsedBytes / (1024.0 * 1024.0); }

    /** Heap max in mebibytes. */
    public double getHeapMaxMiB() { return heapMaxBytes / (1024.0 * 1024.0); }

    /** {@code true} if heap utilization exceeds 85 %. */
    public boolean isMemoryCritical() { return getHeapUtilization() > 0.85; }

    /** {@code true} if CPU load exceeds 80 %. */
    public boolean isCpuHigh() { return systemCpuLoad > 0.80; }

    @Override
    public String toString() {
        return String.format(
                "SchedulerStats{queued=%d, running=%d, completed=%d, failed=%d, cancelled=%d, "
                + "workers=%d/%d, avgMs=%.0f, rps=%.2f, heap=%.0f/%.0fMiB, cpu=%.0f%%}",
                queued, running, completed, failed, cancelled,
                activeWorkers, maxWorkers, avgExecTimeMs, throughputPerSec,
                getHeapUsedMiB(), getHeapMaxMiB(),
                systemCpuLoad >= 0 ? systemCpuLoad * 100 : -1.0);
    }
}
