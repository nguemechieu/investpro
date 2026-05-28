package org.investpro.backtesting;

import org.investpro.data.CandleData;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulatorEventDrivenTest {

    @Test
    void simulatorProcessesCandlesAsEventsWithoutPreGeneratingSignals() {
        BacktestConfig config = config();
        config.setEquityCurveSampling(2);
        TestStrategy strategy = new TestStrategy(config);
        Simulator simulator = new Simulator(strategy, config);

        BacktestResult result = simulator.run(candles(8));

        assertEquals(0, strategy.processDataCalls);
        assertEquals(8, strategy.candleUpdates);
        assertEquals(1, result.getTotalTrades());
        assertFalse(result.getTrades().isEmpty());
        assertTrue(result.getTrades().get(0).getExitTime() > 0);
        assertTrue(result.getEquityCurve().size() < 8);
        assertEquals(8, result.getCandlesProcessed());
        assertTrue(result.getCandlesPerSecond() >= 0.0);
    }

    private static BacktestConfig config() {
        return new BacktestConfig(
                null,
                LocalDateTime.now().minusDays(1),
                LocalDateTime.now(),
                1_000.0);
    }

    private static List<CandleData> candles(int count) {
        List<CandleData> candles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double price = 100.0 + i;
            candles.add(new CandleData(price, price + 1.0, price + 2.0, price - 1.0, i, 1_000.0));
        }
        return candles;
    }

    private static final class TestStrategy extends BacktestStrategy {
        private int processDataCalls;
        private int candleUpdates;

        private TestStrategy(BacktestConfig config) {
            super("event-test", config);
        }

        @Override
        public List<SignalEvent> processData() {
            processDataCalls++;
            return List.of();
        }

        @Override
        public void onCandleUpdate(CandleData candle, int candleIndex) {
            candleUpdates++;
            if (candleIndex == 1) {
                addSignal(new SignalEvent(candleIndex, SignalEvent.Type.BUY, "test buy"));
            } else if (candleIndex == 5) {
                addSignal(new SignalEvent(candleIndex, SignalEvent.Type.SELL, "test sell"));
            }
        }
    }
}
