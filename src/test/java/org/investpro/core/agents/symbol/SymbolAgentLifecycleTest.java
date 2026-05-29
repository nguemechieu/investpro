package org.investpro.core.agents.symbol;

import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SymbolAgentLifecycleTest {

    @Test
    void marketTicksDoNotRegressLiveReadyStateBackToNotStarted() throws Exception {
        SymbolAgentManager manager = new SymbolAgentManager();
        TradePair symbol = TradePair.of("BTC", "USD");
        SymbolAgent agent = new SymbolAgent(symbol, manager);

        SymbolAgentState state = manager.ensureSymbol(symbol);
        state.setState(SymbolEvaluationState.LIVE_READY);
        state.setCanTradeLive(true);
        manager.updateState(symbol, state);

        invokeAdvanceLifecycle(agent, 1L);

        SymbolAgentState updated = manager.ensureSymbol(symbol);
        assertEquals(SymbolEvaluationState.LIVE_READY, updated.getState());
        assertEquals(SymbolTradingMode.LIVE_READY, updated.getTradingMode());
    }

    @Test
    void marketTicksDoNotRegressAssignedStateBackToNotStarted() throws Exception {
        SymbolAgentManager manager = new SymbolAgentManager();
        TradePair symbol = TradePair.of("ETH", "USD");
        SymbolAgent agent = new SymbolAgent(symbol, manager);

        SymbolAgentState state = manager.ensureSymbol(symbol);
        state.setState(SymbolEvaluationState.ASSIGNED);
        state.setAssignedStrategyName("test-strategy");
        state.setCanTradeLive(false);
        manager.updateState(symbol, state);

        invokeAdvanceLifecycle(agent, 1L);

        SymbolAgentState updated = manager.ensureSymbol(symbol);
        assertEquals(SymbolEvaluationState.ASSIGNED, updated.getState());
        assertEquals(SymbolTradingMode.PAPER_TRADING, updated.getTradingMode());
    }

    private void invokeAdvanceLifecycle(SymbolAgent agent, long ticks) throws Exception {
        Method method = SymbolAgent.class.getDeclaredMethod("advanceLifecycle", long.class, Ticker.class);
        method.setAccessible(true);
        method.invoke(agent, ticks, new Ticker(100.0, 99.0, 101.0, 1.0, System.currentTimeMillis()));
    }
}
