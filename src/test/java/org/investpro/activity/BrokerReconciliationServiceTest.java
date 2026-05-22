package org.investpro.activity;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;
import org.investpro.activity.InMemoryBrokerActivityRepository;
import org.investpro.activity.PreferencesActivityCheckpointRepository;
import org.investpro.activity.reconciliation.BrokerReconciliationService;
import org.investpro.activity.reconciliation.ReconciliationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for BrokerReconciliationService.
 */
class BrokerReconciliationServiceTest {

    private InMemoryBrokerActivityRepository repo;
    private BrokerReconciliationService reconciler;

    @BeforeEach
    void setUp() {
        repo = new InMemoryBrokerActivityRepository();
        reconciler = new BrokerReconciliationService(repo, null, null);
    }

    @Test
    void cleanWhenNoUnprojectedEvents() throws ExecutionException, InterruptedException {
        ReconciliationResult result = reconciler
                .reconcileExchangeAccount("OANDA", "acct-001")
                .get();
        assertThat(result.isClean()).isTrue();
        assertThat(result.getMismatchCount()).isZero();
    }

    @Test
    void mismatchWhenUnprojectedEventsExist() throws ExecutionException, InterruptedException {
        BrokerActivityEvent event = BrokerActivityEvent.builder()
                .eventId("txn-999")
                .exchangeId("OANDA")
                .accountId("acct-001")
                .activityType(BrokerActivityType.ORDER_FILLED)
                .eventTime(Instant.now())
                .receivedAt(Instant.now())
                .build();
        repo.save(event);

        ReconciliationResult result = reconciler
                .reconcileExchangeAccount("OANDA", "acct-001")
                .get();
        assertThat(result.isClean()).isFalse();
        assertThat(result.getMismatchCount()).isGreaterThan(0);
    }

    @Test
    void cleanAfterMarkingEventsProjected() throws ExecutionException, InterruptedException {
        BrokerActivityEvent event = BrokerActivityEvent.builder()
                .eventId("txn-777")
                .exchangeId("OANDA")
                .accountId("acct-001")
                .activityType(BrokerActivityType.TRADE_CLOSED)
                .eventTime(Instant.now())
                .receivedAt(Instant.now())
                .build();
        repo.save(event);
        repo.markProjected("OANDA", "txn-777", Instant.now());

        ReconciliationResult result = reconciler
                .reconcileExchangeAccount("OANDA", "acct-001")
                .get();
        assertThat(result.isClean()).isTrue();
    }
}
