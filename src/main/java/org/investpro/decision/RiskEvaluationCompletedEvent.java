package org.investpro.decision;

import org.investpro.core.agents.AgentEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Event emitted when the risk evaluation phase completes for a decision.
 */
public record RiskEvaluationCompletedEvent(
        @NotNull String decisionId,
        @NotNull TradeIntent intent,
        @NotNull RiskEvaluation evaluation,
        @NotNull Instant occurredAt
) {
    public static final String TYPE = "RISK_EVALUATION_COMPLETED";

    public AgentEvent toAgentEvent() {
        return AgentEvent.of(TYPE, "RiskEvaluationPhase", this);
    }
}
