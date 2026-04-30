package org.investpro.core.agents.risk;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Result of risk review.
 */
@Getter
@Setter
public class RiskDecision {

    private final String decisionId;
    private final boolean approved;
    private final String action;
    private final double approvedSize;
    private final double maxLoss;
    private final List<String> reasons;
    private final Object sourcePayload;
    private final Instant timestamp;

    public RiskDecision(boolean approved, String action, double approvedSize, double maxLoss, List<String> reasons, Object sourcePayload) {
        this.decisionId = UUID.randomUUID().toString();
        this.approved = approved;
        this.action = action == null ? "HOLD" : action.toUpperCase();
        this.approvedSize = Math.max(0.0, approvedSize);
        this.maxLoss = Math.max(0.0, maxLoss);
        this.reasons = Collections.unmodifiableList(new ArrayList<>(reasons == null ? List.of() : reasons));
        this.sourcePayload = sourcePayload;
        this.timestamp = Instant.now();
    }

}
