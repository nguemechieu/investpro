package org.investpro.backtesting;

import lombok.Getter;
import lombok.Setter;
import org.investpro.data.CandleData;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Main backtesting engine for strategy evaluation
 * Manages strategy execution, simulation, and result aggregation
 */
@Getter
@Setter
public class BackTesting {
    private List<BacktestStrategy> strategies;
    private List<BacktestConfig> configurations;
    private Map<String, BacktestResult> results;
    private ExecutorService executorService;
    private boolean useParallel;

    public BackTesting() {
        this.strategies = new ArrayList<>();
        this.configurations = new ArrayList<>();
        this.results = new ConcurrentHashMap<>();
        this.executorService = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        this.useParallel = false;
    }

    /**
     * Add a strategy to be backtested
     */
    public void addStrategy(BacktestStrategy strategy) {
        if (strategy != null) {
            strategies.add(strategy);
        }
    }

    /**
     * Add multiple strategies
     */
    public void addStrategies(BacktestStrategy... strategies) {
        for (BacktestStrategy strategy : strategies) {
            addStrategy(strategy);
        }
    }

    /**
     * Add a backtesting configuration
     */
    public void addConfiguration(BacktestConfig config) {
        if (config != null) {
            configurations.add(config);
        }
    }

    /**
     * Run backtest for all strategies with all configurations
     */
    public Map<String, BacktestResult> runBacktest(List<CandleData> historicalData) {
        results.clear();

        if (strategies.isEmpty()) {
            throw new IllegalStateException("No strategies configured for backtesting");
        }
        if (configurations.isEmpty()) {
            throw new IllegalStateException("No configurations provided for backtesting");
        }
        if (historicalData == null || historicalData.isEmpty()) {
            throw new IllegalArgumentException("Historical data cannot be empty");
        }

        if (useParallel) {
            runParallel(historicalData);
        } else {
            runSequential(historicalData);
        }

        return new HashMap<>(results);
    }

    /**
     * Run backtests sequentially
     */
    private void runSequential(List<CandleData> historicalData) {
        for (BacktestStrategy strategy : strategies) {
            for (BacktestConfig config : configurations) {
                BacktestResult result = executeBacktest(strategy, config, historicalData);
                String key = generateResultKey(strategy.getStrategyName(), config.getTradePair().getSymbol());
                results.put(key, result);
            }
        }
    }

    /**
     * Run backtests in parallel
     */
    private void runParallel(List<CandleData> historicalData) {
        List<Future<BacktestResult>> futures = new ArrayList<>();

        for (BacktestStrategy strategy : strategies) {
            for (BacktestConfig config : configurations) {
                Future<BacktestResult> future = executorService.submit(() -> 
                    executeBacktest(strategy, config, historicalData)
                );
                futures.add(future);
            }
        }

        for (int i = 0; i < futures.size(); i++) {
            try {
                BacktestResult result = futures.get(i).get();
                int configIdx = i / strategies.size();
                int strategyIdx = i % strategies.size();
                String key = generateResultKey(
                    strategies.get(strategyIdx).getStrategyName(),
                    configurations.get(configIdx).getTradePair().getSymbol()
                );
                results.put(key, result);
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Error in parallel backtest execution: " + e.getMessage());
            }
        }
    }

    /**
     * Execute a single backtest
     */
    private BacktestResult executeBacktest(BacktestStrategy strategy, 
                                          BacktestConfig config, 
                                          List<CandleData> historicalData) {
        Simulator simulator = new Simulator(strategy, config);
        return simulator.run(historicalData);
    }

    /**
     * Get backtest result for a specific strategy and configuration
     */
    public BacktestResult getResult(String strategyName, String pair) {
        String key = generateResultKey(strategyName, pair);
        return results.get(key);
    }

    /**
     * Get all results
     */
    public Map<String, BacktestResult> getAllResults() {
        return new HashMap<>(results);
    }

