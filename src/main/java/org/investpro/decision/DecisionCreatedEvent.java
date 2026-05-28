package org.investpro.decision;

import java.time.Instant;

/**
 * Event emitted when a new trade decision enters the institutional pipeline.
 *
 * @param decisionId unique identifier of the decision
 * @param intent     the trade intent that triggered the decision
 * @param mode       execution mode (LIVE, PAPER, SIMULATION, LIGHTWEIGHT)
 * @param occurredAt timestamp when the event occurred
 */
public record DecisionCreatedEvent(
        String decisionId,
        TradeIntent intent,
        DecisionMode mode,
        Instant occurredAt
) {
    public DecisionCreatedEvent {
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public static DecisionCreatedEvent of(String decisionId, TradeIntent intent, DecisionMode mode) {
        return new DecisionCreatedEvent(decisionId, intent, mode, Instant.now());
    }
}
