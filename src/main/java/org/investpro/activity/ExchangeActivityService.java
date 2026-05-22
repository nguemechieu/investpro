package org.investpro.activity;

import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public interface ExchangeActivityService {
    CompletableFuture<ActivitySyncResult> syncRecentActivity();
    CompletableFuture<ActivitySyncResult> syncActivitySince(String cursor);
    CompletableFuture<List<BrokerActivityEvent>> getActivityHistory(Instant from, Instant to);
    CompletableFuture<Optional<String>> getLatestCursor();
    boolean supportsRealtimeActivityStream();
    CompletableFuture<Void> startRealtimeActivityStream();
    CompletableFuture<Void> stopRealtimeActivityStream();

    default CompletableFuture<List<BrokerActivityEvent>> getActivityHistory(
            TradePair pair, Instant from, Instant to) {
        return getActivityHistory(from, to)
                .thenApply(events -> events.stream()
                        .filter(event -> pair != null && pair.equals(event.getTradePair()))
                        .toList());
    }
}
