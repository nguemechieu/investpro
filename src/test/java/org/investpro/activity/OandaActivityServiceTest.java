package org.investpro.activity;

import org.investpro.activity.ActivitySyncResult;
import org.investpro.activity.BrokerActivityEvent;
import org.investpro.activity.BrokerActivityType;
import org.investpro.activity.InMemoryBrokerActivityRepository;
import org.investpro.activity.PreferencesActivityCheckpointRepository;
import org.investpro.activity.oanda.OandaActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for OandaActivityService behaviour when HTTP client is not configured.
 */
class OandaActivityServiceTest {

    private InMemoryBrokerActivityRepository repo;
    private PreferencesActivityCheckpointRepository checkpointRepo;
    private OandaActivityService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryBrokerActivityRepository();
        checkpointRepo = new PreferencesActivityCheckpointRepository();
        // No http client / apiKey — should return graceful warning
        service = new OandaActivityService(
                null, "acct-001", null, null, null,
                repo, checkpointRepo, null);
    }

    @Test
    void syncReturnsFalseWhenNotConfigured() throws ExecutionException, InterruptedException {
        ActivitySyncResult result = service.syncRecentActivity().get();
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getWarnings()).isNotEmpty();
    }

    @Test
    void acceptMappedTransactionsSavesEvents() {
        BrokerActivityEvent event = BrokerActivityEvent.builder()
                .eventId("txn-111")
                .exchangeId("OANDA")
                .accountId("acct-001")
                .activityType(BrokerActivityType.ORDER_FILLED)
                .eventTime(java.time.Instant.now())
                .receivedAt(java.time.Instant.now())
                .build();

        ActivitySyncResult result = service.acceptMappedTransactions(List.of(event), null);
        // Projection may fail (no projectionService), but the event should be saved
        assertThat(repo.exists("OANDA", "txn-111")).isTrue();
    }

    @Test
    void supportsRealtimeReturnsFalseWhenOandaIsNull() {
        assertThat(service.supportsRealtimeActivityStream()).isFalse();
    }
}
