package org.investpro.backtesting.simulation;

import org.investpro.backtesting.BacktestConfig;
import org.investpro.config.AppConfig;

/**
 * Runtime knobs for the event-driven simulator.
 */
public record SimulationConfig(
        int equityCurveSampling,
        boolean metricsEnabled,
        boolean resourceProtectionEnabled,
        int maxTradesPerSimulation,
        boolean incrementalStatisticsEnabled,
        long simulatedLatencyMillis,
        double slippagePercent,
        double spreadPercent,
        int maxConcurrentSimulations
) {
    public static SimulationConfig from(BacktestConfig config) {
        return new SimulationConfig(
                Math.max(1, config.getEquityCurveSampling()),
                config.isEnableMetrics(),
                config.isEnableResourceProtection(),
                Math.max(1, config.getMaxTradesPerSimulation()),
                config.isEnableIncrementalStatistics(),
                Math.max(0L, config.getSimulatedLatencyMillis()),
                Math.max(0.0, config.getSlippagePercent()),
                Math.max(0.0, config.getSpreadPercent()),
                Math.max(1, AppConfig.getInt("backtest.maxConcurrentSimulations", 2)));
    }
}
