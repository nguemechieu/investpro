package org.investpro.activity.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Slf4j
public class BrokerReconciliationService {
    private final BrokerActivityRepository activityRepository;
    private final long intervalSeconds;
    private final ScheduledExecutorService scheduler;

    public BrokerReconciliationService(BrokerActivityRepository activityRepository, long intervalSeconds) {
        this.activityRepository = activityRepository;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "broker-reconciliation");
            t.setDaemon(true);
            return t;
        });
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::runReconciliationSweep, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("BrokerReconciliationService started (interval={}s)", intervalSeconds);
    }

    public void stop() {
        scheduler.shutdownNow();
        log.info("BrokerReconciliationService stopped");
    }

    public ReconciliationResult reconcile(String exchangeId, String accountId) {
        List<BrokerActivityEvent> unprojected = activityRepository.findUnprojectedEvents(exchangeId, accountId, 500);
        List<ReconciliationMismatch> mismatches = new ArrayList<>();
        for (BrokerActivityEvent event : unprojected) {
            if (event.isErrorEvent()) {
                mismatches.add(ReconciliationMismatch.builder()
                        .type(MismatchType.STATUS_MISMATCH)
                        .exchangeId(event.getExchangeId())
                        .eventId(event.getEventId())
                        .orderId(event.getOrderId())
                        .tradeId(event.getTradeId())
                        .detail("Unprojected error event: " + event.getActivityType())
                        .localEvent(event)
                        .build());
            }
        }
        ReconciliationResult result = ReconciliationResult.builder()
                .exchangeId(exchangeId)
                .accountId(accountId)
                .eventsChecked(unprojected.size())
                .mismatches(mismatches)
                .build();
        if (!result.isClean()) {
            log.warn("Reconciliation found {} mismatches for {}:{}", result.getMismatchCount(), exchangeId, accountId);
        }
        return result;
    }

    private void runReconciliationSweep() {
        log.debug("Running scheduled broker reconciliation sweep");
    }
}
