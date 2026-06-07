package org.investpro.strategy.auto;

import org.investpro.strategy.StrategyAssignment;

import java.util.List;

public record StrategyAssignmentDecision(
        String symbol,
        String currentStrategyName,
        String selectedStrategyName,
        boolean assigned,
        boolean liveAssignmentAllowed,
        String strategyName,
        double currentScore,
        double selectedScore,
        String reason,
        List<String> warnings,
        StrategyAssignment assignment) {
}
