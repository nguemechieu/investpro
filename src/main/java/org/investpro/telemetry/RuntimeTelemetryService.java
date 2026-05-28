package org.investpro.telemetry;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Low-allocation metrics collector for long-running desktop sessions.
 */
public final class RuntimeTelemetryService {
    private static final RuntimeTelemetryService INSTANCE = new RuntimeTelemetryService();

    private final OperatingSystemMXBean osBean;
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    private RuntimeTelemetryService() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        this.osBean = bean instanceof OperatingSystemMXBean os ? os : null;
    }

    public static RuntimeTelemetryService getInstance() {
        return INSTANCE;
    }

    public void increment(String counterName) {
        add(counterName, 1);
    }

    public void add(String counterName, long delta) {
        if (counterName == null || counterName.isBlank()) {
            return;
        }
        counters.computeIfAbsent(counterName, ignored -> new LongAdder()).add(delta);
    }

    public RuntimeMetricSnapshot snapshot() {
        MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        MemoryUsage nonHeap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
        long gcCount = 0L;
        long gcMillis = 0L;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            if (gcBean.getCollectionCount() > 0) {
                gcCount += gcBean.getCollectionCount();
            }
            if (gcBean.getCollectionTime() > 0) {
                gcMillis += gcBean.getCollectionTime();
            }
        }

        Map<String, Number> copy = new java.util.LinkedHashMap<>();
        counters.forEach((key, value) -> copy.put(key, value.sum()));

        return new RuntimeMetricSnapshot(
                osBean == null ? -1.0 : osBean.getProcessCpuLoad(),
                osBean == null ? -1.0 : osBean.getCpuLoad(),
                heap.getUsed(),
                heap.getMax(),
                nonHeap.getUsed(),
                gcCount,
                gcMillis,
                copy,
                Instant.now());
    }
}
