package org.investpro.core;

import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.core.bot.SmartBot;
import org.investpro.exchange.coinbase.CoinbaseRestRateLimiter;
import org.investpro.exchange.infrastructure.ExchangeStreamConsumer;
import org.investpro.monitoring.SystemEventRecorder;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletionException;

import static org.mockito.Mockito.*;

class SystemCoreStreamErrorsTest {
    @Test void localLimiterDeferralDoesNotBecomeExecutionFailureOrTelegramAlert() throws Exception {
        var core = mock(SystemCore.class, CALLS_REAL_METHODS);
        var bot = mock(SmartBot.class);
        var events = mock(AgentEventBus.class);
        var recorder = mock(SystemEventRecorder.class);
        when(bot.getEventBus()).thenReturn(events);
        field(core, "smartBot", bot);
        field(core, "systemEventRecorder", recorder);
        consumer(core).onError("Coinbase", new CompletionException(
                new CoinbaseRestRateLimiter.RateLimitBlockedException("product-cooldown", 60_000)));
        verifyNoInteractions(events, recorder);
    }

    @Test void runningBotSendsOneDetailedTelegramAlertThroughEventListener() throws Exception {
        var core = mock(SystemCore.class, CALLS_REAL_METHODS);
        var bot = mock(SmartBot.class);
        var events = mock(AgentEventBus.class);
        var recorder = mock(SystemEventRecorder.class);
        var notifier = mock(TelegramNotifier.class);
        when(notifier.isEnabled()).thenReturn(true);
        when(notifier.hasTargetChat()).thenReturn(true);
        var listener = spy(new TelegramEventListener(events, notifier));
        doReturn(true).when(listener).isListening();
        when(bot.getEventBus()).thenReturn(events);
        field(core, "smartBot", bot);
        field(core, "systemEventRecorder", recorder);
        field(core, "telegramNotifier", notifier);
        field(core, "telegramEventListener", listener);
        doAnswer(call -> { listener.accept(call.getArgument(0)); return null; }).when(events).publish(any(AgentEvent.class));
        consumer(core).onError("Coinbase", new IllegalStateException("HTTP 503: service unavailable"));
        // Wait for the IO notification branch before verifying no second Telegram send.
        org.investpro.core.concurrent.AppExecutors.submit(org.investpro.core.concurrent.AppExecutors.IO, () -> null).get();
        verify(notifier, times(1)).sendMarkdown(contains("Source: Coinbase"));
        verify(notifier, never()).send(anyString());
        verify(recorder).recordExecutionError("HTTP 503: service unavailable");
    }

    private ExchangeStreamConsumer consumer(SystemCore core) throws Exception {
        var method = SystemCore.class.getDeclaredMethod("createAgentStreamConsumer");
        method.setAccessible(true); return (ExchangeStreamConsumer) method.invoke(core);
    }
    private void field(SystemCore core, String name, Object value) throws Exception {
        var field = SystemCore.class.getDeclaredField(name);
        field.setAccessible(true); field.set(core, value);
    }
}
