package org.investpro.terminal.ai;

import org.investpro.terminal.domain.InstrumentId;

import java.util.concurrent.CompletableFuture;

public interface AiRegimeDetectionClient {
    CompletableFuture<AiReviewResult> detectRegime(InstrumentId instrumentId, String timeframe);
}
