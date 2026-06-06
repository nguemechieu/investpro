package org.investpro.terminal.domain;

import java.time.Instant;
import java.util.List;

public record RiskDecision(boolean allowed, String reason, List<String> violations, Instant checkedAt) {
    public RiskDecision {
        reason = reason == null ? "" : reason.trim();
        violations = violations == null ? List.of() : List.copyOf(violations);
        checkedAt = checkedAt == null ? Instant.now() : checkedAt;
    }
}
