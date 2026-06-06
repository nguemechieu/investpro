package org.investpro.terminal.ai;

import org.investpro.terminal.domain.MarketTick;

import java.util.concurrent.CompletableFuture;

public interface AiAnomalyDetectionClient {
    CompletableFuture<AiReviewResult> detectAnomaly(MarketTick tick);
}
