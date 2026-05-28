package org.investpro.reasoning;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * AI/local reasoning decision after risk approval.
 */
@Getter
@Setter
public class ReasoningDecision {

    private final String decisionId;
    private final boolean approved;
    private final double confidence;
    private final String summary;
    private final List<String> reasons;
    private final Object sourcePayload;
    private final Instant timestamp;

    public ReasoningDecision(boolean approved, double confidence, String summary, List<String> reasons, Object sourcePayload) {
        this.decisionId = UUID.randomUUID().toString();
        this.approved = approved;
        this.confidence = Math.max(0.0, Math.min(1.0, confidence));
        this.summary = summary == null ? "" : summary;
        this.reasons = List.copyOf(reasons == null ? List.of() : reasons);
        this.sourcePayload = sourcePayload;
        this.timestamp = Instant.now();
    }

}
