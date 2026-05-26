package org.investpro.strategy.lab;

import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;

/**
 * Monitors JVM heap and system CPU to prevent resource exhaustion.
 *
 * <p>When memory or CPU conditions become critical, the scheduler can ask the
 * guard whether it should pause intake to avoid OOM or thermal throttling.
 *
 * <p>This class is stateless and thread-safe; all reads come from JMX beans
 * that are themselves thread-safe.
 */
@Slf4j
public final class ResourceGuard {

    /** Heap fraction at which we consider memory critical (default 85 %). */
    private final double memoryThreshold;

    /** System CPU fraction at which we consider CPU critical (default 80 %). */
    private final double cpuThreshold;

    private final MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    private final OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();

    /**
     * Creates a guard with default thresholds (85 % memory, 80 % CPU).
     */
    public ResourceGuard() {
        this(0.85, 0.80);
    }

    /**
     * Creates a guard with custom thresholds.
     *
     * @param memoryThreshold heap fraction at which to signal critical (0.0–1.0)
     * @param cpuThreshold    CPU fraction at which to signal critical (0.0–1.0)
     */
    public ResourceGuard(double memoryThreshold, double cpuThreshold) {
        this.memoryThreshold = memoryThreshold;
        this.cpuThreshold = cpuThreshold;
    }

    // ─── Memory ──────────────────────────────────────────────────────────────

    /**
     * Returns heap utilisation as a fraction (0.0–1.0).
     */
    public double getHeapUtilization() {
        long used = memoryBean.getHeapMemoryUsage().getUsed();
        long max  = memoryBean.getHeapMemoryUsage().getMax();
        return max > 0 ? (double) used / max : 0.0;
    }

    /**
     * Returns {@code true} when heap utilisation exceeds {@link #memoryThreshold}.
     *
     * <p>When this is {@code true} the scheduler should pause accepting new jobs
     * until the GC has had a chance to recover memory.
     */
    public boolean isMemoryCritical() {
        return getHeapUtilization() > memoryThreshold;
    }

    /**
     * Suggests the JVM should collect garbage immediately.
     * This is a hint only; the GC is not obligated to comply.
     */
    public void suggestGc() {
        log.warn("ResourceGuard: memory critical ({:.1f}% heap used) – requesting GC",
                getHeapUtilization() * 100);
        System.gc();
    }

    // ─── CPU ─────────────────────────────────────────────────────────────────

    /**
     * Returns the recent system-wide CPU load as a fraction (0.0–1.0).
     * Returns -1.0 if the JMX implementation does not support this metric.
     */
    public double getCpuLoad() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            return sunBean.getCpuLoad();
        }
        return -1.0;
    }

    /**
     * Returns {@code true} when CPU load exceeds {@link #cpuThreshold}.
     * Always returns {@code false} if the metric is unavailable.
     */
    public boolean isCpuCritical() {
        double load = getCpuLoad();
        return load > 0 && load > cpuThreshold;
    }

    // ─── Combined ────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} when either memory or CPU is in a critical state.
     * The scheduler should throttle or pause job intake when this is {@code true}.
     */
    public boolean isSystemStressed() {
        return isMemoryCritical() || isCpuCritical();
    }

    /**
     * Builds a {@link SchedulerStats}-compatible resource snapshot.
     *
     * @return heap used/max bytes and CPU load
     */
    public long[] getHeapBytes() {
        long used = memoryBean.getHeapMemoryUsage().getUsed();
        long max  = memoryBean.getHeapMemoryUsage().getMax();
        return new long[]{used, max};
    }
}
