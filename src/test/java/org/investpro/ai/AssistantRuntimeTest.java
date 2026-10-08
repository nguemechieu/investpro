package org.investpro.ai;

import org.investpro.core.SystemCore;
import org.investpro.core.TelegramCommandHandler;
import org.investpro.core.TelegramNotifier;
import org.investpro.exchange.Exchange;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class AssistantRuntimeTest {
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void newsCommandIsAvailableWithoutATradingSession() {
        var transport = mock(TelegramNotifier.class);
        new AssistantRuntime(transport);
        var factories = org.mockito.ArgumentCaptor.forClass(java.util.function.Supplier.class);
        verify(transport).setAssistantCommandExecutorFactory(factories.capture());
        var execute = (java.util.function.BiFunction<String, String, String>) factories.getValue().get();
        assertNewsUsage(execute.apply("owner", "/news@InvestProBot"));
    }

    private static void assertNewsUsage(String response) {
        org.junit.jupiter.api.Assertions.assertTrue(response.contains("Usage: /news"));
        org.junit.jupiter.api.Assertions.assertFalse(response.contains("Connect an exchange"));
    }
    @Test @SuppressWarnings({"unchecked", "rawtypes"})
    void capturedExecutorsRejectActionsAfterTheSelectedExchangeChanges() {
        TelegramNotifier transport = mock(TelegramNotifier.class);
        var runtime = new AssistantRuntime(transport);
        var first = mock(SystemCore.class); var second = mock(SystemCore.class);
        when(first.getExchange()).thenReturn(mock(Exchange.class));
        when(second.getExchange()).thenReturn(mock(Exchange.class));
        var commands = mock(TelegramCommandHandler.class);
        when(first.getTelegramCommandHandler()).thenReturn(commands);
        when(transport.getCommandHandler()).thenReturn(commands);
        runtime.attach(first);
        var factories = org.mockito.ArgumentCaptor.forClass(java.util.function.Supplier.class);
        verify(transport).setAssistantCommandExecutorFactory(factories.capture());
        var execute = (java.util.function.BiFunction<String, String, String>) factories.getValue().get();
        runtime.attach(second);
        org.junit.jupiter.api.Assertions.assertTrue(execute.apply("desktop:owner", "/buy BTC/USD 1").contains("session changed"));
        verify(commands, never()).handleCommand(anyString(), anyString());
    }
    @Test void assistantOwnershipSurvivesTradingStopAndExchangeDetach() {
        TelegramNotifier transport = mock(TelegramNotifier.class);
        when(transport.isEnabled()).thenReturn(true);
        var runtime = new AssistantRuntime(transport);
        SystemCore core = mock(SystemCore.class);
        when(core.getExchange()).thenReturn(mock(Exchange.class));
        var commands = mock(TelegramCommandHandler.class);
        when(core.getTelegramCommandHandler()).thenReturn(commands);
        runtime.start();
        runtime.attach(core);
        core.stop();
        runtime.ask("desktop", "Explain risk", _ -> {});
        runtime.attach(null);
        runtime.ask("desktop", "Explain bonds", _ -> {});
        verify(transport).startPolling();
        verify(transport).setCommandHandler(commands);
        verify(transport, never()).stopPolling();
        verify(transport, never()).close();
        runtime.close();
        verify(transport).close();
    }
}
