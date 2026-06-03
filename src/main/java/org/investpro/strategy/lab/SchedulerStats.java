package org.investpro.strategy.lab;

import java.time.Instant;

/**
 * Immutable point-in-time snapshot of the {@link BacktestScheduler}'s metrics.
 *
 * <p>Designed to be published to the JavaFX UI thread via a throttled updater
 * so the control panel can render scheduler health without any shared-state risk.
 *
 * @param queued           Number of jobs waiting in the priority queue.
 * @param running          Number of jobs currently executing on worker threads.
 * @param completed        Total jobs completed successfully since scheduler start.
 * @param failed           Total jobs that failed since scheduler start.
 * @param cancelled        Total jobs cancelled since scheduler start.
 * @param activeWorkers    Number of active worker threads (≤ maxWorkers).
 * @param maxWorkers       Configured maximum concurrent workers.
 * @param avgExecTimeMs    Rolling average execution time in milliseconds.
 * @param throughputPerSec Jobs completed per second (rolling window throughput).
 * @param heapUsedBytes    JVM heap used at snapshot time (bytes).
 * @param heapMaxBytes     JVM heap max at snapshot time (bytes).
 * @param systemCpuLoad    System CPU load at snapshot time (0.0–1.0), or -1 if unavailable.
 * @param capturedAt       When this snapshot was captured.
 */
public record SchedulerStats(int queued, int running, long completed, long failed, long cancelled, int activeWorkers,
                             int maxWorkers, double avgExecTimeMs, double throughputPerSec, long heapUsedBytes,
                             long heapMaxBytes, double systemCpuLoad, Instant capturedAt) {

    public static SchedulerStats empty(int maxWorkers) {
        Runtime rt = Runtime.getRuntime();
        return new SchedulerStats(0, 0, 0L, 0L, 0L, 0, maxWorkers,
                0.0, 0.0, rt.totalMemory() - rt.freeMemory(), rt.maxMemory(), -1.0, Instant.now());
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    /**
     * Worker utilisation as a fraction (0.0–1.0).
     */
    public double getWorkerUtilization() {
        return maxWorkers > 0 ? (double) activeWorkers / maxWorkers : 0.0;
    }

    /**
     * Heap utilisation as a fraction (0.0–1.0).
     */
    public double getHeapUtilization() {
        return heapMaxBytes > 0 ? (double) heapUsedBytes / heapMaxBytes : 0.0;
    }

    /**
     * Heap used in mebibytes.
     */
    public double getHeapUsedMiB() {
        return heapUsedBytes / (1024.0 * 1024.0);
    }

    /**
     * Heap max in mebibytes.
     */
    public double getHeapMaxMiB() {
        return heapMaxBytes / (1024.0 * 1024.0);
    }

    /**
     * {@code true} if heap utilization exceeds 85 %.
     */
    public boolean isMemoryCritical() {
        return getHeapUtilization() > 0.85;
    }

    /**
     * {@code true} if CPU load exceeds 80 %.
     */
    public boolean isCpuHigh() {
        return systemCpuLoad > 0.80;
    }

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
