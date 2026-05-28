package org.investpro.decision;

import java.time.Instant;

/**
 * Event emitted when position sizing is completed for a trade decision.
 *
 * @param decisionId   unique decision identifier
 * @param sizing       the completed position sizing decision
 * @param occurredAt   timestamp when the event occurred
 */
public record PositionSizedEvent(
        String decisionId,
        PositionSizingDecision sizing,
        Instant occurredAt
) {
    public PositionSizedEvent {
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public static PositionSizedEvent of(String decisionId, PositionSizingDecision sizing) {
        return new PositionSizedEvent(decisionId, sizing, Instant.now());
    }
}
