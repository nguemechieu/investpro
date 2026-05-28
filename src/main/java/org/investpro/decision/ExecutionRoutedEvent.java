package org.investpro.decision;

import java.time.Instant;

/**
 * Event emitted when an execution route is selected for a trade decision.
 *
 * @param decisionId unique decision identifier
 * @param route      the selected execution route
 * @param occurredAt timestamp when the event occurred
 */
public record ExecutionRoutedEvent(
        String decisionId,
        ExecutionRoute route,
        Instant occurredAt
) {
    public ExecutionRoutedEvent {
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public static ExecutionRoutedEvent of(String decisionId, ExecutionRoute route) {
        return new ExecutionRoutedEvent(decisionId, route, Instant.now());
    }
}
