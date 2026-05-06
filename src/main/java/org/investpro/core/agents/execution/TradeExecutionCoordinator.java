package org.investpro.core.agents.execution;

import org.investpro.ai.AiReasoningService;
import org.investpro.ai.AiTradeReviewRequest;
import org.investpro.ai.AiTradeReviewResponse;
import org.investpro.ai.FinalRiskGate;
import org.investpro.risk.RiskDecision;
import org.investpro.risk.RiskManagementSystem;
import org.investpro.risk.TradeRiskContext;
import org.investpro.strategy.StrategySignal;

public class TradeExecutionCoordinator {

    private final RiskManagementSystem riskManagementSystem;
    private final AiReasoningService aiReasoningService;
    private final ExecutionEngine executionEngine;

    public TradeExecutionCoordinator(
            RiskManagementSystem riskManagementSystem,
            AiReasoningService aiReasoningService,
            ExecutionEngine executionEngine
    ) {
        this.riskManagementSystem = riskManagementSystem;
        this.aiReasoningService = aiReasoningService;
        this.executionEngine = executionEngine;
    }

    public void handleSignal(StrategySignal signal, TradeRiskContext riskContext) {
        RiskDecision riskDecision = riskManagementSystem.evaluateTrade(riskContext);

        AiTradeReviewRequest aiRequest = AiTradeReviewRequest.from(signal, riskContext, riskDecision);
        AiTradeReviewResponse aiResponse = aiReasoningService.reviewTrade(aiRequest);

        FinalRiskGate.OrderApprovalDecision finalDecision =
                FinalRiskGate.makeDecision(riskDecision, aiResponse);

        if (finalDecision.isApproved()) {
            executionEngine.executeApprovedOrder(signal, riskContext, finalDecision);
            return;
        }

        if (finalDecision.requiresManualReview()) {
            createManualReviewTicket(signal, riskContext, riskDecision, aiResponse, finalDecision);
            return;
        }

        if (finalDecision.shouldWait()) {
            logWaitDecision(signal, finalDecision);
            return;
        }

        if (finalDecision.isRejected()) {
            logRejectedTrade(signal, finalDecision);
        }
    }

    private void createManualReviewTicket(
            StrategySignal signal,
            TradeRiskContext riskContext,
            RiskDecision riskDecision,
            AiTradeReviewResponse aiResponse,
            FinalRiskGate.OrderApprovalDecision finalDecision
    ) {
        // Save to database, show in UI, or notify user.
    }

    private void logWaitDecision(StrategySignal signal, FinalRiskGate.OrderApprovalDecision finalDecision) {
        // Store in audit log.
    }

    private void logRejectedTrade(StrategySignal signal, FinalRiskGate.OrderApprovalDecision finalDecision) {
        // Store in audit log.
    }
}