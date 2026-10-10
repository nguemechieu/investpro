package org.investpro.strategy;

import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.models.trading.TradePair;
import org.investpro.strategy.impl.UnifiedStrategy;
import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdditionalStrategiesTest {
    private StrategyContext context(double previousOpen, double previousClose, double open, double close) throws Exception {
        var candles = new ArrayList<CandleData>();
        for (int i = 0; i < 100; i++) candles.add(new CandleData(100, 100, 102, 98, 100000 + i * 3600, 10));
        candles.set(98, new CandleData(previousOpen, previousClose, Math.max(previousOpen, previousClose) + 1,
                Math.min(previousOpen, previousClose) - 1, 100000 + 98 * 3600, 10));
        candles.set(99, new CandleData(open, close, Math.max(open, close) + 1, Math.min(open, close) - 1, 100000 + 99 * 3600, 10));
        return StrategyContext.builder().symbol(new TradePair("BTC", "USD")).timeframe(Timeframe.H1)
                .candles(candles).barsAvailable(100).currentPrice(close).bid(close - 0.1).ask(close + 0.1).build();
    }
    private FeatureRow features(double close, double fast, double slow, double rsi) {
        return FeatureRow.builder().close(close).emaFast(fast).emaSlow(slow).rsi(rsi).atr(1)
                .lowerBand(95).upperBand(105).regime("ranging").build();
    }
    private StrategySignal evaluate(String name, FeatureRow current, FeatureRow previous, StrategyContext context) {
        var pipeline = mock(FeaturePipeline.class);
        when(pipeline.computeLatest(anyList(), any())).thenReturn(current, previous);
        return new UnifiedStrategy(name, pipeline).generateSignal(context);
    }
    @Test void rsiTrendRequiresAlignmentAndAvoidsExtremes() throws Exception {
        var context = context(100, 100, 100, 100);
        assertEquals(Side.BUY, evaluate("RSI Trend Filter", features(103, 102, 101, 55), null, context).getSide());
        assertEquals(Side.SELL, evaluate("RSI Trend Filter", features(97, 98, 99, 45), null, context).getSide());
        assertFalse(evaluate("RSI Trend Filter", features(103, 102, 101, 80), null, context).isActionable());
    }
    @Test void bandReentryRequiresAPreviousOutsideClose() throws Exception {
        var context = context(100, 100, 100, 100);
        assertEquals(Side.BUY, evaluate("Bollinger Reentry", features(96, 100, 100, 50), features(94, 100, 100, 50), context).getSide());
        assertEquals(Side.SELL, evaluate("Bollinger Reentry", features(104, 100, 100, 50), features(106, 100, 100, 50), context).getSide());
        assertFalse(evaluate("Bollinger Reentry", features(100, 100, 100, 50), features(100, 100, 100, 50), context).isActionable());
    }
    @Test void engulfingRequiresOppositeBodiesAndFullBodyCoverage() throws Exception {
        var features = features(100, 100, 100, 50);
        assertEquals(Side.BUY, evaluate("Engulfing Reversal", features, null, context(101, 99, 98, 102)).getSide());
        assertEquals(Side.SELL, evaluate("Engulfing Reversal", features, null, context(99, 101, 102, 98)).getSide());
        assertFalse(evaluate("Engulfing Reversal", features, null, context(101, 99, 100, 101)).isActionable());
        assertFalse(evaluate("Engulfing Reversal", features, null, context(100, 100, 98, 102)).isActionable());
    }
    @Test void newFamiliesResolveInRegistryWithCatalogVariants() {
        var registry = new StrategyRegistry();
        for (String name : new String[]{"RSI Trend Filter", "Bollinger Reentry", "Engulfing Reversal"}) {
            assertNotNull(registry.getStrategy(name));
            assertTrue(StrategyCatalog.availableStrategyNames().stream().anyMatch(value -> value.startsWith(name + " |")));
        }
    }
}
