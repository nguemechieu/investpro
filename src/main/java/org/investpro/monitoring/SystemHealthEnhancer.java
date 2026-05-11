package org.investpro.monitoring;

import lombok.extern.slf4j.Slf4j;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;

/**
 * Utility class for gathering system resource and health metrics.
 * Provides methods to collect memory, CPU, thread, and network statistics.
 *
 * @author NOEL NGUEMECHIEU
 */
@Slf4j
@SuppressWarnings("unused")
public final class SystemHealthEnhancer {

    /**
     * System resource metrics
     */
    public record SystemMetrics(
            long heapUsedMB,
            long heapMaxMB,
            double heapUsagePercent,
            double cpuUsagePercent,
            int threadCount,
            int peakThreadCount,
            long uptime,
            long systemFreeMemoryMB) {
    }

    /**
     * Get current system metrics
     */
    public static SystemMetrics getSystemMetrics() {
        try {
            MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

            long heapUsed = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
            long heapMax = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024);
            double heapPercent = (double) heapUsed / heapMax * 100;
            double cpuUsage = osBean.getSystemLoadAverage() >= 0 ? osBean.getSystemLoadAverage() * 100 : 0;
            int threadCount = threadBean.getThreadCount();
            int peakCount = threadBean.getPeakThreadCount();
            long uptime = runtimeBean.getUptime();
            long freeMemory = Runtime.getRuntime().freeMemory() / (1024 * 1024);

            return new SystemMetrics(
                    heapUsed,
                    heapMax,
                    heapPercent,
                    cpuUsage < 0 ? 0 : cpuUsage,
                    threadCount,
                    peakCount,
                    uptime,
                    freeMemory);
        } catch (Exception e) {
            log.warn("Failed to gather system metrics", e);
            return new SystemMetrics(0, 0, 0, 0, 0, 0, 0, 0);
        }
    }

    /**
     * Create a ComponentHealth for system resources
     */
    public static ComponentHealth getSystemResourcesHealth() {
        SystemMetrics metrics = getSystemMetrics();

        ComponentStatus status = ComponentStatus.HEALTHY;
        StringBuilder summary = new StringBuilder();

        summary.append("Heap: ").append(metrics.heapUsedMB()).append("MB / ")
                .append(metrics.heapMaxMB()).append("MB (").append(String.format("%.1f", metrics.heapUsagePercent()))
                .append("%), CPU: ").append(String.format("%.1f", metrics.cpuUsagePercent())).append("%");

        // Check for issues
        if (metrics.heapUsagePercent() > 90) {
            status = ComponentStatus.FAILED;
            summary.append(" - CRITICAL: Heap nearly full!");
        } else if (metrics.heapUsagePercent() > 80) {
            status = ComponentStatus.DEGRADED;
            summary.append(" - WARNING: Heap usage high");
        }

        if (metrics.cpuUsagePercent() > 95) {
            if (status == ComponentStatus.HEALTHY) {
                status = ComponentStatus.DEGRADED;
            }
            summary.append(" - WARNING: CPU usage high");
        }

        return ComponentHealth.builder()
                .componentName("System Resources")
                .status(status)
                .summary(summary.toString())
                .build();
    }

    /**
     * Format uptime in human-readable format
     */
    public static String formatUptime(long uptimeMs) {
        long seconds = uptimeMs / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        long days = hours / 24;

        if (days > 0) {
            return days + "d " + (hours % 24) + "h";
        } else if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }

    /**
     * Get memory usage percentage
     */
    public static double getMemoryUsagePercent() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        return (double) heapUsed / heapMax * 100;
    }

    /**
     * Get CPU usage percentage
     */
    public static double getCpuUsagePercent() {
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        double cpuLoad = osBean.getSystemLoadAverage();
        return cpuLoad < 0 ? 0 : cpuLoad * 100;
    }

    /**
     * Get current thread count
     */
    public static int getThreadCount() {
        return ManagementFactory.getThreadMXBean().getThreadCount();
    }

    /**
     * Get peak thread count
     */
    public static int getPeakThreadCount() {
        return ManagementFactory.getThreadMXBean().getPeakThreadCount();
    }

    /**
     * Get heap memory used in MB
     */
    public static long getHeapUsedMB() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed() / (1024 * 1024);
    }

    /**
     * Get heap memory max in MB
     */
    public static long getHeapMaxMB() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax() / (1024 * 1024);
    }

    /**
     * Get system free memory in MB
     */
    public static long getFreeMemoryMB() {
        return Runtime.getRuntime().freeMemory() / (1024 * 1024);
    }
}
