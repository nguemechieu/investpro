package org.investpro.terminal.ai;

import org.investpro.terminal.domain.InstrumentId;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AiStrategyRankingClient {
    CompletableFuture<AiReviewResult> rankStrategies(InstrumentId instrumentId, List<String> strategyIds);
}
