package org.investpro.ai;

import lombok.Getter;

/**
 * AI reasoning engine decision on whether to approve, modify, wait, reject, or escalate a proposed trade.
 * This enum represents the final decision from the AI layer after risk review.
 *
 * The AI never places trades directly. All decisions flow through FinalRiskGate for final authority.
 */
public enum AiDecision {
    /**
     * Trade setup is valid. Proceed with execution at proposed position size.
     * Implies: Risk metrics are acceptable, market conditions are favorable, no concerns identified.
     */
    APPROVE("Trade approved for execution", true),

    /**
     * Trade is valid but position size should be reduced.
     * Implies: Strategy is sound but market conditions or account state warrant caution.
     * The suggested position size must be <= RiskDecision.finalPositionSize.
     */
    APPROVE_WITH_REDUCED_SIZE("Trade approved with reduced position size", true),

    /**
     * Trade is not immediately actionable. Wait for better conditions.
     * Implies: Market is not in an optimal state (poor liquidity, high volatility, adverse bias).
     * No order will be placed. User can retry later.
     */
    WAIT("Trade is valid but conditions not optimal. Wait for better market conditions.", false),

    /**
     * Trade should not be executed.
     * Implies: Setup has fundamental flaws, market conditions are unfavorable, or risks exceed acceptable thresholds.
     * This is a veto recommendation (FinalRiskGate may still block if RiskDecision already blocked).
     */
    REJECT("Trade rejected. Risks or market conditions unfavorable.", false),

    /**
     * Escalate to manual human review.
     * Implies: Edge case, ambiguous data, missing information, or AI cannot make high-confidence decision.
     * User/trader must manually review and approve/reject.
     */
    ESCALATE_TO_MANUAL_REVIEW("Trade requires manual human review", false);

    @Getter
    private final String description;
    private final boolean allowsExecution;

    AiDecision(String description, boolean allowsExecution) {
        this.description = description;
        this.allowsExecution = allowsExecution;
    }

    public boolean allowsExecution() {
        return allowsExecution;
    }
}
