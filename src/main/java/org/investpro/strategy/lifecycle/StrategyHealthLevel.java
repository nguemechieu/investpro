package org.investpro.strategy.lifecycle;

/**
 * Health classification for an active strategy assignment.
 * Used by {@link org.investpro.strategy.ai.AIStrategyHealthEngine} to characterise
 * current strategy performance relative to baseline expectations.
 */
public enum StrategyHealthLevel {
    EXCELLENT("Strategy performing exceptionally well across all metrics", 5),
    GOOD("Strategy performing well within acceptable parameters", 4),
    WATCH("Strategy showing early signs of degradation - monitoring intensified", 3),
    DEGRADED("Strategy performance has degraded significantly", 2),
    CRITICAL("Strategy is critically underperforming - immediate action required", 1);

    /** Human-readable description. */
    public final String description;

    /** Numeric priority where higher value means better health. */
    public final int priority;

    StrategyHealthLevel(String description, int priority) {
        this.description = description;
        this.priority = priority;
    }

    /** @return true if this health level requires active intervention (e.g. replacement or demotion). */
    public boolean requiresIntervention() {
        return this == DEGRADED || this == CRITICAL;
    }

    /** @return true if this health level is within acceptable performance bounds. */
    public boolean isAcceptable() {
        return this == EXCELLENT || this == GOOD;
    }
}
