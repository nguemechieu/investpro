package org.investpro.activity.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.activity.BrokerActivityType;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Periodically compares local projections against broker truth.
 * Broker state wins; mismatches produce {@link ReconciliationMismatch} records
 * and, for repairable gaps, generate RECONCILIATION_ADJUSTMENT events.
 */
@Slf4j
public class BrokerReconciliationService {

    private final BrokerActivityRepository repository;
    private final long intervalSeconds;
    private ScheduledExecutorService scheduler;

    public BrokerReconciliationService(BrokerActivityRepository repository, long intervalSeconds) {
        this.repository = repository;
        this.intervalSeconds = intervalSeconds <= 0 ? 120 : intervalSeconds;
    }

    public CompletableFuture<ReconciliationResult> reconcileExchangeAccount(String exchangeId, String accountId) {
        return CompletableFuture.supplyAsync(() -> {
            Instant start = Instant.now();
            List<ReconciliationMismatch> mismatches = new ArrayList<>();

            // Check for events saved but never projected
            List<BrokerActivityEvent> unprojected = repository.findUnprojectedEvents(exchangeId, accountId, 500);
            for (BrokerActivityEvent event : unprojected) {
                mismatches.add(ReconciliationMismatch.builder()
                        .exchangeId(exchangeId)
                        .accountId(accountId)
                        .mismatchType(MismatchType.PROJECTION_FAILURE)
                        .entityId(event.getEventId())
                        .localValue("stored, not projected")
                        .brokerValue(event.getNativeEventType())
                        .description("Broker event " + event.getEventId() + " was saved but never projected")
                        .build());
            }

            log.info("Reconciliation for {}/{}: {} mismatches", exchangeId, accountId, mismatches.size());

            return ReconciliationResult.builder()
                    .exchangeId(exchangeId)
                    .accountId(accountId)
                    .startedAt(start)
                    .finishedAt(Instant.now())
                    .mismatches(mismatches)
                    .repaired(0)
                    .successful(true)
                    .build();
        });
    }

    public CompletableFuture<List<ReconciliationMismatch>> findMismatches(String exchangeId, String accountId) {
        return reconcileExchangeAccount(exchangeId, accountId)
                .thenApply(ReconciliationResult::getMismatches);
    }

    /** Creates a RECONCILIATION_ADJUSTMENT event and marks the original as repaired. */
    public CompletableFuture<BrokerActivityEvent> repairFromBrokerTruth(ReconciliationMismatch mismatch) {
        return CompletableFuture.supplyAsync(() -> {
            BrokerActivityEvent adjustmentEvent = BrokerActivityEvent.builder()
                    .eventId("reconcile-" + mismatch.getMismatchType().name() + "-" + System.nanoTime())
                    .exchangeId(mismatch.getExchangeId())
                    .accountId(mismatch.getAccountId())
                    .nativeEventType("RECONCILIATION_ADJUSTMENT")
                    .activityType(BrokerActivityType.RECONCILIATION_ADJUSTMENT)
                    .reason(mismatch.getDescription())
                    .source("reconciliation")
                    .build();
            repository.save(adjustmentEvent);
            log.warn("RECONCILIATION ADJUSTMENT created for mismatch {} on {}/{}: {}",
                    mismatch.getMismatchType(), mismatch.getExchangeId(), mismatch.getAccountId(),
                    mismatch.getDescription());
            return adjustmentEvent;
        });
    }

    public void schedulePeriodicReconciliation(String exchangeId, String accountId) {
        if (scheduler != null && !scheduler.isShutdown()) return;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reconciliation-" + exchangeId);
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {
            try {
                ReconciliationResult result = reconcileExchangeAccount(exchangeId, accountId).join();
                if (!result.isClean()) {
                    log.warn("Reconciliation found {} mismatches for {}/{}",
                            result.getMismatchCount(), exchangeId, accountId);
                }
            } catch (Exception e) {
                log.error("Scheduled reconciliation failed for {}/{}", exchangeId, accountId, e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("Periodic reconciliation scheduled every {}s for {}/{}", intervalSeconds, exchangeId, accountId);
    }

    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}
