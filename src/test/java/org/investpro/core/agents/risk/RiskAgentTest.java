package org.investpro.core.agents.risk;

import org.investpro.strategy.StrategySignal;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

class RiskAgentTest {

    @Test
    void tradeAllowedFalseBlocksPipeline() {
        RiskAgent agent = new RiskAgent(request -> CompletableFuture.completedFuture(RiskReviewResult.approved(
                1.0, 100.0, "test", "1h", "BUY", "MARKET_ORDER")));

        Map<String, Object> result = agent.process(Map.of(
                "trade_allowed", false,
                "block_reason", "Blocked by regime")).join();

        assertTrue((Boolean) result.get("halt_pipeline"));
        assertTrue((Boolean) result.get("risk_blocked"));
        assertEquals("Blocked by regime", result.get("risk_reason"));
    }

    @Test
    void missingSignalBlocksPipeline() {
        RiskAgent agent = new RiskAgent(request -> CompletableFuture.completedFuture(RiskReviewResult.rejected("no")));

        Map<String, Object> result = agent.process(Map.of()).join();

        assertTrue((Boolean) result.get("halt_pipeline"));
        assertTrue((Boolean) result.get("risk_blocked"));
        assertEquals("No active signal.", result.get("risk_reason"));
    }

    @Test
    void rejectedReviewBlocksAndStoresReview() {
        RiskAgent agent = new RiskAgent(request -> CompletableFuture.completedFuture(
                RiskReviewResult.rejected("Position too large")));

        Map<String, Object> result = agent.process(Map.of("signal", buySignal())).join();

        assertTrue((Boolean) result.get("halt_pipeline"));
        assertTrue((Boolean) result.get("risk_blocked"));
        assertEquals("Position too large", result.get("risk_reason"));
        assertInstanceOf(RiskReviewResult.class, result.get("trade_review"));
    }

    @Test
    void approvedReviewClearsRiskBlockAndStoresReview() {
        RiskReviewResult review = RiskReviewResult.approved(1.0, 100.0, "test", "1h", "BUY", "MARKET_ORDER");
        RiskAgent agent = new RiskAgent(request -> CompletableFuture.completedFuture(review));

        Map<String, Object> result = agent.process(Map.of("signal", buySignal())).join();

        assertFalse((Boolean) result.get("halt_pipeline"));
        assertFalse((Boolean) result.get("risk_blocked"));
        assertNull(result.get("risk_reason"));
        assertSame(review, result.get("trade_review"));
    }

    private StrategySignal buySignal() {
        return StrategySignal.builder()
                .symbol("EUR/USD")
                .timeframe("1h")
                .strategyId("test")
                .strategyName("Test")
                .side(Side.BUY)
                .confidence(0.75)
                .entryPrice(100.0)
                .stopLossPrice(99.0)
                .takeProfitPrice(102.0)
                .build();
    }
}
