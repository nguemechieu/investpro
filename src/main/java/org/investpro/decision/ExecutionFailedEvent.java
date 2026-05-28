package org.investpro.decision;

import java.time.Instant;

/**
 * Event emitted when a trade decision execution fails at any phase.
 *
 * @param decisionId  unique decision identifier
 * @param reason      human-readable reason for the failure
 * @param phase       pipeline phase where the failure occurred
 * @param occurredAt  timestamp when the failure occurred
 */
public record ExecutionFailedEvent(
        String decisionId,
        String reason,
        String phase,
        Instant occurredAt
) {
    public ExecutionFailedEvent {
        if (reason == null)      reason = "unknown failure";
        if (phase == null)       phase = "unknown";
        if (occurredAt == null)  occurredAt = Instant.now();
    }

    public static ExecutionFailedEvent of(String decisionId, String reason, String phase) {
        return new ExecutionFailedEvent(decisionId, reason, phase, Instant.now());
    }

    public static ExecutionFailedEvent of(String decisionId, String reason) {
        return new ExecutionFailedEvent(decisionId, reason, "pipeline", Instant.now());
    }
}
