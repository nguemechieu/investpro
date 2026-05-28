package org.investpro.backtesting.simulation;

import org.investpro.backtesting.BacktestConfig;
import org.investpro.backtesting.BacktestStrategy;

/**
 * Fast risk gate for simulation events.
 */
public final class SimulationRiskEngine {
    private final BacktestConfig config;
    private final SimulationConfig simulationConfig;

    public SimulationRiskEngine(BacktestConfig config, SimulationConfig simulationConfig) {
        this.config = config;
        this.simulationConfig = simulationConfig;
    }

    public boolean allow(BacktestStrategy.SignalEvent signal, PortfolioEngine portfolio, int completedTrades) {
        if (signal == null || signal.type() == BacktestStrategy.SignalEvent.Type.HOLD) {
            return false;
        }
        if (completedTrades >= simulationConfig.maxTradesPerSimulation()) {
            return false;
        }
        if (signal.type() == BacktestStrategy.SignalEvent.Type.BUY) {
            return portfolio.cash() > 0.0
                    && portfolio.totalExposure() < Math.max(config.getMaxPositionSize(), 0.0);
        }
        return signal.type() == BacktestStrategy.SignalEvent.Type.SELL && portfolio.hasPosition();
    }
}
