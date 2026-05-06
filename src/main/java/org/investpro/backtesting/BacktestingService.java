package org.investpro.backtesting;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing and executing backtesting operations
 */
@Slf4j
@Getter
@Setter
public class BacktestingService {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BacktestingService.class);
    private BackTesting backtestEngine;
    private Map<String, List<CandleData>> historicalDataCache;

    public BacktestingService() {
        this.backtestEngine = new BackTesting();
        this.historicalDataCache = new HashMap<>();
    }

    /**
     * Create a new backtest configuration
     */
    public BacktestConfig createConfig(TradePair pair, LocalDateTime start, LocalDateTime end,
            double initialBalance) {
        return new BacktestConfig(pair, start, end, initialBalance);
    }

    /**
     * Run backtest with a single strategy
     */
    public BacktestResult runBacktest(BacktestStrategy strategy, BacktestConfig config,
            List<CandleData> historicalData) {
        Simulator simulator = new Simulator(strategy, config);
        return simulator.run(historicalData);
    }

    /**
     * Run backtest with multiple strategies
     */
    public Map<String, BacktestResult> runMultipleBacktests(List<BacktestStrategy> strategies,
            List<BacktestConfig> configs,
            List<CandleData> historicalData) {
        backtestEngine.reset();

        for (BacktestStrategy strategy : strategies) {
            backtestEngine.addStrategy(strategy);
        }

        for (BacktestConfig config : configs) {
            backtestEngine.addConfiguration(config);
        }

        return backtestEngine.runBacktest(historicalData);
    }

    /**
     * Create and run a standard backtest suite
     */
    public BacktestSuiteResult runStandardSuite(TradePair pair, LocalDateTime start, LocalDateTime end,
            double initialBalance, List<CandleData> historicalData) {
        BacktestConfig config = createConfig(pair, start, end, initialBalance);
        BacktestSuiteResult suiteResult = new BacktestSuiteResult();

        // Test Stochastic strategy
        BacktestStrategy stochasticStrategy = new StochasticBacktestStrategy(config);
        BacktestResult stochasticResult = runBacktest(stochasticStrategy, config, historicalData);
        suiteResult.addResult("Stochastic", stochasticResult);

        // Test Simple MA strategy
        BacktestStrategy maStrategy = new SimpleMABacktestStrategy(config);
        BacktestResult maResult = runBacktest(maStrategy, config, historicalData);
        suiteResult.addResult("Simple MA", maResult);

        // Test Volatility strategy
        BacktestStrategy volatilityStrategy = new VolatilityBacktestStrategy(config);
        BacktestResult volatilityResult = runBacktest(volatilityStrategy, config, historicalData);
        suiteResult.addResult("Volatility", volatilityResult);

        return suiteResult;
    }

    /**
     * Cache historical data for multiple backtests
     */
    public void cacheHistoricalData(String key, List<CandleData> data) {
        historicalDataCache.put(key, new ArrayList<>(data));
    }

    /**
     * Get cached historical data
     */
    public List<CandleData> getCachedData(String key) {
        return historicalDataCache.get(key);
    }

    /**
     * Clear cache
     */
    public void clearCache() {
        historicalDataCache.clear();
    }

    /**
     * Optimize strategy parameters
     */
    public BacktestResult optimizeStrategy(BacktestStrategy strategy, BacktestConfig config,
            List<CandleData> historicalData,
            Map<String, Object[]> parameterRanges) {
        final BacktestResult[] bestResult = { null };
        final double[] bestReturn = { Double.NEGATIVE_INFINITY };

        optimizeRecursive(strategy, config, historicalData, parameterRanges,
                new HashMap<>(), parameterRanges.keySet().iterator(),
                result -> {
                    if (result.getReturnPercent() > bestReturn[0]) {
                        bestReturn[0] = result.getReturnPercent();
                        bestResult[0] = result;
                    }
                });

        return bestResult[0];
    }

    private void optimizeRecursive(BacktestStrategy strategy, BacktestConfig config,
            List<CandleData> historicalData,
            Map<String, Object[]> parameterRanges,
            Map<String, Object> currentParams,
            Iterator<String> paramIterator,
            java.util.function.Consumer<BacktestResult> callback) {
        if (!paramIterator.hasNext()) {
            // Set current parameters and run backtest
            currentParams.forEach(strategy::setParameter);
            BacktestResult result = runBacktest(strategy, config, historicalData);
            callback.accept(result);
            return;
        }

        String paramName = paramIterator.next();
        Object[] values = parameterRanges.get(paramName);

        for (Object value : values) {
            currentParams.put(paramName, value);
            optimizeRecursive(strategy, config, historicalData, parameterRanges,
                    currentParams, paramIterator, callback);
        }
    }

    /**
     * Print results summary
     */
    public void printResults(Map<String, BacktestResult> results) {
        System.out.println("\n=== BACKTEST RESULTS ===");
        results.forEach((key, result) -> {
            System.out.println(String.format(
                    "%s: Return=%.2f%% | Sharpe=%.2f | WinRate=%.2f%% | Trades=%d | MaxDD=%.2f%%",
                    key, result.getReturnPercent(), result.getSharpeRatio(),
                    result.getWinRate() * 100, result.getTotalTrades(), result.getMaxDrawdown()));
        });
    }

    /**
     * Container for multiple backtest results
     */
    public static class BacktestSuiteResult {
        private Map<String, BacktestResult> results;

        public BacktestSuiteResult() {
            this.results = new LinkedHashMap<>();
        }

        public void addResult(String strategyName, BacktestResult result) {
            results.put(strategyName, result);
        }

        public BacktestResult getResult(String strategyName) {
            return results.get(strategyName);
        }

        public Map<String, BacktestResult> getAllResults() {
            return new HashMap<>(results);
        }

        public BacktestResult getBestResult() {
            return results.values().stream()
                    .max(Comparator.comparingDouble(BacktestResult::getReturnPercent))
                    .orElse(null);
        }

        public void printSummary() {
            System.out.println("\n=== BACKTEST SUITE RESULTS ===");
            results.forEach((name, result) -> {
                System.out.println(String.format(
                        "%-20s: Return=%7.2f%% | Sharpe=%6.2f | WinRate=%6.2f%% | Trades=%4d | MaxDD=%6.2f%%",
                        name, result.getReturnPercent(), result.getSharpeRatio(),
                        result.getWinRate() * 100, result.getTotalTrades(), result.getMaxDrawdown()));
            });

            BacktestResult best = getBestResult();
            if (best != null) {
                System.out.println(String.format("\n✓ Best Strategy: %s with %.2f%% return",
                        best.getStrategyName(), best.getReturnPercent()));
            }
        }
    }
}
