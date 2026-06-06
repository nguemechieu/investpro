package org.investpro.terminal.ai;

import org.investpro.terminal.domain.ExecutionPlan;

import java.util.concurrent.CompletableFuture;

public interface AiRiskScoringClient {
    CompletableFuture<AiReviewResult> scoreRisk(ExecutionPlan executionPlan);
}
