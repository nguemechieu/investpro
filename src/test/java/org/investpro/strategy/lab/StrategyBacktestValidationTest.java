package org.investpro.strategy.lab;

import org.investpro.data.CandleData;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.utils.HistoricalDataPrefetcher;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CancellationException;
import static org.junit.jupiter.api.Assertions.*;

class StrategyBacktestValidationTest {
    @Test void queuedRequestsKeepAnImmutableCandleSnapshot() {
        var source = new java.util.ArrayList<>(request().getCandles());
        var queued = request().toBuilder().candles(source).build();
        source.clear();
        assertEquals(100, queued.getCandles().size());
        assertThrows(UnsupportedOperationException.class, () -> queued.getCandles().clear());
    }
    private StrategyBacktestRequest request() {
        return StrategyBacktestRequest.builder().symbol("BTC/USD").timeframe(Timeframe.H1).strategyName("test")
                .candles(java.util.stream.IntStream.range(0, 100)
                        .mapToObj(i -> new CandleData(100, 100, 101, 99, i * 3600, 1)).toList()).build();
    }
    @Test void basicDataReadinessHasTheMeaningItsNamePromises() {
        assertFalse(HistoricalDataPrefetcher.hasEnoughDataForBasicTesting(99));
        assertTrue(HistoricalDataPrefetcher.hasEnoughDataForBasicTesting(100));
    }
    @Test void invalidCostsCapitalAndLimitsProduceUsefulFailures() {
        for (var input : java.util.List.of(request().toBuilder().initialCapital(Double.NaN).build(),
                request().toBuilder().commissionRate(-.01).build(), request().toBuilder().slippageRate(1).build(),
                request().toBuilder().maxTrades(0).build(), request().toBuilder().fallbackExitBars(0).build())) {
            var report = new StrategyBacktestRunner().run(input);
            assertEquals(0, report.getTotalTrades());
            assertTrue(report.getWarnings().getFirst().contains("finite rates"));
        }
    }
    @Test void interruptionIsCancellationRatherThanAZeroScoreFailureReport() {
        Thread.currentThread().interrupt();
        try { assertThrows(CancellationException.class, () -> new StrategyBacktestRunner().run(request())); }
        finally { Thread.interrupted(); }
    }
}
