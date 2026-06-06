package org.investpro.terminal.ai;

import org.investpro.terminal.domain.BacktestResult;

import java.util.concurrent.CompletableFuture;

public interface AiBacktestReviewClient {
    CompletableFuture<AiReviewResult> reviewBacktest(BacktestResult backtestResult);
}
