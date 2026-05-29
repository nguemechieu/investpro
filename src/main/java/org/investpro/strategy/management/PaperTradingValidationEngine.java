package org.investpro.strategy.management;

import org.investpro.strategy.ai.AIStrategyValidationEngine;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyPerformanceMetrics;
import org.investpro.strategy.lifecycle.StrategyValidationReport;

import java.util.List;

/**
 * Validates paper trading evidence before a strategy can enter PAPER_APPROVED.
 *
 * <p>This engine tracks and scores paper-trading performance only. It never
 * executes orders or changes broker state.</p>
 */
public final class PaperTradingValidationEngine {

    private static volatile PaperTradingValidationEngine instance;
    private final AIStrategyValidationEngine validationEngine = AIStrategyValidationEngine.getInstance();

    private PaperTradingValidationEngine() {
    }

    /** @return singleton paper validation engine. */
    public static PaperTradingValidationEngine getInstance() {
        PaperTradingValidationEngine local = instance;
        if (local == null) {
            synchronized (PaperTradingValidationEngine.class) {
                local = instance;
                if (local == null) {
                    local = new PaperTradingValidationEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Validates paper trades using the platform-owned validation policy. */
    public StrategyValidationReport validate(StrategyLifecycleRecord record, List<StrategyPerformanceMetrics> metrics) {
        return validationEngine.validatePaperTrades(record, metrics);
    }
}
