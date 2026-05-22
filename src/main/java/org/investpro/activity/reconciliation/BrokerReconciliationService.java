package org.investpro.activity.reconciliation;

import lombok.extern.slf4j.Slf4j;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Slf4j
public class BrokerReconciliationService {

    private final BrokerActivityRepository repo;
    private final long intervalSeconds;
    private final ScheduledExecutorService scheduler;

    public BrokerReconciliationService(BrokerActivityRepository repo, long intervalSeconds) {
        this.repo = repo;
        this.intervalSeconds = intervalSeconds;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "broker-reconciliation");
            t.setDaemon(true);
            return t;
        });
    }

    public ReconciliationResult reconcileExchangeAccount(
            String exchangeId, String accountId, List<BrokerActivityEvent> brokerTruth) {
        Instant reconciledAt = Instant.now();
        ReconciliationResult.ReconciliationResultBuilder result = ReconciliationResult.builder()
                .exchangeId(exchangeId)
                .accountId(accountId)
                .reconciledAt(reconciledAt);

        if (brokerTruth == null || brokerTruth.isEmpty()) {
            return result.eventsChecked(0).successful(true).build();
        }

        List<BrokerActivityEvent> local = repo.findByTimeRange(exchangeId, accountId,
                brokerTruth.stream().map(BrokerActivityEvent::getEventTime).min(Instant::compareTo).orElse(Instant.EPOCH),
                brokerTruth.stream().map(BrokerActivityEvent::getEventTime).max(Instant::compareTo).orElse(Instant.now()));

        Set<String> localIds = new HashSet<>();
        for (BrokerActivityEvent e : local) {
            if (e.getEventId() != null) localIds.add(e.getEventId());
        }

        Set<String> brokerIds = new HashSet<>();
        for (BrokerActivityEvent e : brokerTruth) {
            if (e.getEventId() != null) brokerIds.add(e.getEventId());
        }

        for (BrokerActivityEvent brokerEvent : brokerTruth) {
            String id = brokerEvent.getEventId();
            if (id == null) continue;
            if (!localIds.contains(id)) {
                result.mismatch(ReconciliationMismatch.builder()
                        .exchangeId(exchangeId)
                        .eventId(id)
                        .type(MismatchType.MISSING_IN_LOCAL)
                        .brokerValue(id)
                        .description("Event present at broker but not in local store")
                        .build());
            }
        }

        for (BrokerActivityEvent localEvent : local) {
            String id = localEvent.getEventId();
            if (id == null) continue;
            if (!brokerIds.contains(id)) {
                result.mismatch(ReconciliationMismatch.builder()
                        .exchangeId(exchangeId)
                        .eventId(id)
                        .type(MismatchType.MISSING_IN_BROKER)
                        .localValue(id)
                        .description("Event present locally but not returned by broker")
                        .build());
            }
        }

        ReconciliationResult built = result
                .eventsChecked(brokerTruth.size())
                .successful(true)
                .build();
        log.info("Reconciliation for exchange={} account={}: checked={} mismatches={}",
                exchangeId, accountId, built.getEventsChecked(), built.getMismatchCount());
        return built;
    }

    public void schedulePeriodicReconciliation(
            String exchangeId, String accountId,
            Supplier<List<BrokerActivityEvent>> brokerFetch) {
        scheduler.scheduleAtFixedRate(() -> {
            try {
                List<BrokerActivityEvent> truth = brokerFetch.get();
                ReconciliationResult res = reconcileExchangeAccount(exchangeId, accountId, truth);
                if (!res.isClean()) {
                    log.warn("Periodic reconciliation found {} mismatches for exchange={} account={}",
                            res.getMismatchCount(), exchangeId, accountId);
                }
            } catch (Exception e) {
                log.error("Periodic reconciliation failed for exchange={} account={}", exchangeId, accountId, e);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        log.info("Scheduled periodic reconciliation every {}s for exchange={} account={}",
                intervalSeconds, exchangeId, accountId);
    }
}
