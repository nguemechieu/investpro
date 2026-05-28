package org.investpro.backtesting.simulation;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.backtesting.BacktestConfig;
import org.investpro.backtesting.BacktestResult;
import org.investpro.backtesting.BacktestStrategy;
import org.investpro.data.CandleData;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Institutional-grade event-driven simulation engine.
 *
 * <p>Execution is candle-by-candle: market event, strategy evaluation, risk
 * validation, simulated execution, portfolio update, metrics update.</p>
 */
@Slf4j
@Getter
@Setter
public final class SimulationEngine {
    private final BacktestStrategy strategy;
    private final BacktestConfig config;
    private final SimulationConfig simulationConfig;
    private final StrategyRuntime strategyRuntime;
    private final PortfolioEngine portfolio;
    private final SimulationRiskEngine riskEngine;
    private final ExecutionSimulator executionSimulator;
    private final MetricsEngine metricsEngine = new MetricsEngine();
    private final PerformanceRecorder recorder;
    private final SimulationResourceGuard resourceGuard;
    private volatile BacktestSessionSnapshot latestSnapshot;

    public SimulationEngine(BacktestStrategy strategy, BacktestConfig config) {
        this.strategy = strategy;
        this.config = config;
        this.simulationConfig = SimulationConfig.from(config);
        this.strategyRuntime = new StrategyRuntime(strategy);
        this.portfolio = new PortfolioEngine(config);
        this.riskEngine = new SimulationRiskEngine(config, simulationConfig);
        this.executionSimulator = new ExecutionSimulator(config, simulationConfig);
        this.recorder = new PerformanceRecorder(simulationConfig.equityCurveSampling());
        this.resourceGuard = new SimulationResourceGuard(simulationConfig.resourceProtectionEnabled());
    }

    public BacktestResult run(List<CandleData> candles) throws InterruptedException {
        if (candles == null || candles.isEmpty()) {
            throw new IllegalArgumentException("Historical data cannot be empty");
        }

        String sessionId = UUID.randomUUID().toString();
        SimulationClock clock = new SimulationClock();
        MarketDataFeed feed = new MarketDataFeed(candles);

        portfolio.reset();
        recorder.reset();
        metricsEngine.reset(config.getInitialBalance());
        strategyRuntime.initialize(candles);
        clock.start();

        try {
            while (feed.hasNext()) {
                checkCancelled();
                CandleData candle = feed.next();
                int index = feed.index();

                resourceGuard.check(index);

                long strategyStart = System.nanoTime();
                List<BacktestStrategy.SignalEvent> signals = strategyRuntime.onCandle(candle, index);
                clock.addStrategyNanos(System.nanoTime() - strategyStart);

                for (BacktestStrategy.SignalEvent signal : signals) {
                    if (!riskEngine.allow(signal, portfolio, recorder.tradeCount())) {
                        continue;
                    }

                    long executionStart = System.nanoTime();
                    ExecutionFill fill = executionSimulator.fill(signal, candle, portfolio);
                    if (signal.type() == BacktestStrategy.SignalEvent.Type.BUY && !portfolio.hasPosition()) {
                        portfolio.open(fill);
                    } else if (signal.type() == BacktestStrategy.SignalEvent.Type.SELL && portfolio.hasPosition()) {
                        BacktestResult.TradeRecord closed = portfolio.close(fill);
                        recorder.recordTrade(closed);
                        metricsEngine.recordClosedTrade(closed);
                    }
                    clock.addExecutionNanos(System.nanoTime() - executionStart);
                }

                double equity = portfolio.totalEquity(candle.closePrice());
                metricsEngine.recordEquity(equity);
                recorder.recordEquity(index, equity);
                clock.candleProcessed();

                if (index % 1024 == 0) {
                    latestSnapshot = snapshot(sessionId, index, feed.size(), equity, "RUNNING", null);
                }
            }

            closeOpenPosition(feed);
            CandleData last = feed.last();
            double finalPrice = last == null ? 0.0 : last.closePrice();
            double finalEquity = portfolio.totalEquity(finalPrice);
            recorder.recordFinalEquity(finalEquity);

            SimulationMetrics simMetrics = clock.finish();
            latestSnapshot = snapshot(sessionId, feed.index(), feed.size(), finalEquity, "COMPLETED", simMetrics);
            return buildResult(simMetrics, finalEquity);
        } catch (InterruptedException interrupted) {
            CandleData last = feed.last();
            double price = last == null ? 0.0 : last.closePrice();
            latestSnapshot = snapshot(sessionId, feed.index(), feed.size(), portfolio.totalEquity(price), "CANCELLED", clock.finish());
            Thread.currentThread().interrupt();
            throw interrupted;
        }
    }

    public BacktestSessionSnapshot latestSnapshot() {
        return latestSnapshot;
    }

    private void closeOpenPosition(MarketDataFeed feed) {
        if (!portfolio.hasPosition()) {
            return;
        }
        CandleData last = feed.last();
        if (last == null) {
            return;
        }
        BacktestStrategy.SignalEvent signal = new BacktestStrategy.SignalEvent(
                Math.max(0, feed.size() - 1),
                BacktestStrategy.SignalEvent.Type.SELL,
                "End of simulation");
        ExecutionFill fill = executionSimulator.fill(signal, last, portfolio);
        BacktestResult.TradeRecord closed = portfolio.close(fill);
        recorder.recordTrade(closed);
        metricsEngine.recordClosedTrade(closed);
    }

    private BacktestResult buildResult(SimulationMetrics simMetrics, double finalEquity) {
        BacktestResult result = new BacktestResult();
        result.setStrategyName(strategyRuntime.strategyName());
        result.setInitialBalance(config.getInitialBalance());
        result.setBackTestDuration(simMetrics.durationMillis());
        result.setTrades(recorder.tradesCopy());
        result.setEquityCurve(recorder.equityCurveCopy());
        metricsEngine.applyTo(result, config.getInitialBalance(), finalEquity);

        result.setCandlesProcessed(simMetrics.candlesProcessed());
        result.setCandlesPerSecond(simMetrics.candlesPerSecond());
        result.setStrategyExecutionNanos(simMetrics.strategyExecutionNanos());
        result.setIndicatorCalculationNanos(simMetrics.indicatorCalculationNanos());
        result.setExecutionSimulationNanos(simMetrics.executionSimulationNanos());
        result.setStartMemoryBytes(simMetrics.startMemoryBytes());
        result.setEndMemoryBytes(simMetrics.endMemoryBytes());
        result.setPeakMemoryBytes(simMetrics.peakMemoryBytes());
        result.setGcPressureEstimateBytes(simMetrics.gcPressureEstimateBytes());
        result.addAdditionalMetric("equityCurveSampling", simulationConfig.equityCurveSampling());
        result.addAdditionalMetric("maxTradesPerSimulation", simulationConfig.maxTradesPerSimulation());
        return result;
    }

    private BacktestSessionSnapshot snapshot(
            String sessionId,
            int index,
            int total,
            double equity,
            String status,
            SimulationMetrics metrics) {
        return new BacktestSessionSnapshot(
                sessionId,
                strategyRuntime.strategyName(),
                Math.max(0, index + 1),
                total,
                total <= 0 ? 0.0 : Math.min(1.0, (index + 1.0) / total),
                portfolio.cash(),
                equity,
                recorder.tradeCount(),
                status,
                metrics,
                Instant.now());
    }

    private void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedException("Simulation cancelled");
        }
    }
}
