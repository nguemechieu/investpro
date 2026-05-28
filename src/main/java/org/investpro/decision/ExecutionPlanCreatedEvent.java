package org.investpro.decision;

import org.investpro.core.agents.AgentEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Event emitted when an {@link ExecutionPlan} has been generated and validated.
 */
public record ExecutionPlanCreatedEvent(
        @NotNull String decisionId,
        @NotNull TradeIntent intent,
        @NotNull ExecutionPlan plan,
        @NotNull Instant occurredAt
) {
    public static final String TYPE = "EXECUTION_PLAN_CREATED";

    public AgentEvent toAgentEvent() {
        return AgentEvent.of(TYPE, "ExecutionPlanningPhase", this);
    }
}
