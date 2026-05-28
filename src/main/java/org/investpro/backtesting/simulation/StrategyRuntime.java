package org.investpro.backtesting.simulation;

import org.investpro.backtesting.BacktestStrategy;
import org.investpro.data.CandleData;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Adapts legacy {@link BacktestStrategy} implementations to per-candle
 * event-driven execution.
 */
public final class StrategyRuntime {
    private final BacktestStrategy strategy;
    private int consumedSignalCount;

    public StrategyRuntime(BacktestStrategy strategy) {
        this.strategy = Objects.requireNonNull(strategy, "strategy must not be null");
    }

    public void initialize(List<CandleData> candles) {
        strategy.initialize(candles);
        consumedSignalCount = strategy.signalCount();
    }

    public @NonNull List<BacktestStrategy.SignalEvent> onCandle(CandleData candle, int index) {
        strategy.onCandleUpdate(candle, index);
        int currentCount = strategy.signalCount();
        if (currentCount <= consumedSignalCount) {
            return List.of();
        }

        List<BacktestStrategy.SignalEvent> newSignals = new ArrayList<>(currentCount - consumedSignalCount);
        for (int i = consumedSignalCount; i < currentCount; i++) {
            BacktestStrategy.SignalEvent signal = strategy.signalAt(i);
            if (signal != null && signal.candleIndex() == index) {
                newSignals.add(signal);
            }
        }
        consumedSignalCount = currentCount;
        return newSignals;
    }

    public String strategyName() {
        return strategy.getStrategyName();
    }
}
