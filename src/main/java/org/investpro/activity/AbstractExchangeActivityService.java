package org.investpro.activity;

import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

@Slf4j
public abstract class AbstractExchangeActivityService implements ExchangeActivityService {
    protected final String exchangeId;
    protected final String accountId;
    protected final BrokerActivityRepository activityRepository;
    protected final ActivityCheckpointRepository checkpointRepository;
    protected final ActivityProjectionService projectionService;

    protected AbstractExchangeActivityService(
            String exchangeId,
            String accountId,
            BrokerActivityRepository activityRepository,
            ActivityCheckpointRepository checkpointRepository,
            ActivityProjectionService projectionService
    ) {
        this.exchangeId = exchangeId;
        this.accountId = accountId;
        this.activityRepository = activityRepository;
        this.checkpointRepository = checkpointRepository;
        this.projectionService = projectionService;
    }

    @Override
    public CompletableFuture<ActivitySyncResult> syncRecentActivity() {
        return syncActivitySince(checkpointRepository.getLastCursor(exchangeId, accountId).orElse(null));
    }

    @Override
    public CompletableFuture<List<BrokerActivityEvent>> getActivityHistory(Instant from, Instant to) {
        return CompletableFuture.supplyAsync(() -> activityRepository.findByTimeRange(exchangeId, from, to));
    }

    @Override
    public CompletableFuture<Optional<String>> getLatestCursor() {
        return CompletableFuture.supplyAsync(() -> checkpointRepository.getLastCursor(exchangeId, accountId));
    }

    @Override
    public CompletableFuture<Void> startRealtimeActivityStream() {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> stopRealtimeActivityStream() {
        return CompletableFuture.completedFuture(null);
    }

    protected ActivitySyncResult processFetchedEvents(String previousCursor, List<BrokerActivityEvent> events, Instant startedAt) {
        final ProjectionBatchResult projection;
        if (projectionService == null) {
            activityRepository.saveAll(events == null ? List.of() : events);
            int count = events == null ? 0 : events.size();
            projection = ProjectionBatchResult.builder()
                    .attempted(count)
                    .applied(count)
                    .skipped(0)
                    .failed(0)
                    .build();
        } else {
            projection = projectionService.applyAll(events);
        }
        String latestCursor = latestCursor(events).orElse(previousCursor);
        ActivitySyncResult.ActivitySyncResultBuilder result = ActivitySyncResult.builder()
                .exchangeId(exchangeId)
                .accountId(accountId)
                .startedAt(startedAt)
                .finishedAt(Instant.now())
                .previousCursor(previousCursor)
                .latestCursor(latestCursor)
                .eventsFetched(events == null ? 0 : events.size())
                .eventsProcessed(projection.getApplied())
                .eventsSkipped(projection.getSkipped())
                .eventsFailed(projection.getFailed())
                .successful(projection.successful());
        if (projection.successful()) {
            if (latestCursor != null && !latestCursor.isBlank()) {
                checkpointRepository.saveLastCursor(exchangeId, accountId, latestCursor);
            }
            checkpointRepository.saveLastSyncTime(exchangeId, accountId, Instant.now());
        } else {
            result.warning("Checkpoint not advanced because one or more broker activity events failed projection");
        }
        return result.build();
    }

    private Optional<String> latestCursor(List<BrokerActivityEvent> events) {
        if (events == null || events.isEmpty()) {
            return Optional.empty();
        }
        return events.stream()
                .map(BrokerActivityEvent::getCursor)
                .filter(cursor -> cursor != null && !cursor.isBlank())
                .reduce((first, second) -> second);
    }
}
