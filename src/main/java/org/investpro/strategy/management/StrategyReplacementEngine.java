package org.investpro.strategy.management;

import org.investpro.strategy.ai.AIReplacementEngine;
import org.investpro.strategy.lifecycle.AIReplacementReport;
import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyRankScore;

import java.util.List;

/**
 * Evaluates replacement candidates for degraded or critical strategies.
 *
 * <p>Recommendations are advisory; only {@link StrategyAssignmentManager}
 * performs demotion, pause, or replacement transitions.</p>
 */
public final class StrategyReplacementEngine {

    private static volatile StrategyReplacementEngine instance;
    private final AIReplacementEngine delegate = AIReplacementEngine.getInstance();

    private StrategyReplacementEngine() {
    }

    /** @return singleton replacement engine. */
    public static StrategyReplacementEngine getInstance() {
        StrategyReplacementEngine local = instance;
        if (local == null) {
            synchronized (StrategyReplacementEngine.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyReplacementEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Finds the best platform-approved action for a degraded strategy. */
    public AIReplacementReport evaluateReplacement(
            StrategyLifecycleRecord degradedRecord,
            List<StrategyRankScore> availableCandidates) {
        return delegate.evaluateReplacement(degradedRecord, availableCandidates);
    }
}
