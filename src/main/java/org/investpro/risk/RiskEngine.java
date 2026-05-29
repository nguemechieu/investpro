package org.investpro.risk;

import org.investpro.decision.BotTradeDecision;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Decision-layer risk gate that does not submit orders.
 */
public class RiskEngine {

    @NotNull
    public RiskDecision evaluate(@NotNull BotTradeDecision decision) {
        List<String> blockers = new ArrayList<>(decision.blockers());
        List<String> warnings = new ArrayList<>(decision.warnings());

        boolean approved = blockers.isEmpty()
                && (decision.finalAction() == BotTradeDecision.FinalAction.TRADE
                        || decision.finalAction() == BotTradeDecision.FinalAction.REDUCE_SIZE
                        || decision.finalAction() == BotTradeDecision.FinalAction.CLOSE);

        RiskDecisionType decisionType = approved
                ? RiskDecisionType.APPROVE
                : RiskDecisionType.REJECT;

        return RiskDecision.builder()
                .approved(approved)
                .decisionType(decisionType)
                .approvalReason(approved ? "Risk approved" : "Risk rejected")
                .finalPositionSize(approved ? 1.0 : 0.0)
                .finalLeverage(1.0)
                .riskMultiplier(approved ? 1.0 : 0.0)
                .expectedValue(decision.expectation().expectedValue().doubleValue())
                .portfolioHeat(0.0)
                .estimatedSlippage(decision.costEstimate().slippage().doubleValue())
                .blockers(blockers)
                .warnings(warnings)
                .recommendations(List.of())
                .humanReadableSummary(approved ? "Trade can proceed under risk policy" : "Trade blocked by risk policy")
                .build();
    }
}
