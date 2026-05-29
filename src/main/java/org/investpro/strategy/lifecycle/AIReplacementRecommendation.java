package org.investpro.strategy.lifecycle;

/**
 * AI recommendation for a degraded strategy assignment.
 * The recommendation is advisory only; {@link org.investpro.strategy.management.StrategyAssignmentManager}
 * is responsible for applying any replacement or demotion action.
 */
public enum AIReplacementRecommendation {
    KEEP("Strategy should be retained - temporary performance dip"),
    REPLACE("Strategy should be replaced with a higher-ranked candidate"),
    DEMOTE("Strategy should be demoted to paper trading for re-validation"),
    PAUSE("Strategy should be paused pending market regime change");

    /** Human-readable description. */
    public final String description;

    AIReplacementRecommendation(String description) {
        this.description = description;
    }

    /** @return true if any concrete action is required (i.e. recommendation is not KEEP). */
    public boolean requiresAction() {
        return this != KEEP;
    }
}
