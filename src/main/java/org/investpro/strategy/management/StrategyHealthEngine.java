package org.investpro.strategy.management;

import org.investpro.strategy.ai.AIStrategyHealthEngine;
import org.investpro.strategy.lifecycle.StrategyHealthReport;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyPerformanceMetrics;

/**
 * Platform-owned health monitor for assigned strategies.
 *
 * <p>Health decisions are advisory until the assignment manager applies a
 * lifecycle transition. The engine does not place trades.</p>
 */
public final class StrategyHealthEngine {

    private static volatile StrategyHealthEngine instance;
    private final AIStrategyHealthEngine delegate = AIStrategyHealthEngine.getInstance();

    private StrategyHealthEngine() {
    }

    /** @return singleton health engine. */
    public static StrategyHealthEngine getInstance() {
        StrategyHealthEngine local = instance;
        if (local == null) {
            synchronized (StrategyHealthEngine.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyHealthEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Scores the latest performance snapshot and returns a health report. */
    public StrategyHealthReport assessHealth(StrategyLifecycleRecord record, StrategyPerformanceMetrics metrics) {
        return delegate.assessHealth(record, metrics);
    }
}
