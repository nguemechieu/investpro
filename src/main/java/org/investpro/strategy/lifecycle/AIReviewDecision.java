package org.investpro.strategy.lifecycle;

/**
 * Decision outcome from AI strategy review (backtest or paper trade validation).
 * AI reviews are advisory; the final lifecycle transition is managed by
 * {@link org.investpro.strategy.management.StrategyAssignmentManager}.
 */
public enum AIReviewDecision {
    APPROVE("Strategy approved to proceed to next phase"),
    REJECT("Strategy rejected - does not meet requirements"),
    NEEDS_MORE_DATA("Insufficient data for reliable assessment"),
    PAPER_TRADE_FIRST("Strategy requires paper trading validation before live approval"),
    LOW_CONFIDENCE("AI confidence too low to make a reliable determination");

    /** Human-readable description of this decision. */
    public final String description;

    AIReviewDecision(String description) {
        this.description = description;
    }

    /** @return true if the AI has given a positive approval decision. */
    public boolean isPositive() {
        return this == APPROVE;
    }

    /** @return true if more work or data is required before a final decision can be made. */
    public boolean requiresMoreWork() {
        return this == NEEDS_MORE_DATA || this == PAPER_TRADE_FIRST;
    }
}
