package org.investpro.core.pipeline;

import lombok.extern.slf4j.Slf4j;
import org.investpro.risk.TradeRiskContext;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.RiskManagementSystem;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * TradeDecisionPipeline orchestrates the flow from trade signal through risk
 * evaluation.
 * <p>
 * This pipeline enforces the architecture rule that:
 * 1. All trades MUST go through risk evaluation before execution
 * 2. Risk decisions are fact-based and transparent
 * 3. Execution is decoupled from decision-making
 * <p>
 * Flow:
 * - Signal/Manual Trade → TradeRiskContext builder
 * - TradeRiskContext → RiskManagementSystem.evaluateTrade()
 * - RiskDecision (approved/rejected) returned
 * - Execution layer decides next action
 * <p>
 * This class does NOT execute trades. ExecutionEngine handles that.
 */
@Slf4j
public class TradeDecisionPipeline {

    private final RiskManagementSystem riskManagementSystem;

    public TradeDecisionPipeline(@NotNull RiskManagementSystem riskManagementSystem) {
        this.riskManagementSystem = Objects.requireNonNull(riskManagementSystem,
                "riskManagementSystem must not be null");
    }

    /**
     * Evaluate a trade through the risk management system.
     * <p>
     * This is the ONLY place where trade approval decisions are made.
     * All trades (automated signals or manual user actions) go through this.
     *
     * @param tradeContext the complete trade context with all relevant data
     * @return RiskDecision with approved flag and sizing/warnings
     * @throws IllegalArgumentException if context is invalid
     */
    public RiskDecision evaluate(@NotNull TradeRiskContext tradeContext) {
        Objects.requireNonNull(tradeContext, "tradeContext must not be null");

        log.debug("TradeDecisionPipeline: Evaluating trade for symbol={}, size={}",
                tradeContext.getSymbol(), tradeContext.getRequestedSize());

        try {
            RiskDecision decision = riskManagementSystem.evaluateTrade(tradeContext);

            if (decision == null) {
                log.error("TradeDecisionPipeline: Risk evaluation returned null");
                return RiskDecision.rejected("Risk evaluation failed - null response");
            }

            logDecision(decision, tradeContext.getSymbol().toString('/'));
            return decision;

        } catch (Exception e) {
            log.error("TradeDecisionPipeline: Error evaluating trade", e);
            return RiskDecision.rejected("Risk evaluation error: " + e.getMessage());
        }
    }

    /**
     * Check if a trade would be approved without executing it.
     * <p>
     * Useful for UI preview of whether a trade would be allowed.
     *
     * @param tradeContext the trade context
     * @return true if trade would be approved
     */
    public boolean wouldApprove(@NotNull TradeRiskContext tradeContext) {
        RiskDecision decision = evaluate(tradeContext);
        return decision != null && decision.isApproved();
    }

    /**
     * Get approval reason for a trade decision.
     *
     * @param decision the risk decision
     * @return human-readable reason or summary
     */
    public String getApprovalReason(@NotNull RiskDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");

        if (decision.isApproved()) {
            return decision.getApprovalReason() != null
                    ? decision.getApprovalReason()
                    : decision.getHumanReadableSummary();
        } else {
            // For rejected trades, blockers take precedence
            if (decision.getBlockers() != null && !decision.getBlockers().isEmpty()) {
                return String.join("; ", decision.getBlockers());
            }
            return decision.getApprovalReason() != null
                    ? decision.getApprovalReason()
                    : "Trade rejected";
        }
    }

    private void logDecision(RiskDecision decision, String symbol) {
        if (decision.isApproved()) {
            log.info("TradeDecisionPipeline: Trade approved for {}. Size={}, Leverage={}, Summary: {}",
                    symbol,
                    decision.getFinalPositionSize(),
                    decision.getFinalLeverage(),
                    decision.getHumanReadableSummary());
        } else {
            log.warn("TradeDecisionPipeline: Trade rejected for {}. Reasons: {} (blockers), {} (warnings)",
                    symbol,
                    decision.getBlockers(),
                    decision.getWarnings());
        }
    }
}
