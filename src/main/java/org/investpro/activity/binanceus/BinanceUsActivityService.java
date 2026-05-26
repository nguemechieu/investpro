package org.investpro.activity.binanceus;

import org.investpro.activity.AbstractExchangeActivityService;
import org.investpro.activity.ActivityCheckpointRepository;
import org.investpro.activity.ActivityProjectionService;
import org.investpro.activity.ActivitySyncResult;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.exchange.BinanceUs;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

public class BinanceUsActivityService extends AbstractExchangeActivityService {
    private final BinanceUs binanceUs;

    public BinanceUsActivityService(
            BinanceUs binanceUs,
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            ActivityProjectionService projectionService
    ) {
        super(BinanceUsActivityMapper.EXCHANGE_ID, null, activityRepository, checkpointRepository, projectionService);
        this.binanceUs = binanceUs;
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
                .warning("BinanceUS activity service registered; REST/user-data-stream wiring is staged behind the normalized mapper")
                .build());
    }

    @Override
    public boolean supportsRealtimeActivityStream() {
        return binanceUs != null && Boolean.TRUE.equals(binanceUs.isConnected());
    }
}
