package org.investpro.strategy.lab;

import org.investpro.utils.Side;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class StrategyBacktestRunnerAccountingTest {
    @Test
    void fixedAllocationUsesActualPriceAcrossPriceScales() throws Exception {
        var method = StrategyBacktestRunner.class.getDeclaredMethod("calculateQuantity", double.class, double.class);
        method.setAccessible(true);
        var runner = new StrategyBacktestRunner();
        for (double price : new double[]{.00123, 100, 60000}) {
            double quantity = (double) method.invoke(runner, 10000.0, price);
            assertEquals(100, quantity * price, 1e-9);
        }
    }

    @Test
    void historicalExitUsesEpochSecondsAndDeductsBothSidesCosts() throws Exception {
        var method = StrategyBacktestRunner.class.getDeclaredMethod("closeTrade", StrategyBacktestTrade.class,
                double.class, long.class, int.class, String.class, double.class, double.class);
        method.setAccessible(true);
        var trade = StrategyBacktestTrade.builder().strategyName("test").symbol("BTC/USD")
                .timeframe(org.investpro.enums.timeframe.Timeframe.H1)
                .side(Side.BUY).entryPrice(100).quantity(2)
                .entryTime(Instant.ofEpochSecond(1_600_000_000)).build();
        var closed = (StrategyBacktestTrade) method.invoke(new StrategyBacktestRunner(), trade,
                110.0, 1_600_003_600L, 1, "exit", .001, .0002);
        assertEquals(Instant.ofEpochSecond(1_600_003_600), closed.getExitTime());
        assertTrue(closed.getExitTime().isAfter(closed.getEntryTime()));
        assertEquals(20 - 420 * .0012, closed.getProfitLoss(), 1e-9);
    }
}
