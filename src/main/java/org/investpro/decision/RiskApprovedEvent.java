package org.investpro.decision;

import java.time.Instant;

/**
 * Event emitted when risk evaluation approves or reduces a trade decision.
 *
 * @param decisionId     unique decision identifier
 * @param verdict        risk evaluation verdict (APPROVED or REDUCED)
 * @param riskEvaluation full risk evaluation result
 * @param occurredAt     timestamp when the event occurred
 */
public record RiskApprovedEvent(
        String decisionId,
        RiskEvaluation.Verdict verdict,
        RiskEvaluation riskEvaluation,
        Instant occurredAt
) {
    public RiskApprovedEvent {
        if (occurredAt == null) occurredAt = Instant.now();
    }

    public static RiskApprovedEvent of(String decisionId, RiskEvaluation evaluation) {
        return new RiskApprovedEvent(decisionId, evaluation.verdict(), evaluation, Instant.now());
    }
}
