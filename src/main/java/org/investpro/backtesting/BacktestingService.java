package org.investpro.backtesting;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.models.trading.TradePair;
import org.investpro.repository.HistoricalDataRepository;
import org.investpro.repository.HistoricalDataRepositoryImpl;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Service for managing and executing backtesting operations.
 * Manages historical data storage and backtesting execution with persistent
 * data caching.
 */
@Slf4j
@Getter
@Setter
@ToString
public class BacktestingService {
    private BackTesting backtestEngine;
    private Map<String, List<CandleData>> historicalDataCache;
    private HistoricalDataRepository historicalDataRepository;

    public BacktestingService() {
        this.backtestEngine = new BackTesting();
        this.historicalDataCache = new HashMap<>();
        this.historicalDataRepository = new HistoricalDataRepositoryImpl();
        log.info("BacktestingService initialized with persistent historical data storage");
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
        // Store historical data for future reference
        storeHistoricalData(pair, start, end, "1h", historicalData);

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
     * Store historical data persistently for future backtests.
     * Data is stored in JSON format and can be retrieved without fetching from
     * exchange again.
     * 
     * @param pair      The trading pair
     * @param startTime Start of the data range
     * @param endTime   End of the data range
     * @param timeframe The timeframe/granularity (e.g., "1m", "5m", "1h", "1d")
     * @param data      The candle data to store
     */
    public void storeHistoricalData(TradePair pair, LocalDateTime startTime, LocalDateTime endTime,
            String timeframe, List<CandleData> data) {
        try {
            historicalDataRepository.saveHistoricalData(pair, startTime, endTime, timeframe, data);
            String cacheKey = pair.getSymbol() + "_" + timeframe;
            historicalDataCache.put(cacheKey, new ArrayList<>(data));
            log.info("Stored {} historical data points for {}/{}", data.size(), pair.getSymbol(), timeframe);
        } catch (Exception e) {
            log.error("Failed to store historical data for {}", pair.getSymbol(), e);
        }
    }

    /**
     * Retrieve stored historical data for backtesting.
     * Checks persistent storage before attempting to fetch from exchange.
     * 
     * @param pair      The trading pair
     * @param startTime Start of the data range
     * @param endTime   End of the data range
     * @param timeframe The timeframe/granularity
     * @return Optional containing the historical data if available
     */
    public Optional<List<CandleData>> getStoredHistoricalData(TradePair pair, LocalDateTime startTime,
            LocalDateTime endTime, String timeframe) {
        try {
            return historicalDataRepository.getHistoricalData(pair, startTime, endTime, timeframe);
        } catch (java.sql.SQLException e) {
            return Optional.empty();
        }
    }

    /**
     * Check if historical data is available for the given parameters.
     * 
     * @param pair      The trading pair
     * @param startTime Start of the data range
     * @param endTime   End of the data range
     * @param timeframe The timeframe
     * @return true if data is stored and available
     */
    public boolean hasStoredData(TradePair pair, LocalDateTime startTime, LocalDateTime endTime, String timeframe) {
        try {
            return historicalDataRepository.hasData(pair, startTime, endTime, timeframe);
        } catch (java.sql.SQLException e) {
            return false;
        }
    }

    /**
     * Cache historical data for multiple backtests (in-memory)
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
     * Clear in-memory cache
     */
    public void clearCache() {
        historicalDataCache.clear();
    }

    /**
     * Get total number of stored historical data points
     */
    public long getStoredDataPointCount() {
        return historicalDataRepository.getDataPointCount();
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
            System.out.printf(
                    "%s: Return=%.2f%% | Sharpe=%.2f | WinRate=%.2f%% | Trades=%d | MaxDD=%.2f%%%n",
                    key, result.getReturnPercent(), result.getSharpeRatio(),
                    result.getWinRate() * 100, result.getTotalTrades(), result.getMaxDrawdown());
        });
    }

    /**
     * Container for multiple backtest results
     */
    public static class BacktestSuiteResult {
        private final Map<String, BacktestResult> results;

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
                System.out.printf(
                        "%-20s: Return=%7.2f%% | Sharpe=%6.2f | WinRate=%6.2f%% | Trades=%4d | MaxDD=%6.2f%%%n",
                        name, result.getReturnPercent(), result.getSharpeRatio(),
                        result.getWinRate() * 100, result.getTotalTrades(), result.getMaxDrawdown());
            });

            BacktestResult best = getBestResult();
            if (best != null) {
                System.out.printf("\n✓ Best Strategy: %s with %.2f%% return%n",
                        best.getStrategyName(), best.getReturnPercent());
            }
        }
    }
}
