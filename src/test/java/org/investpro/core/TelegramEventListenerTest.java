package org.investpro.core;

import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramEventListenerTest {
    @Test void directlyDeliveredErrorsAreNotSentAgainByEventListener() {
        var notifier = mock(TelegramNotifier.class);
        when(notifier.isEnabled()).thenReturn(true);
        when(notifier.hasTargetChat()).thenReturn(true);
        var listener = new TelegramEventListener(mock(AgentEventBus.class), notifier);
        listener.accept(new AgentEvent(AgentEvent.ERROR, "Coinbase", "Endpoint unavailable", Instant.now(),
                Map.of("notifyTelegram", false)));
        verify(notifier, never()).sendMarkdown(anyString());
        verify(notifier, never()).send(anyString());
    }
    @Test void errorUsesActualSourceRootCauseAndErrorSeverity() {
        var notifier = mock(TelegramNotifier.class);
        when(notifier.isEnabled()).thenReturn(true);
        when(notifier.hasTargetChat()).thenReturn(true);
        var listener = new TelegramEventListener(mock(AgentEventBus.class), notifier);
        listener.accept(new AgentEvent(AgentEvent.ERROR, "Coinbase",
                new CompletionException(new IllegalStateException("Market endpoint unavailable")), Instant.now(), Map.of()));
        var message = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendMarkdown(message.capture());
        assertTrue(message.getValue().contains("Source: Coinbase"));
        assertTrue(message.getValue().contains("Error: IllegalStateException"));
        assertTrue(message.getValue().contains("Details: Market endpoint unavailable"));
        assertTrue(message.getValue().startsWith("🔴"));
        assertFalse(message.getValue().contains("N/A"));
        verify(notifier, never()).send(anyString());
    }

    @Test void errorPreservesRecordedSafeDetails() {
        var notifier = mock(TelegramNotifier.class);
        when(notifier.isEnabled()).thenReturn(true);
        when(notifier.hasTargetChat()).thenReturn(true);
        var listener = new TelegramEventListener(mock(AgentEventBus.class), notifier);
        listener.accept(new AgentEvent(AgentEvent.ERROR, "Coinbase", new RuntimeException(), Instant.now(),
                Map.of("severity", "ERROR", "errorType", "HTTP error", "error", "Coinbase HTTP 403: Trade permission required")));
        var message = ArgumentCaptor.forClass(String.class);
        verify(notifier).sendMarkdown(message.capture());
        assertTrue(message.getValue().contains("Error: HTTP error"));
        assertTrue(message.getValue().contains("Trade permission required"));
    }
}
