package org.investpro.ai;

import lombok.Getter;

/**
 * Recommended actions for open positions from AI position manager.
 * <p>
 * AI may recommend these actions, but RiskManagementSystem and FinalRiskGate retain final authority.
 * AI cannot directly execute these actions. Only approved PositionActionIntent objects are executed.
 */
public enum AiPositionAction {

    /**
     * Hold the position. Current levels are acceptable.
     */
    HOLD("Hold position", false, false, false, false, false, false),

    /**
     * Reduce position size. Lowers risk exposure while keeping the position alive.
     */
    REDUCE_SIZE("Reduce position size", true, false, false, false, false, false),

    /**
     * Take partial profit. Closes a portion of the position.
     */
    TAKE_PARTIAL_PROFIT("Take partial profit", true, false, false, true, false, false),

    /**
     * Move stop loss to a better level.
     * <p>
     * Warning:
     * This can reduce or increase risk depending on direction, so the validator
     * must check whether the new stop is safer than the old stop.
     */
    MOVE_STOP_LOSS("Move stop loss", false, true, false, false, false, false),

    /**
     * Implement or tighten trailing stop loss.
     */
    TRAIL_STOP("Implement/tighten trailing stop", true, true, false, false, false, false),

    /**
     * Move take-profit level.
     */
    MOVE_TAKE_PROFIT("Move take-profit level", false, false, false, true, false, false),

    /**
     * Close the entire position.
     */
    CLOSE_POSITION("Close position", true, false, true, true, false, false),

    /**
     * Open a hedge position if supported.
     * <p>
     * This is complex and should require manual approval unless you later build
     * full broker/account hedge-mode validation.
     */
    HEDGE("Open hedge position", false, false, false, false, true, true),

    /**
     * Escalate to manual human review.
     */
    ESCALATE_TO_MANUAL_REVIEW("Escalate to manual review", false, false, false, false, true, false);

    @Getter
    private final String description;

    private final boolean reducesRisk;
    private final boolean modifiesStop;
    private final boolean closesPosition;
    private final boolean affectsProfit;
    private final boolean requiresManualApproval;
    private final boolean increasesComplexity;

    AiPositionAction(
            String description,
            boolean reducesRisk,
            boolean modifiesStop,
            boolean closesPosition,
            boolean affectsProfit,
            boolean requiresManualApproval,
            boolean increasesComplexity
    ) {
        this.description = description;
        this.reducesRisk = reducesRisk;
        this.modifiesStop = modifiesStop;
        this.closesPosition = closesPosition;
        this.affectsProfit = affectsProfit;
        this.requiresManualApproval = requiresManualApproval;
        this.increasesComplexity = increasesComplexity;
    }

    public boolean reducesRisk() {
        return reducesRisk;
    }

    public boolean modifiesStop() {
        return modifiesStop;
    }

    public boolean closesPosition() {
        return closesPosition;
    }

    public boolean affectsProfit() {
        return affectsProfit;
    }

    public boolean requiresManualApproval() {
        return requiresManualApproval;
    }

    public boolean increasesComplexity() {
        return increasesComplexity;
    }

    /**
     * Returns true when this action is defensive and can generally be considered
     * safer than adding risk, assuming FinalRiskGate approves it.
     */
    public boolean isDefensiveAction() {
        return reducesRisk || closesPosition || this == TRAIL_STOP || this == TAKE_PARTIAL_PROFIT;
    }

    /**
     * Returns true when this action should never be auto-executed without extra validation.
     */
    public boolean requiresStrictValidation() {
        return this == MOVE_STOP_LOSS
                || this == MOVE_TAKE_PROFIT
                || this == HEDGE
                || requiresManualApproval;
    }
}