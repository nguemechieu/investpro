package org.investpro.strategy.auto;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class StrategyRankingEngine {

    public List<StrategyEvaluationResult> rank(List<StrategyEvaluationResult> results) {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .sorted(Comparator.comparingDouble(StrategyEvaluationResult::score).reversed())
                .toList();
    }

    public Optional<StrategyEvaluationResult> best(List<StrategyEvaluationResult> results) {
        return rank(results).stream().findFirst();
    }
}
