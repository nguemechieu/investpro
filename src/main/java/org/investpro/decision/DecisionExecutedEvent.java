package org.investpro.decision;

import org.investpro.core.agents.AgentEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Event emitted when a decision transitions to {@link DecisionStatus#EXECUTED}
 * or {@link DecisionStatus#FAILED} after order submission.
 */
public record DecisionExecutedEvent(
        @NotNull String decisionId,
        @NotNull ExecutionDecision decision,
        boolean success,
        String failureReason,
         String exchangeOrderId,
        @NotNull Instant occurredAt
) {
    public static final String TYPE = "DECISION_EXECUTED";

    public AgentEvent toAgentEvent() {
        return AgentEvent.of(TYPE, "ExecutionVenue", this);
    }
}
