package org.investpro.strategy.lab;

import java.time.Instant;
import java.util.List;

public record AdaptiveSchedulerDecision(
        int recommendedWorkers,
        boolean pauseLowPriority,
        boolean stressed,
        List<String> warnings,
        Instant decidedAt) {

    public AdaptiveSchedulerDecision {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        decidedAt = decidedAt == null ? Instant.now() : decidedAt;
    }
}
