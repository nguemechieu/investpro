package org.investpro.core.agents.risk;

import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface RiskReviewer {
    CompletableFuture<RiskReviewResult> review(RiskReviewRequest request);
}
