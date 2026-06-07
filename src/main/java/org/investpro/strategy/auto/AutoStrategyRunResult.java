package org.investpro.strategy.auto;

import java.time.Instant;
import java.util.List;

public record AutoStrategyRunResult(
        StrategyGenerationContext context,
        List<StrategyCandidate> candidates,
        List<StrategyEvaluationResult> evaluations,
        StrategyAssignmentDecision assignmentDecision,
        boolean cancelled,
        Instant startedAt,
        Instant finishedAt) {
}
