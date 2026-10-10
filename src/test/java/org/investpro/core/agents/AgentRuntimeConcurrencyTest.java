package org.investpro.core.agents;

import org.junit.jupiter.api.Test;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class AgentRuntimeConcurrencyTest {
    @Test void blockedStrategyDoesNotBlockRiskOrCallingThreadAndRuntimeRestarts() throws Exception {
        var runtime = new AgentRuntime();
        var context = new AgentContext();
        context.setEventBus(new AgentEventBus());
        var started = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var riskDone = new LinkedBlockingQueue<String>();
        var strategyThread = new AtomicReference<String>();
        runtime.register(new TestAgent("SignalAgent", () -> {
            strategyThread.set(Thread.currentThread().getName());
            started.countDown();
            try { release.await(3, TimeUnit.SECONDS); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); }
        }));
        runtime.register(new TestAgent("RiskAgent", () -> riskDone.add(Thread.currentThread().getName())));
        try {
            runtime.start(context);
            context.getEventBus().publish(AgentEvent.of(AgentEvent.MARKET_TICK, "test", null));
            assertTrue(started.await(2, TimeUnit.SECONDS));
            assertTrue(riskDone.poll(2, TimeUnit.SECONDS).startsWith("investpro-risk-"));
            assertTrue(strategyThread.get().startsWith("investpro-strategy-"));
            release.countDown();
            runtime.stop();
            runtime.start(context);
            context.getEventBus().publish(AgentEvent.of(AgentEvent.MARKET_TICK, "test", null));
            assertNotNull(riskDone.poll(2, TimeUnit.SECONDS));
            assertNull(riskDone.poll(100, TimeUnit.MILLISECONDS), "Restart must not duplicate subscriptions");
        } finally { release.countDown(); runtime.stop(); }
    }

    private record TestAgent(String name, Runnable handler) implements Agent {
        public void start(AgentContext context) { }
        public void stop() { }
        public void onEvent(AgentEvent event) { handler.run(); }
    }
}
