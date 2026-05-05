package org.investpro.ai;

import lombok.Getter;

/**
 * Recommended actions for open positions from AI position manager.
 * AI may recommend these actions, but RiskManagementSystem and FinalRiskGate retain final authority.
 * AI cannot directly execute these actions—only approved PositionActionIntent objects are executed.
 */
public enum AiPositionAction {
    /**
     * Hold the position. Current levels are optimal.
     * Risk is acceptable, profit protection is adequate, thesis remains valid.
     */
    HOLD("Hold position", false, false, false, false, false),

    /**
     * Reduce position size. Lower risk exposure while keeping position alive.
     * Usually recommended when: risk is elevated, profit is significant but momentum weakens.
     * Reduces exposure and locks in partial gains.
     */
    REDUCE_SIZE("Reduce position size", true, false, false, false, false),

    /**
     * Take partial profit. Close a portion of the position at current favorable price.
     * Usually recommended when: position is in profit, momentum shows signs of weakness,
     * profit target is reached, or market conditions become less favorable.
     */
    TAKE_PARTIAL_PROFIT("Take partial profit", true, false, false, true, false),

    /**
     * Move stop loss up/down to better level.
     * Usually recommended when: price movement invalidates original stop logic,
     * support/resistance changes, or portfolio risk needs rebalancing.
     */
    MOVE_STOP_LOSS("Move stop loss", false, true, false, false, false),

    /**
     * Implement or tighten trailing stop loss.
     * Usually recommended when: position is in profit and momentum is strong,
     * to protect gains while allowing upside potential.
     */
    TRAIL_STOP("Implement/tighten trailing stop", false, true, false, false, false),

    /**
     * Move take-profit level.
     * Usually recommended when: market conditions change profit projections,
     * momentum suggests higher targets, or risk/reward shifts.
     */
    MOVE_TAKE_PROFIT("Move take-profit level", false, false, false, false, false),

    /**
     * Close the entire position.
     * Triggered when: risk rules require exit, thesis is invalidated, stop loss is hit,
     * take profit is reached, or emergency conditions occur.
     */
    CLOSE_POSITION("Close position", true, false, true, true, false),

    /**
     * Open a hedge position (if broker supports).
     * Usually recommended when: directional risk is very high, volatility spikes,
     * or tail risk protection is needed without closing the original position.
     * Requires broker hedging capability.
     */
    HEDGE("Open hedge position", false, false, false, false, false),

    /**
     * Escalate to manual human review.
     * Triggered when: AI cannot make high-confidence decision, edge case detected,
     * unusual market conditions, or data is incomplete.
     * Trader must manually review and decide.
     */
    ESCALATE_TO_MANUAL_REVIEW("Escalate to manual review", false, false, false, false, true);

    @Getter
    private final String description;
    private final boolean reducesRisk;
    private final boolean modifiesStop;
    private final boolean closesPosition;
    private final boolean affectsProfit;
    private final boolean requiresManualApproval;

    AiPositionAction(String description, boolean reducesRisk, boolean modifiesStop, 
                     boolean closesPosition, boolean affectsProfit, boolean requiresManualApproval) {
        this.description = description;
        this.reducesRisk = reducesRisk;
        this.modifiesStop = modifiesStop;
        this.closesPosition = closesPosition;
        this.affectsProfit = affectsProfit;
        this.requiresManualApproval = requiresManualApproval;
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
}
