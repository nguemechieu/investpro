package org.investpro.backtesting;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.backtesting.simulation.BacktestSessionSnapshot;
import org.investpro.backtesting.simulation.SimulationEngine;
import org.investpro.data.CandleData;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Lightweight orchestration facade for InvestPro's event-driven simulation
 * engine.
 *
 * <p>The public API remains compatible with the legacy simulator, while the
 * actual implementation now processes candles as events and supports
 * cancellation, sampled equity curves, incremental statistics, and performance
 * instrumentation.</p>
 */
@Getter
@Setter
@Slf4j
public class Simulator {
    private BacktestStrategy strategy;
    private BacktestConfig config;
    private List<CandleData> historicalData;
    private List<BacktestResult.TradeRecord> executedTrades;
    private List<Double> equityCurve;
    private BacktestSessionSnapshot latestSnapshot;

    public Simulator(BacktestStrategy strategy, @NonNull BacktestConfig config) {
        this.strategy = strategy;
        this.config = config;
        this.executedTrades = new ArrayList<>();
        this.equityCurve = new ArrayList<>();
        log.debug("Simulator created for strategy {}", strategy == null ? "unknown" : strategy.getClass().getSimpleName());
    }

    /**
     * Run a backtest on historical candles. This method must not be called on
     * the JavaFX application thread.
     */
    public BacktestResult run(List<CandleData> historicalData) {
        this.historicalData = historicalData;
        this.executedTrades.clear();
        this.equityCurve.clear();

        SimulationEngine engine = new SimulationEngine(strategy, config);
        try {
            BacktestResult result = engine.run(historicalData);
            this.latestSnapshot = engine.latestSnapshot();
            this.executedTrades.addAll(result.getTrades());
            this.equityCurve.addAll(result.getEquityCurve());
            log.info("Simulation completed: strategy={} candles={} trades={} speed={} candles/s",
                    result.getStrategyName(),
                    result.getCandlesProcessed(),
                    result.getTotalTrades(),
                    "%.0f".formatted(result.getCandlesPerSecond()));
            return result;
        } catch (InterruptedException interrupted) {
            this.latestSnapshot = engine.latestSnapshot();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Simulation cancelled", interrupted);
        }
    }

    public List<BacktestResult.TradeRecord> getExecutedTrades() {
        return new ArrayList<>(executedTrades);
    }

    public List<Double> getEquityCurve() {
        return new ArrayList<>(equityCurve);
    }
}
