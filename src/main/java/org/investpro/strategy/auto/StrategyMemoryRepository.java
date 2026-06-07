package org.investpro.strategy.auto;

import java.util.List;

public interface StrategyMemoryRepository {
    void saveCandidate(StrategyCandidate candidate);

    void saveEvaluation(StrategyEvaluationResult result);

    void saveDecision(StrategyAssignmentDecision decision);

    List<StrategyCandidate> candidatesFor(String symbol);

    List<StrategyEvaluationResult> evaluationsFor(String symbol);

    List<StrategyAssignmentDecision> decisionsFor(String symbol);
}
