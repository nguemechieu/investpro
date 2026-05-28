package org.investpro.strategy.provider;

/**
 * Runtime cost class used by scheduling, profiling, and UI filtering.
 */
public enum StrategyComplexity {
    LIGHT(1),
    MEDIUM(2),
    HEAVY(4),
    EXTREME(8);

    private final int weight;

    StrategyComplexity(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
