package org.investpro.ai;

import org.investpro.core.SystemCore;
import org.investpro.core.TelegramCommandHandler;
import org.investpro.core.TelegramNotifier;
import org.investpro.exchange.Exchange;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class AssistantRuntimeTest {
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
