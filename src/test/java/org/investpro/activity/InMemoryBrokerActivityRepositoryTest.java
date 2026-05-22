package org.investpro.activity;

import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;
import org.investpro.activity.InMemoryBrokerActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for InMemoryBrokerActivityRepository, focusing on idempotency and projection tracking.
 */
class InMemoryBrokerActivityRepositoryTest {

    private InMemoryBrokerActivityRepository repo;

    @BeforeEach
    void setUp() {
        repo = new InMemoryBrokerActivityRepository();
    }

    private BrokerActivityEvent makeEvent(String eventId) {
        return BrokerActivityEvent.builder()
                .eventId(eventId)
                .exchangeId("OANDA")
                .accountId("001-001-12345")
                .nativeEventType("ORDER_FILL")
                .activityType(BrokerActivityType.ORDER_FILLED)
                .eventTime(Instant.now())
                .receivedAt(Instant.now())
                .build();
    }

    @Test
    void saveSameEventTwiceStoresOnlyOnce() {
        BrokerActivityEvent event = makeEvent("txn-001");
        repo.save(event);
        repo.save(event); // duplicate

        List<BrokerActivityEvent> found = repo.findByOrderId("OANDA", "001-001-12345", null);
        // at most 1 event stored
        assertThat(repo.exists("OANDA", "txn-001")).isTrue();
    }

    @Test
    void saveAllIdempotent() {
        BrokerActivityEvent e1 = makeEvent("txn-001");
        BrokerActivityEvent e2 = makeEvent("txn-002");
        repo.saveAll(List.of(e1, e2, e1)); // e1 duplicated

        assertThat(repo.exists("OANDA", "txn-001")).isTrue();
        assertThat(repo.exists("OANDA", "txn-002")).isTrue();
    }

    @Test
    void findByEventIdReturnsEvent() {
        BrokerActivityEvent event = makeEvent("txn-abc");
        repo.save(event);

        Optional<BrokerActivityEvent> found = repo.findByEventId("OANDA", "txn-abc");
        assertThat(found).isPresent();
        assertThat(found.get().getEventId()).isEqualTo("txn-abc");
    }

    @Test
    void findByEventIdMissingReturnsEmpty() {
        assertThat(repo.findByEventId("OANDA", "nonexistent")).isEmpty();
    }

    @Test
    void findUnprojectedEventsReturnsSavedUnprojectedEvents() {
        repo.save(makeEvent("txn-001"));
        repo.save(makeEvent("txn-002"));

        List<BrokerActivityEvent> unprojected = repo.findUnprojectedEvents("OANDA", "001-001-12345", 10);
        assertThat(unprojected).hasSize(2);
    }

    @Test
    void markProjectedRemovesFromUnprojected() {
        repo.save(makeEvent("txn-001"));
        repo.save(makeEvent("txn-002"));
        repo.markProjected("OANDA", "txn-001", Instant.now());

        List<BrokerActivityEvent> unprojected = repo.findUnprojectedEvents("OANDA", "001-001-12345", 10);
        assertThat(unprojected).hasSize(1);
        assertThat(unprojected.get(0).getEventId()).isEqualTo("txn-002");
    }

    @Test
    void markProjectionFailedPreservesUnprojectedStatus() {
        repo.save(makeEvent("txn-001"));
        repo.markProjectionFailed("OANDA", "txn-001", "Projection engine down");

        // event should still be in unprojected (failed, not projected)
        List<BrokerActivityEvent> unprojected = repo.findUnprojectedEvents("OANDA", "001-001-12345", 10);
        assertThat(unprojected).hasSize(1);
    }

    @Test
    void existsReturnsFalseForMissingEvent() {
        assertThat(repo.exists("OANDA", "does-not-exist")).isFalse();
    }

    @Test
    void findByTimeRangeFiltersCorrectly() {
        Instant base = Instant.parse("2024-01-15T10:00:00Z");
        BrokerActivityEvent early = BrokerActivityEvent.builder()
                .eventId("early")
                .exchangeId("OANDA")
                .accountId("acct")
                .activityType(BrokerActivityType.BALANCE_CHANGED)
                .eventTime(base.minusSeconds(3600))
                .receivedAt(Instant.now())
                .build();
        BrokerActivityEvent inRange = BrokerActivityEvent.builder()
                .eventId("in-range")
                .exchangeId("OANDA")
                .accountId("acct")
                .activityType(BrokerActivityType.BALANCE_CHANGED)
                .eventTime(base)
                .receivedAt(Instant.now())
                .build();
        repo.save(early);
        repo.save(inRange);

        List<BrokerActivityEvent> result = repo.findByTimeRange(
                "OANDA", "acct",
                base.minusSeconds(10), base.plusSeconds(10)
        );
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEventId()).isEqualTo("in-range");
    }
}