    /**
     * Get best performing strategy by return
     */
    public BacktestResult getBestByReturn() {
        return results.values().stream()
                .max(Comparator.comparingDouble(BacktestResult::getReturnPercent))
                .orElse(null);
    }

    /**
     * Get best performing strategy by Sharpe ratio
     */
    public BacktestResult getBestBySharpeRatio() {
        return results.values().stream()
                .max(Comparator.comparingDouble(BacktestResult::getSharpeRatio))
                .orElse(null);
    }

    /**
     * Get best performing strategy by win rate
     */
    public BacktestResult getBestByWinRate() {
        return results.values().stream()
                .max(Comparator.comparingDouble(BacktestResult::getWinRate))
                .orElse(null);
    }

    /**
     * Get best performing strategy by profit factor
     */
    public BacktestResult getBestByProfitFactor() {
        return results.values().stream()
                .max(Comparator.comparingDouble(BacktestResult::getProfitFactor))
                .orElse(null);
    }

    /**
     * Filter results by criteria
     */
    public List<BacktestResult> filterResults(BacktestResultFilter filter) {
        return results.values().stream()
                .filter(filter::matches)
                .collect(Collectors.toList());
    }

    /**
     * Get statistics for all results
     */
    public BacktestStatistics getStatistics() {
        return new BacktestStatistics(results.values());
    }

    /**
     * Set parallel execution mode
     */
    public void setParallel(boolean parallel) {
        this.useParallel = parallel;
    }

    /**
     * Clear all results
     */
    public void clearResults() {
        results.clear();
    }

    /**
     * Clear all strategies and configurations
     */
    public void reset() {
        strategies.clear();
        configurations.clear();
        results.clear();
    }

    /**
     * Shutdown executor service
     */
    public void shutdown() {
        executorService.shutdown();
    }

    private String generateResultKey(String strategyName, String pair) {
        return strategyName + "_" + pair;
    }

    /**
     * Filter interface for backtest results
     */
    public interface BacktestResultFilter {
        boolean matches(BacktestResult result);
    }

    /**
     * Statistics aggregator for backtest results
     */
    public static class BacktestStatistics {
        private final Collection<BacktestResult> results;

        public BacktestStatistics(Collection<BacktestResult> results) {
            this.results = results;
        }

        public double getAverageReturn() {
            return results.stream()
                    .mapToDouble(BacktestResult::getReturnPercent)
                    .average()
                    .orElse(0.0);
        }

        public double getAverageSharpeRatio() {
            return results.stream()
                    .mapToDouble(BacktestResult::getSharpeRatio)
                    .average()
                    .orElse(0.0);
        }

        public double getAverageWinRate() {
            return results.stream()
                    .mapToDouble(BacktestResult::getWinRate)
                    .average()
                    .orElse(0.0);
        }

        public double getAverageMaxDrawdown() {
            return results.stream()
                    .mapToDouble(BacktestResult::getMaxDrawdown)
                    .average()
                    .orElse(0.0);
        }

        public int getTotalTrades() {
            return results.stream()
                    .mapToInt(BacktestResult::getTotalTrades)
                    .sum();
        }

        public double getTotalProfit() {
            return results.stream()
                    .mapToDouble(BacktestResult::getTotalProfit)
                    .sum();
        }

        @Override
        public String toString() {
            return String.format(
                "Statistics{avgReturn=%.2f%%, avgSharpe=%.2f, avgWinRate=%.2f%%, totalTrades=%d}",
                getAverageReturn(), getAverageSharpeRatio(), getAverageWinRate() * 100, getTotalTrades()
            );
        }
    }

    /**
     * Print results summary
     */
    public void printSummary() {
        System.out.println("\n=== BACKTEST RESULTS ===");
        results.forEach((key, result) -> {
            System.out.println(key + ": " + result);
        });
        System.out.println("\n" + getStatistics());
    }
}
