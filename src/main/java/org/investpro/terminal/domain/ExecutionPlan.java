package org.investpro.terminal.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ExecutionPlan(
        String planId,
        OrderRequest orderRequest,
        RiskDecision riskDecision,
        List<String> guardrails,
        Instant createdAt,
        Map<String, Object> metadata
) {
    public ExecutionPlan {
        planId = planId == null ? "" : planId.trim();
        if (orderRequest == null) {
            throw new IllegalArgumentException("orderRequest is required");
        }
        guardrails = guardrails == null ? List.of() : List.copyOf(guardrails);
        createdAt = createdAt == null ? Instant.now() : createdAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
