package org.investpro.activity.coinbase;

import org.investpro.activity.AbstractExchangeActivityService;
import org.investpro.activity.ActivityCheckpointRepository;
import org.investpro.activity.ActivityProjectionService;
import org.investpro.activity.ActivitySyncResult;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.exchange.Coinbase;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class CoinbaseActivityService extends AbstractExchangeActivityService {
    private final Coinbase coinbase;

    public CoinbaseActivityService(
            Coinbase coinbase,
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            ActivityProjectionService projectionService
    ) {
        super(CoinbaseActivityMapper.EXCHANGE_ID, null, activityRepository, checkpointRepository, projectionService);
        this.coinbase = coinbase;
    }

    @Override
    public CompletableFuture<ActivitySyncResult> syncActivitySince(String cursor) {
        Instant startedAt = Instant.now();
        return CompletableFuture.supplyAsync(() -> ActivitySyncResult.builder()
                .exchangeId(exchangeId)
                .accountId(accountId)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .previousCursor(cursor)
                .latestCursor(cursor)
                .eventsFetched(0)
                .eventsProcessed(0)
                .successful(true)
                .warning("Coinbase activity service registered; Advanced Trade orders/fills polling is staged behind the normalized mapper")
                .build());
    }

    @Override
    public boolean supportsRealtimeActivityStream() {
        return coinbase != null && Boolean.TRUE.equals(coinbase.isConnected());
    }
}
