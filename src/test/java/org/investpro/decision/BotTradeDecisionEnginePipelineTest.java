package org.investpro.decision;

import org.investpro.models.trading.Ticker;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BotTradeDecisionEnginePipelineTest {

    @Test
    void invalidSignalStrengthReturnsSkip() throws Exception {
        BotTradeDecisionEngine engine = new BotTradeDecisionEngine(null);
        BotTradeDecision decision = engine.evaluateSignal(
                TradePair.fromSymbol("EUR/USD"),
                Side.BUY,
                ticker(1.1000, 1.1002, 1.1001),
                0.2);

        assertEquals(BotTradeDecision.FinalAction.SKIP, decision.finalAction());
        assertTrue(decision.blockers().stream().anyMatch(b -> b.contains("Invalid signal strength")));
    }

    @Test
    void invalidTickerReturnsSkip() throws Exception {
        BotTradeDecisionEngine engine = new BotTradeDecisionEngine(null);
        BotTradeDecision decision = engine.evaluateSignal(
                TradePair.fromSymbol("EUR/USD"),
                Side.BUY,
                ticker(0.0, 0.0, 0.0),
                0.8);

        assertEquals(BotTradeDecision.FinalAction.SKIP, decision.finalAction());
        assertFalse(decision.blockers().isEmpty());
    }

    @Test
    void wideSpreadCreatesBlockerAndSkips() throws Exception {
        BotTradeDecisionEngine engine = new BotTradeDecisionEngine(null);
        BotTradeDecision decision = engine.evaluateSignal(
                TradePair.fromSymbol("BTC/USDT"),
                Side.BUY,
                ticker(103.0, 101.0, 102.0),
                0.8);

        assertEquals(BotTradeDecision.FinalAction.SKIP, decision.finalAction());
        assertTrue(decision.blockers().stream().anyMatch(b -> b.contains("Spread")));
    }

    @Test
    void weakStrategyFallsBackToIndicatorAndSkipsWhenStillWeak() throws Exception {
        BotTradeDecisionEngine engine = new BotTradeDecisionEngine(null);
        BotTradeDecision decision = engine.evaluateSignal(
                TradePair.fromSymbol("EUR/USD"),
                Side.HOLD,
                ticker(1.1000, 1.1002, 1.1001),
                0.3);

        assertNotEquals(SetupSource.STRATEGY, decision.setupSource());
        assertNotEquals(BotTradeDecision.FinalAction.TRADE, decision.finalAction());
    }

    @Test
    void negativeExpectedValueReturnsSkip() throws Exception {
        BotTradeDecisionEngine engine = new BotTradeDecisionEngine(null);
        BotTradeDecision decision = engine.evaluateSignal(
                TradePair.fromSymbol("BTC/USDT"),
                Side.BUY,
                ticker(100.9, 100.0, 100.45),
                0.9);

        assertEquals(BotTradeDecision.FinalAction.SKIP, decision.finalAction());
        assertTrue(decision.blockers().stream().anyMatch(b -> b.contains("Negative expected value")));
    }

    private static Ticker ticker(double ask, double bid, double last) {
        return new Ticker(last, bid, ask, 0.0, System.currentTimeMillis());
    }
}
