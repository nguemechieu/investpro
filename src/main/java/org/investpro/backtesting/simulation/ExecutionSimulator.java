package org.investpro.backtesting.simulation;

import org.investpro.backtesting.BacktestConfig;
import org.investpro.backtesting.BacktestStrategy;
import org.investpro.data.CandleData;

/**
 * Models fills, spread, slippage, commission, and future latency hooks.
 */
public final class ExecutionSimulator {
    private final BacktestConfig config;
    private final SimulationConfig simulationConfig;

    public ExecutionSimulator(BacktestConfig config, SimulationConfig simulationConfig) {
        this.config = config;
        this.simulationConfig = simulationConfig;
    }

    public ExecutionFill fill(BacktestStrategy.SignalEvent signal, CandleData candle, PortfolioEngine portfolio) {
        double close = candle.closePrice();
        double spreadAdjustment = close * simulationConfig.spreadPercent() / 100.0;
        double slippageAdjustment = close * simulationConfig.slippagePercent() / 100.0;
        boolean buy = signal.type() == BacktestStrategy.SignalEvent.Type.BUY;
        double price = buy
                ? close + spreadAdjustment + slippageAdjustment
                : Math.max(0.00000001, close - spreadAdjustment - slippageAdjustment);

        if (buy) {
            double cash = portfolio.cash();
            double commission = cash * config.getCommissionPercent() / 100.0;
            double investAmount = Math.max(0.0, Math.min(cash - commission, config.getMaxPositionSize()));
            double quantity = investAmount / price;
            return new ExecutionFill(signal.candleIndex(), price, quantity, commission, false, signal.reason());
        }

        double quantity = portfolio.openQuantity();
        double commission = price * quantity * config.getCommissionPercent() / 100.0;
        return new ExecutionFill(signal.candleIndex(), price, quantity, commission, false, signal.reason());
    }
}
