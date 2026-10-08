package org.investpro.core;

import org.investpro.core.agents.AgentEvent;
import org.investpro.core.agents.AgentEventBus;
import org.investpro.core.agents.symbol.SymbolAgentManager;
import org.investpro.core.agents.symbol.SymbolEvaluationState;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SymbolAgentUpdaterTest {
    private final TradePair pair = TradePair.of("BTC", "USD");

    SymbolAgentUpdaterTest() throws Exception {
    }

    @Test
    void restartProcessesEachEventOnceAndStopUnsubscribes() {
        AgentEventBus bus = new AgentEventBus();
        SymbolAgentManager manager = spy(new SymbolAgentManager());
        SymbolAgentUpdater updater = new SymbolAgentUpdater(bus, manager);
        bus.start();
        try {
            updater.start();
            updater.stop();
            updater.start();
            bus.publish(event(AgentEvent.ORDER_SUBMITTED, false));
            verify(manager, times(1)).updateState(eq(pair), any());
            updater.stop();
            bus.publish(event(AgentEvent.ORDER_SUBMITTED, false));
            verify(manager, times(1)).updateState(eq(pair), any());
        } finally {
            updater.stop();
            bus.stop();
        }
    }

    @Test
    void ordersNeverGrantLivePermissionAndPaperOrdersPreserveValidation() {
        SymbolAgentManager manager = new SymbolAgentManager();
        SymbolAgentUpdater updater = new SymbolAgentUpdater(new AgentEventBus(), manager);
        updater.start();
        try {
            var state = manager.ensureSymbol(pair);
            state.setState(SymbolEvaluationState.PAPER_TRADING);
            updater.accept(event(AgentEvent.ORDER_SUBMITTED, false));
            updater.accept(event(AgentEvent.POSITION_CLOSED, false));
            assertFalse(state.isCanTradeLive());
            assertEquals(SymbolEvaluationState.PAPER_TRADING, state.getState());
            state.setState(SymbolEvaluationState.LIVE_READY);
            state.setCanTradeLive(true);
            updater.accept(event(AgentEvent.ORDER_SUBMITTED, true));
            assertEquals(SymbolEvaluationState.LIVE_READY, state.getState());
            updater.accept(event(AgentEvent.ORDER_SUBMITTED, false));
            assertEquals(SymbolEvaluationState.LIVE_TRADING, state.getState());
        } finally {
            updater.stop();
        }
    }

    @Test
    void coverageDistinguishesSubscribedSymbolsWithoutChangingReadiness() throws Exception {
        SymbolAgentManager manager = new SymbolAgentManager();
        var subscribed = manager.ensureSymbol(pair);
        var excluded = manager.ensureSymbol(TradePair.of("ETH", "USD"));
        subscribed.setState(SymbolEvaluationState.LIVE_READY);
        subscribed.setCanTradeLive(true);
        manager.updateMarketDataCoverage(List.of(pair));
        assertEquals("Waiting for market ticks", subscribed.getMarketDataStatus());
        assertEquals("Not subscribed to market data", excluded.getMarketDataStatus());
        assertTrue(subscribed.isLiveAllowed());
        manager.updateMarketDataCoverage(List.of());
        assertEquals("Not subscribed to market data", subscribed.getMarketDataStatus());
    }

    private AgentEvent event(String type, boolean paper) {
        return new AgentEvent(type, "test", null, Instant.now(),
                Map.of("tradePairObject", pair, "paperTrading", paper));
    }
}
