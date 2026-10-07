package org.investpro.ai;

import org.investpro.risk.TradeRiskContext;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.StrategySignal;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class AiTradeReviewSignalContextTest {
    @Test void strategyIdentityAndConfidenceReachTheReview() {
        var pair = mock(TradePair.class);
        var signal = StrategySignal.builder().side(Side.BUY).confidence(.84)
                .strategyId("ema").strategyName("EMA crossover").reason("Confirmed crossover").build();
        var context = TradeRiskContext.builder().symbol(pair).broker("test")
                .assetClass("CRYPTO").contractType("SPOT").entryPrice(100).build();
        var request = AiTradeReviewRequest.fromStrategySignal(signal, context, null);
        assertSame(pair, request.getSymbol());
        assertEquals(.84, request.getSignalConfidence());
        assertEquals("EMA crossover", request.getStrategyName());
        assertEquals("Confirmed crossover", request.getSignalReason());
        assertEquals("test", request.getBroker());
    }
}
