package org.investpro.strategy.auto;

import org.investpro.config.AppConfig;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyDefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoStrategyLab {

    private final List<StrategyCandidateGenerator> generators;
    private final StrategyEvaluationEngine evaluationEngine;
    private final StrategyRankingEngine rankingEngine;
    private final StrategyAssignmentEngine assignmentEngine;
    private final StrategyMutationEngine mutationEngine;
    private final StrategyMemoryRepository memoryRepository;
    private final ExecutorService executorService;

    public AutoStrategyLab() {
        this(
                List.of(new RuleBasedStrategyCandidateGenerator()),
                new StrategyEvaluationEngine(),
                new StrategyRankingEngine(),
                new StrategyAssignmentEngine(),
                new StrategyMutationEngine(),
                new FileStrategyMemoryRepository(),
                Executors.newFixedThreadPool(2, runnable -> {
                    Thread thread = new Thread(runnable, "auto-strategy-lab");
                    thread.setDaemon(true);
                    return thread;
                }));
    }

    public AutoStrategyLab(
            List<StrategyCandidateGenerator> generators,
            StrategyEvaluationEngine evaluationEngine,
            StrategyRankingEngine rankingEngine,
            StrategyAssignmentEngine assignmentEngine,
            StrategyMutationEngine mutationEngine,
            StrategyMemoryRepository memoryRepository,
            ExecutorService executorService) {
        this.generators = generators == null ? List.of() : List.copyOf(generators);
        this.evaluationEngine = evaluationEngine;
        this.rankingEngine = rankingEngine;
        this.assignmentEngine = assignmentEngine;
        this.mutationEngine = mutationEngine == null ? new StrategyMutationEngine() : mutationEngine;
        this.memoryRepository = memoryRepository;
        this.executorService = executorService;
    }

    public CompletableFuture<List<StrategyCandidate>> generateCandidates(StrategyGenerationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            if (!AppConfig.getBoolean("autoStrategy.enabled", true)) {
                return List.of();
            }
            return generateCandidatesSync(context, null);
        }, executorService);
    }

    public CompletableFuture<List<StrategyCandidate>> generateCandidates(
            StrategyGenerationContext context,
            StrategyDefinition currentStrategyDefinition) {
        return CompletableFuture.supplyAsync(() -> {
            if (!AppConfig.getBoolean("autoStrategy.enabled", true)) {
                return List.of();
            }
            return generateCandidatesSync(context, currentStrategyDefinition);
        }, executorService);
    }

    public CompletableFuture<List<StrategyEvaluationResult>> evaluateCandidates(
            List<StrategyCandidate> candidates,
            StrategyGenerationContext context) {
        return CompletableFuture.supplyAsync(() -> {
            if (candidates == null || candidates.isEmpty()) {
                return List.of();
            }
            return evaluateCandidatesSync(candidates, context);
        }, executorService);
    }

    public StrategyAssignmentDecision assignBest(
            List<StrategyEvaluationResult> results,
            StrategyAssignment currentAssignment,
            StrategyGenerationContext context,
            boolean userApproved) {
        StrategyAssignmentDecision decision = assignmentEngine.decide(
                rankingEngine.best(results).orElse(null),
                currentAssignment,
                context,
                userApproved);
        memoryRepository.saveDecision(decision);
        return decision;
    }

    public CompletableFuture<AutoStrategyRunResult> runImprovementCycle(
            StrategyGenerationContext context,
            StrategyAssignment currentAssignment,
            StrategyDefinition currentStrategyDefinition,
            boolean userApproved) {
        Instant startedAt = Instant.now();
        return CompletableFuture.supplyAsync(() -> {
            if (Thread.currentThread().isInterrupted()) {
                return new AutoStrategyRunResult(context, List.of(), List.of(), null, true, startedAt, Instant.now());
            }
            List<StrategyCandidate> candidates = generateCandidatesSync(context, currentStrategyDefinition);
            if (Thread.currentThread().isInterrupted()) {
                return new AutoStrategyRunResult(context, candidates, List.of(), null, true, startedAt, Instant.now());
            }
            List<StrategyEvaluationResult> evaluations = evaluateCandidatesSync(candidates, context);
            StrategyAssignmentDecision decision = assignBest(evaluations, currentAssignment, context, userApproved);
            return new AutoStrategyRunResult(context, candidates, evaluations, decision, false, startedAt, Instant.now());
        }, executorService);
    }

    private List<StrategyCandidate> generateCandidatesSync(
            StrategyGenerationContext context,
            StrategyDefinition currentStrategyDefinition) {
        int limit = Math.max(1, AppConfig.getInt("autoStrategy.maxCandidatesPerSymbol", 20));
        List<StrategyCandidate> candidates = new ArrayList<>();
        for (StrategyCandidateGenerator generator : generators) {
            candidates.addAll(generator.generateCandidates(context));
            if (candidates.size() >= limit) {
                break;
            }
        }
        if (candidates.size() < limit && currentStrategyDefinition != null) {
            candidates.addAll(mutationEngine.mutate(currentStrategyDefinition, context).stream()
                    .limit(limit - candidates.size())
                    .toList());
        }
        List<StrategyCandidate> limited = candidates.stream().limit(limit).toList();
        limited.forEach(memoryRepository::saveCandidate);
        return limited;
    }

    private List<StrategyEvaluationResult> evaluateCandidatesSync(
            List<StrategyCandidate> candidates,
            StrategyGenerationContext context) {
        List<StrategyEvaluationResult> results = candidates.stream()
                .map(candidate -> evaluationEngine.evaluate(candidate, context))
                .peek(memoryRepository::saveEvaluation)
                .toList();
        return rankingEngine.rank(results);
    }

    public StrategyMemoryRepository memoryRepository() {
        return memoryRepository;
    }

    public void shutdown() {
        executorService.shutdownNow();
    }
}
