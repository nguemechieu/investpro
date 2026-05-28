package org.investpro.decision;

import org.investpro.core.agents.AgentEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Event emitted when a decision has been fully approved and is queued for execution.
 */
public record DecisionApprovedEvent(
        @NotNull String decisionId,
        @NotNull ExecutionDecision decision,
        @NotNull Instant occurredAt
) {
    public static final String TYPE = "DECISION_APPROVED";

    public AgentEvent toAgentEvent() {
        return AgentEvent.of(TYPE, "DecisionPipeline", this);
    }
}
