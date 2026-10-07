package org.investpro.core.execution;

import org.investpro.exchange.contracts.OrderExecutionProvider;
import org.investpro.models.trading.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BotOrderJournalTest {
    @TempDir Path directory;

    @Test void recoveryRequiresMatchingBrokerEvidence() {
        var provider = mock(OrderExecutionProvider.class);
        var journal = new BotOrderJournal(directory);
        String intent = journal.begin("account", "BTC/USD", provider);
        Order order = new Order();
        order.setSymbol("ETH-USD");
        order.setStatus("FILLED");
        when(provider.fetchOrder("broker-1")).thenReturn(CompletableFuture.completedFuture(Optional.of(order)));
        assertThrows(IllegalStateException.class,
                () -> journal.reconcileUnknown("account", intent, "broker-1", provider));
        order.setSymbol("BTC-USD");
        journal.reconcileUnknown("account", intent, "broker-1", provider);
        assertNotEquals(intent, journal.begin("account", "ETH/USD", provider));
    }

    @Test void unknownSubmissionSurvivesRestartAndBlocksRetry() {
        var provider = mock(OrderExecutionProvider.class);
        var journal = new BotOrderJournal(directory);
        journal.begin("account", "BTC/USD", provider);
        assertThrows(IllegalStateException.class,
                () -> new BotOrderJournal(directory).begin("account", "BTC/USD", provider));
        verifyNoInteractions(provider);
    }

    @Test void pendingAndPartialOrdersBlockUntilTerminalBrokerConfirmation() {
        var provider = mock(OrderExecutionProvider.class);
        var journal = new BotOrderJournal(directory);
        String intent = journal.begin("account", "BTC/USD", provider);
        journal.submitted("account", intent, "broker-1");
        Order order = new Order();
        order.setStatus("PARTIALLY_FILLED");
        when(provider.fetchOrder("broker-1")).thenReturn(CompletableFuture.completedFuture(Optional.of(order)));
        assertThrows(IllegalStateException.class, () -> journal.begin("account", "ETH/USD", provider));
        order.setStatus("FILLED");
        assertNotEquals(intent, new BotOrderJournal(directory).begin("account", "ETH/USD", provider));
    }

    @Test void blankAcknowledgementDoesNotReleaseSubmissionBarrier() {
        var provider = mock(OrderExecutionProvider.class);
        var journal = new BotOrderJournal(directory);
        String intent = journal.begin("account", "BTC/USD", provider);
        assertThrows(IllegalStateException.class, () -> journal.submitted("account", intent, ""));
        assertThrows(IllegalStateException.class, () -> journal.begin("account", "BTC/USD", provider));
    }

    @Test void failedReconciliationDoesNotReleaseSubmissionBarrier() {
        var provider = mock(OrderExecutionProvider.class);
        var journal = new BotOrderJournal(directory);
        String intent = journal.begin("account", "BTC/USD", provider);
        journal.submitted("account", intent, "broker-1");
        when(provider.fetchOrder("broker-1")).thenReturn(CompletableFuture.failedFuture(new RuntimeException("offline")));
        assertThrows(IllegalStateException.class, () -> journal.begin("account", "BTC/USD", provider));
    }
}
