package org.investpro.activity;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerActivityRepositoryTest {

    @Test
    void duplicateEventDoesNotCreateDuplicateActivityRows() {
        InMemoryBrokerActivityRepository repository = new InMemoryBrokerActivityRepository();
        BrokerActivityEvent event = BrokerActivityEvent.builder()
                .exchangeId("OANDA")
                .eventId("123")
                .activityType(BrokerActivityType.ORDER_FILLED)
                .eventTime(Instant.parse("2026-05-22T12:00:00Z"))
                .build();

        repository.save(event);
        repository.save(event);

        assertTrue(repository.findByEventId("OANDA", "123").isPresent());
        assertEquals(1, repository.findByTimeRange(
                "OANDA",
                Instant.parse("2026-05-22T00:00:00Z"),
                Instant.parse("2026-05-23T00:00:00Z")).size());
    }

    @Test
    void projectionServiceSkipsDuplicateEvents() {
        InMemoryBrokerActivityRepository repository = new InMemoryBrokerActivityRepository();
        DefaultActivityProjectionService projectionService = new DefaultActivityProjectionService(repository);
        BrokerActivityEvent event = BrokerActivityEvent.builder()
                .exchangeId("COINBASE")
                .eventId("fill-1")
                .activityType(BrokerActivityType.ORDER_FILLED)
                .build();

        ProjectionResult first = projectionService.apply(event);
        ProjectionResult second = projectionService.apply(event);

        assertTrue(first.isApplied());
        assertTrue(second.isDuplicate());
    }
}
