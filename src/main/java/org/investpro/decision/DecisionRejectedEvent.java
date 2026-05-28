package org.investpro.decision;

import org.investpro.core.agents.AgentEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.List;

/**
 * Event emitted when a decision is rejected by any pipeline phase
 * (risk evaluation, AI reasoning, or signal validation).
 */
public record DecisionRejectedEvent(
        @NotNull String decisionId,
        @NotNull TradeIntent intent,
        @NotNull DecisionStatus rejectionStatus,
        @NotNull List<String> rejectionReasons,
        @NotNull Instant occurredAt
) {
    public static final String TYPE = "DECISION_REJECTED";

    public AgentEvent toAgentEvent() {
        return AgentEvent.of(TYPE, "DecisionPipeline", this);
    }
}
