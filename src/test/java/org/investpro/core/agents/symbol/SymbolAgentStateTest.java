package org.investpro.core.agents.symbol;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.TradePair;
import org.investpro.symbol.SymbolAgentState;
import org.investpro.symbol.SymbolEvaluationState;
import org.investpro.symbol.SymbolTradingMode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SymbolAgentStateTest {

    @Test
    void assignedStrategyWithoutLiveApprovalStaysInPaperTradingMode() throws Exception {
        SymbolAgentState state = SymbolAgentState.builder()
                .symbol(new TradePair("BTC", "USD"))
                .state(SymbolEvaluationState.ASSIGNED)
                .assignedStrategyName("trend-following")
                .activeStrategyName("trend-following")
                .activeTimeframe(Timeframe.H1)
                .canTradeLive(false)
                .build();

        assertEquals(SymbolTradingMode.PAPER_TRADING, state.getTradingMode());
        assertFalse(state.isLiveAllowed());
        assertEquals("Paper validating: trend-following | 1h", state.getMarketWatchStatusText());
    }

    @Test
    void assignedStrategyWithLiveApprovalIsLiveReady() throws Exception {
        SymbolAgentState state = SymbolAgentState.builder()
                .symbol(new TradePair("BTC", "USD"))
                .state(SymbolEvaluationState.ASSIGNED)
                .assignedStrategyName("trend-following")
                .activeStrategyName("trend-following")
                .activeTimeframe(Timeframe.H1)
                .canTradeLive(true)
                .build();

        assertEquals(SymbolTradingMode.LIVE_READY, state.getTradingMode());
        assertTrue(state.isLiveAllowed());
    }
}
