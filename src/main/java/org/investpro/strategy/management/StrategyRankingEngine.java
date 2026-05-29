package org.investpro.strategy.management;

import org.investpro.strategy.lifecycle.StrategyLifecycleRecord;
import org.investpro.strategy.lifecycle.StrategyRankScore;

import java.util.List;

/**
 * Institutional lifecycle ranking facade.
 *
 * <p>Combines backtest score, paper score, AI score, health, regime fit, risk,
 * consistency, and stability into a platform-owned overall score.</p>
 */
public final class StrategyRankingEngine {

    private static volatile StrategyRankingEngine instance;
    private final StrategyLifecycleRankingEngine delegate = StrategyLifecycleRankingEngine.getInstance();

    private StrategyRankingEngine() {
    }

    /** @return singleton ranking engine. */
    public static StrategyRankingEngine getInstance() {
        StrategyRankingEngine local = instance;
        if (local == null) {
            synchronized (StrategyRankingEngine.class) {
                local = instance;
                if (local == null) {
                    local = new StrategyRankingEngine();
                    instance = local;
                }
            }
        }
        return local;
    }

    /** Ranks lifecycle records from best to weakest. */
    public List<StrategyRankScore> rankStrategies(List<StrategyLifecycleRecord> records) {
        return delegate.rankStrategies(records);
    }

    /** Scores a single lifecycle record. */
    public StrategyRankScore score(StrategyLifecycleRecord record) {
        return delegate.scoreRecord(record);
    }
}
