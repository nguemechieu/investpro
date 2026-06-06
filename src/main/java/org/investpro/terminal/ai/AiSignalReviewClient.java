package org.investpro.terminal.ai;

import org.investpro.terminal.domain.StrategySignal;

import java.util.concurrent.CompletableFuture;

public interface AiSignalReviewClient {
    CompletableFuture<AiReviewResult> reviewSignal(StrategySignal signal);
}
