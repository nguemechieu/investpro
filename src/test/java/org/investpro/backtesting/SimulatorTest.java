package org.investpro.backtesting;

import org.investpro.data.CandleData;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SimulatorTest {
    @Test void rejectsSignalsOutsideTheHistoricalWindow() {
        assertThrows(IllegalArgumentException.class, () -> simulator(config(), List.of(buy(-1))).run(List.of(candle(100))));
        assertThrows(IllegalArgumentException.class, () -> simulator(config(), List.of(buy(1))).run(List.of(candle(100))));
    }
    private int timestampOffset;
    @Test
    void feesAreChargedOnNotionalAndTradeProfitMatchesAccountProfit() {
        BacktestConfig config = config();
        config.setCommissionPercent(1);
        Simulator simulator = simulator(config, List.of(buy(0)));
        var result = simulator.run(List.of(candle(100), candle(100)));
        double invested = 1000 / 1.01;
        assertEquals(invested * .99, result.getFinalBalance(), 1e-9);
        assertEquals(result.getTotalReturn(), result.getTrades().getFirst().getProfit(), 1e-9);
        assertEquals(invested * .02, result.getTrades().getFirst().getFee(), 1e-9);
        assertEquals(result.getFinalBalance(), simulator.getEquityCurve().getLast(), 1e-9);
    }

    @Test
    void drawdownIncludesCandlesWithoutSignalsAndSignalsExecuteChronologically() {
        BacktestConfig config = config();
        config.setCommissionPercent(0);
        Simulator simulator = simulator(config, List.of(
                new BacktestStrategy.SignalEvent(2, BacktestStrategy.SignalEvent.Type.SELL, "exit"), buy(0)));
        var result = simulator.run(List.of(candle(100), candle(50), candle(100)));
        assertEquals(50, result.getMaxDrawdown(), 1e-9);
        assertEquals(1000, result.getFinalBalance(), 1e-9);
        assertEquals(1, result.getTotalTrades());
        assertEquals(4, simulator.getEquityCurve().size());
    }

    @Test
    void rejectsInvalidFinancialInputs() {
        BacktestConfig config = config();
        config.setCommissionPercent(-1);
        assertThrows(IllegalArgumentException.class,
                () -> simulator(config, List.of()).run(List.of(candle(100))));
        config.setCommissionPercent(0);
        assertThrows(IllegalArgumentException.class,
                () -> simulator(config, List.of()).run(List.of(candle(Double.NaN))));
    }

    private Simulator simulator(BacktestConfig config, List<BacktestStrategy.SignalEvent> signals) {
        BacktestStrategy strategy = mock(BacktestStrategy.class);
        when(strategy.processData()).thenReturn(signals);
        return new Simulator(strategy, config);
    }

    private BacktestConfig config() {
        return new BacktestConfig(null, LocalDateTime.of(2020, 1, 1, 0, 0),
                LocalDateTime.of(2020, 1, 2, 0, 0), 1000);
    }

    private BacktestStrategy.SignalEvent buy(int index) {
        return new BacktestStrategy.SignalEvent(index, BacktestStrategy.SignalEvent.Type.BUY, "entry");
    }

    private CandleData candle(double price) {
        return new CandleData(price, price, price, price, 1_600_000_000 + timestampOffset++ * 3600, 100);
    }
}
