package org.investpro.decision;

import org.investpro.core.agents.AgentEvent;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Event emitted when a {@link TradeIntent} is first created in the decision pipeline.
 *
 * <p>Publish to {@link org.investpro.core.agents.AgentEventBus} so downstream
 * subscribers (portfolio analyzers, risk engines, AI layers) can react immediately.</p>
 */
public record TradeIntentCreatedEvent(
        @NotNull String decisionId,
        @NotNull TradeIntent intent,
        @NotNull Instant occurredAt
) {

    /** Event type constant used for AgentEventBus routing. */
    public static final String TYPE = "TRADE_INTENT_CREATED";

    /** Wraps this domain event into an {@link AgentEvent} for bus publication. */
    public AgentEvent toAgentEvent() {
        return AgentEvent.of(TYPE, "DecisionPipeline", this);
    }
}
