package org.investpro.terminal.domain;

import java.time.Instant;
import java.util.Map;

public record StrategyScore(
        String strategyId,
        InstrumentId instrumentId,
        double score,
        String reason,
        Instant scoredAt,
        Map<String, Object> metrics
) {
    public StrategyScore {
        strategyId = strategyId == null ? "" : strategyId.trim();
        score = Math.max(0.0, Math.min(100.0, score));
        reason = reason == null ? "" : reason.trim();
        scoredAt = scoredAt == null ? Instant.now() : scoredAt;
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }
}
