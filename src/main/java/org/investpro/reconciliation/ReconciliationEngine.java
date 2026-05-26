package org.investpro.reconciliation;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityRepository;
import org.investpro.activity.BrokerActivityType;
import org.investpro.projection.ProjectionSnapshot;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ReconciliationEngine {
    private final BrokerActivityRepository repository;

    public ReconciliationEngine(BrokerActivityRepository repository) {
        this.repository = repository;
    }

    public ReconciliationResult reconcile(ProjectionSnapshot projected, BrokerTruthSnapshot brokerTruth) {
        List<String> mismatches = new ArrayList<>();
        if (projected == null) {
            mismatches.add("Projected state is unavailable.");
        }
        if (brokerTruth == null) {
            mismatches.add("Broker truth is unavailable.");
        } else if (projected != null) {
            if (projected.orders().orders().size() != brokerTruth.openOrdersCount()) {
                mismatches.add("Order count mismatch: projected=%d broker=%d"
                        .formatted(projected.orders().orders().size(), brokerTruth.openOrdersCount()));
            }
            if (projected.positions().positions().size() != brokerTruth.openPositionsCount()) {
                mismatches.add("Position count mismatch: projected=%d broker=%d"
                        .formatted(projected.positions().positions().size(), brokerTruth.openPositionsCount()));
            }
        }

        if (!mismatches.isEmpty()) {
            emit(BrokerActivityType.RECONCILIATION_MISMATCH, String.join("; ", mismatches));
            return new ReconciliationResult(ReconciliationStatus.MISMATCH_DETECTED, true, mismatches, List.of(), Instant.now());
        }

        return new ReconciliationResult(ReconciliationStatus.OK, false, List.of(), List.of(), Instant.now());
    }

    public ReconciliationResult markRepaired(String reason) {
        emit(BrokerActivityType.RECONCILIATION_REPAIRED, reason);
        return new ReconciliationResult(ReconciliationStatus.REPAIRED, false, List.of(), List.of(reason), Instant.now());
    }

    private void emit(BrokerActivityType type, String reason) {
        if (repository == null) {
            return;
        }
        repository.save(BrokerActivityEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .exchangeId("SYSTEM")
                .activityType(type)
                .eventTime(Instant.now())
                .receivedAt(Instant.now())
                .reason(reason)
                .source("ReconciliationEngine")
                .build());
    }

    public record BrokerTruthSnapshot(int openOrdersCount, int openPositionsCount, Instant fetchedAt) {
        public BrokerTruthSnapshot {
            fetchedAt = fetchedAt == null ? Instant.now() : fetchedAt;
        }
    }
}
