package org.investpro.decision;

import java.time.Instant;

/**
 * Event emitted when a trade order is submitted to an execution venue.
 *
 * @param decisionId unique decision identifier
 * @param venue      target execution venue
 * @param occurredAt timestamp when the event occurred
 */
public record ExecutionSubmittedEvent(
        String decisionId,
        ExecutionVenueType venue,
        Instant occurredAt
) {
    public ExecutionSubmittedEvent {
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public static ExecutionSubmittedEvent of(String decisionId, ExecutionVenueType venue) {
        return new ExecutionSubmittedEvent(decisionId, venue, Instant.now());
    }
}
