package org.investpro.strategy.auto;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryStrategyMemoryRepository implements StrategyMemoryRepository {

    private final ConcurrentMap<String, List<StrategyCandidate>> candidates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<StrategyEvaluationResult>> evaluations = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, List<StrategyAssignmentDecision>> decisions = new ConcurrentHashMap<>();

    @Override
    public void saveCandidate(StrategyCandidate candidate) {
        if (candidate == null) {
            return;
        }
        candidates.computeIfAbsent(candidate.symbol(), ignored -> new ArrayList<>()).add(candidate);
    }

    @Override
    public void saveEvaluation(StrategyEvaluationResult result) {
        if (result == null || result.candidate() == null) {
            return;
        }
        evaluations.computeIfAbsent(result.candidate().symbol(), ignored -> new ArrayList<>()).add(result);
    }

    @Override
    public void saveDecision(StrategyAssignmentDecision decision) {
        if (decision == null) {
            return;
        }
        String symbol = decision.symbol() == null || decision.symbol().isBlank() ? "UNKNOWN" : decision.symbol();
        decisions.computeIfAbsent(symbol, ignored -> new ArrayList<>()).add(decision);
    }

    @Override
    public List<StrategyCandidate> candidatesFor(String symbol) {
        return List.copyOf(candidates.getOrDefault(symbol, List.of()));
    }

    @Override
    public List<StrategyEvaluationResult> evaluationsFor(String symbol) {
        return List.copyOf(evaluations.getOrDefault(symbol, List.of()));
    }

    @Override
    public List<StrategyAssignmentDecision> decisionsFor(String symbol) {
        return List.copyOf(decisions.getOrDefault(symbol, List.of()));
    }
}
