package org.investpro.activities.binanceus;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activities.AbstractExchangeActivityService;
import org.investpro.activities.ActivityCheckpointRepository;
import org.investpro.activities.ActivityProjectionService;
import org.investpro.activities.ActivitySyncResult;
import org.investpro.activities.BrokerActivityRepository;
import org.investpro.exchange.binanceus.BinanceUs;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
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
        return CompletableFuture.supplyAsync(() -> {
            log.info("BinanceUS activity sync requested (cursor={})", cursor);
            return ActivitySyncResult.builder()
                    .exchangeId(exchangeId)
                    .accountId(accountId)
                    .startedAt(startedAt)
                    .finishedAt(Instant.now())
                    .previousCursor(cursor)
                    .latestCursor(cursor)
                    .eventsFetched(0)
                    .eventsProcessed(0)
                    .successful(true)
                    .warning("BinanceUS activity sync is not yet wired to REST API")
                    .build();
        });
    }

    @Override
    public boolean supportsRealtimeActivityStream() {
        return false;
    }
}
