package org.investpro.backtesting.simulation;

import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;

/**
 * Low-cost resource checks for long desktop sessions.
 */
@Slf4j
public final class SimulationResourceGuard {
    private final boolean enabled;
    private final double criticalMemoryRatio;

    public SimulationResourceGuard(boolean enabled) {
        this.enabled = enabled;
        this.criticalMemoryRatio = AppConfig.getDouble("backtest.criticalMemoryRatio", 0.92);
    }

    public void check(int candleIndex) throws InterruptedException {
        if (!enabled || candleIndex % 512 != 0) {
            return;
        }
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long used = runtime.totalMemory() - runtime.freeMemory();
        if (max > 0L && (double) used / max >= criticalMemoryRatio) {
            log.warn("Simulation memory pressure high: used={}MiB max={}MiB; yielding worker",
                    used / 1024 / 1024, max / 1024 / 1024);
            Thread.sleep(25L);
        }
    }
}
