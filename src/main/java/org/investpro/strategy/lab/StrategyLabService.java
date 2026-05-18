package org.investpro.strategy.lab;

import lombok.extern.slf4j.Slf4j;
import org.investpro.data.CandleData;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySelectionService;
import org.investpro.models.trading.TradePair;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.repository.StrategyAssignmentRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Main service orchestrating all Strategy Lab operations.
 *
 * Responsibilities:
 * - Run backtests for multiple strategies and timeframes
 * - Rank results by performance
 * - Generate consensus from votes
 * - Assign best strategies automatically
 * - Manage assignments
 * - Provide snapshots for UI display
 *
 * Runs backtests asynchronously in a dedicated thread pool.
 */
@Slf4j
public class StrategyLabService {

    private static volatile StrategyLabService instance = null;

    private final StrategyBacktestRunner backtestRunner;
    private final StrategyRankingEngine rankingEngine;
    private final StrategyVotingEngine votingEngine;
    private final StrategyAssignmentService assignmentService;

    private final ExecutorService executorService;

    /**
     * Cache of latest rankings by symbol/timeframe.
     */
    private final Map<String, List<StrategyPerformanceReport>> rankingsCache = new ConcurrentHashMap<>();

    /**
     * Cache of latest consensus by symbol/timeframe.
     */
    private final Map<String, StrategyConsensusResult> consensusCache = new ConcurrentHashMap<>();

    public StrategyLabService() {
        this.backtestRunner = new StrategyBacktestRunner();
        this.rankingEngine = new StrategyRankingEngine();
        this.votingEngine = new StrategyVotingEngine();
        this.assignmentService = new StrategyAssignmentService();

        // Create dedicated thread pool for strategy backtests
        this.executorService = Executors.newFixedThreadPool(
                4,
                r -> {
                    Thread t = new Thread(r, "strategy-lab-worker");
                    t.setDaemon(true);
                    return t;
                });

        log.info("StrategyLabService initialized with backtesting engine");
    }

    /**
     * Get singleton instance of StrategyLabService.
     *
     * Thread-safe lazy initialization using double-checked locking.
     */
    public static StrategyLabService getInstance() {
        if (instance == null) {
            synchronized (StrategyLabService.class) {
                if (instance == null) {
                    instance = new StrategyLabService();
                }
            }
        }
        return instance;
    }

    /**
     * Reset singleton (for testing only).
     */
    public static synchronized void resetSingleton() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    /**
     * Test all available strategies on given symbol/timeframes asynchronously.
     *
     * Returns a CompletableFuture to allow async operation without blocking UI.
     */
    public CompletableFuture<List<StrategyPerformanceReport>> testAllStrategies(
            @NotNull String symbol,
            @NotNull List<Timeframe> timeframes) {
        List<String> allStrategyNames = new ArrayList<>(
                StrategyCatalog.availableStrategyNames());

        return testStrategiesAsync(symbol, timeframes, allStrategyNames);
    }

    /**
     * Test all available strategies against real candles for one symbol/timeframe,
     * rank them, vote across the top candidates, and save the selected strategy to
     * the canonical assignment repository used by live signal generation.
     */
    public CompletableFuture<StrategyAssignment> evaluateAndAssignBest(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull List<CandleData> candles) {
        List<String> allStrategyNames = new ArrayList<>(StrategyCatalog.availableStrategyNames());
        return evaluateAndAssignBest(symbol, timeframe, candles, allStrategyNames);
    }

    /**
     * Same as evaluateAndAssignBest, scoped to a strategy subset.
     */
    public CompletableFuture<StrategyAssignment> evaluateAndAssignBest(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull List<CandleData> candles,
            @NotNull List<String> strategyNames) {
        return CompletableFuture.supplyAsync(() -> {
            List<CandleData> cleanCandles = sanitizeCandles(candles);
            if (cleanCandles.size() < 50) {
                log.warn("Cannot evaluate strategies for {}/{}: only {} candles available",
                        symbol, timeframe.getCode(), cleanCandles.size());
                return null;
            }

            List<StrategyPerformanceReport> results = new ArrayList<>();
            for (String strategyName : strategyNames) {
                try {
                    StrategyBacktestRequest request = StrategyBacktestRequest.builder()
                            .symbol(symbol)
                            .timeframe(timeframe)
                            .strategyName(strategyName)
                            .candles(cleanCandles)
                            .initialCapital(10000.0)
                            .commissionRate(0.001)
                            .slippageRate(0.0002)
                            .maxTrades(Integer.MAX_VALUE)
                            .allowShorts(true)
                            .useRiskManagement(true)
                            .build();
                    results.add(backtestRunner.run(request));
                } catch (Exception exception) {
                    log.warn("Strategy evaluation failed for {}/{} strategy={}: {}",
                            symbol, timeframe.getCode(), strategyName, exception.getMessage());
                }
            }

            List<StrategyPerformanceReport> ranked = rankingEngine.rank(results);
            String cacheKey = makeKey(symbol, timeframe);
            rankingsCache.put(cacheKey, ranked);

            StrategyContext context = buildVotingContext(symbol, timeframe, cleanCandles);
            StrategyConsensusResult consensus = votingEngine.vote(symbol, timeframe, context, ranked, 5);
            consensusCache.put(cacheKey, consensus);

            StrategyPerformanceReport selected = selectAssignmentCandidate(ranked, consensus);
            if (selected == null) {
                log.warn("No assignable strategy found for {}/{}", symbol, timeframe.getCode());
                return null;
            }

            double minimumScore = readDoubleProperty("tradeadviser.strategy.minStrategyScore", 60.0);
            if (selected.getScore() < minimumScore) {
                log.warn("Best strategy for {}/{} did not meet minimum score: {} < {}",
                        symbol, timeframe.getCode(), selected.getScore(), minimumScore);
                return null;
            }

            boolean autoAssignBest = Boolean.parseBoolean(
                    System.getProperty("tradeadviser.strategy.autoAssignBest", "true"));
            if (!autoAssignBest) {
                log.info("Auto assignment disabled; ranked best strategy for {}/{} is {} score={}",
                        symbol, timeframe.getCode(), selected.getStrategyName(), selected.getScore());
                return null;
            }

            String reason = "Auto-assigned from real-candle backtest";
            if (consensus != null && consensus.isConsensusReached()
                    && !"NONE".equalsIgnoreCase(consensus.getSelectedStrategyName())) {
                reason = "Consensus assignment: " + consensus.getReason();
            }

            StrategyAssignment assignment = StrategySelectionService.getInstance()
                    .autoAssign(symbol, timeframe, selected.getStrategyName(), selected.getScore(), reason);

            log.info("Canonical strategy assignment created: {}/{} -> {} score={}",
                    symbol, timeframe.getCode(), assignment == null ? "NONE" : assignment.getStrategyId(),
                    selected.getScore());
            return assignment;
        }, executorService);
    }

    /**
     * Test specific strategies on a symbol/timeframe asynchronously.
     */
    public CompletableFuture<List<StrategyPerformanceReport>> testStrategies(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull List<String> strategyNames) {
        return testStrategiesAsync(symbol, List.of(timeframe), strategyNames);
    }

    /**
     * Internal async test executor.
     */
    private CompletableFuture<List<StrategyPerformanceReport>> testStrategiesAsync(
            @NotNull String symbol,
            @NotNull List<Timeframe> timeframes,
            @NotNull List<String> strategyNames) {
        return CompletableFuture.supplyAsync(() -> {
            List<StrategyPerformanceReport> allResults = new ArrayList<>();

            int totalTests = strategyNames.size() * timeframes.size();
            AtomicInteger completed = new AtomicInteger(0);

            for (Timeframe timeframe : timeframes) {
                String cacheKey = makeKey(symbol, timeframe);
                List<StrategyPerformanceReport> results = new ArrayList<>();

                for (String strategyName : strategyNames) {
                    try {
                        log.info(
                                "Testing {}/{}: {} ({}/{})",
                                symbol,
                                timeframe.getCode(),
                                strategyName,
                                completed.incrementAndGet(),
                                totalTests);

                        StrategyBacktestRequest request = StrategyBacktestRequest.builder()
                                .symbol(symbol)
                                .timeframe(timeframe)
                                .strategyName(strategyName)
                                .candles(generateMockCandles()) // TODO: get real historical candles
                                .initialCapital(10000.0)
                                .commissionRate(0.001)
                                .slippageRate(0.0002)
                                .maxTrades(Integer.MAX_VALUE)
                                .allowShorts(true)
                                .useRiskManagement(true)
                                .build();

                        StrategyPerformanceReport report = backtestRunner.run(request);
                        results.add(report);
                        allResults.add(report);

                    } catch (Exception e) {
                        log.error("Backtest failed for {}", strategyName, e);
                    }
                }

                // Rank and cache results for this timeframe
                List<StrategyPerformanceReport> ranked = rankingEngine.rank(results);
                rankingsCache.put(cacheKey, ranked);

                // Generate consensus
                StrategyConsensusResult consensus = votingEngine.vote(
                        symbol,
                        timeframe,
                        null, // No context for backtest consensus
                        ranked,
                        5 // Use top 5 strategies for consensus
                );
                consensusCache.put(cacheKey, consensus);
            }

            return rankingEngine.rank(allResults);

        }, executorService);
    }

    /**
     * Get last ranking for symbol/timeframe.
     */
    public List<StrategyPerformanceReport> getLastRanking(
            @NotNull String symbol,
            @NotNull Timeframe timeframe) {
        String key = makeKey(symbol, timeframe);
        return rankingsCache.getOrDefault(key, List.of());
    }

    /**
     * Get last consensus for symbol/timeframe.
     */
    public StrategyConsensusResult getLastConsensus(
            @NotNull String symbol,
            @NotNull Timeframe timeframe) {
        String key = makeKey(symbol, timeframe);
        return consensusCache.get(key);
    }

    /**
     * Assign best strategy automatically.
     */
    public StrategyAssignment assignBest(
            @NotNull String symbol,
            @NotNull Timeframe timeframe) {
        List<StrategyPerformanceReport> rankings = getLastRanking(symbol, timeframe);
        StrategyPerformanceReport selected = selectAssignmentCandidate(rankings, getLastConsensus(symbol, timeframe));
        if (selected == null) {
            return null;
        }
        return StrategySelectionService.getInstance()
                .autoAssign(symbol, timeframe, selected.getStrategyName(), selected.getScore(),
                        "Auto-assigned from Strategy Lab ranking");
    }

    /**
     * Manually assign a strategy.
     */
    public StrategyAssignment manuallyAssign(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyName) {
        return StrategySelectionService.getInstance()
                .manuallyAssign(symbol, timeframe, strategyName, true, "Manual assignment from Strategy Lab");
    }

    /**
     * Unassign a strategy.
     */
    public void unassign(@NotNull String symbol, @NotNull Timeframe timeframe) {
        StrategyAssignment active = StrategyAssignmentRepository.getInstance().getActive(symbol, timeframe);
        if (active != null) {
            StrategyAssignmentRepository.getInstance().delete(active.getAssignmentId());
        }
    }

    /**
     * Get current snapshot of Strategy Lab state for UI.
     */
    public StrategyLabSnapshot getSnapshot(
            @NotNull String symbol,
            @NotNull Timeframe timeframe) {
        String key = makeKey(symbol, timeframe);

        List<StrategyPerformanceReport> rankings = rankingsCache.getOrDefault(key, List.of());
        StrategyConsensusResult consensus = consensusCache.get(key);
        StrategyAssignment assignment = StrategyAssignmentRepository.getInstance().getActive(symbol, timeframe);

        return StrategyLabSnapshot.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .rankings(rankings)
                .consensus(consensus)
                .activeAssignment(assignment)
                .running(false)
                .progress(1.0)
                .statusMessage("Ready")
                .build();
    }

    /**
     * Get assignment service for direct access.
     */
    public StrategyAssignmentService getAssignmentService() {
        return assignmentService;
    }

    /**
     * Shutdown the service.
     */
    public void shutdown() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Make cache key for symbol/timeframe.
     */
    private String makeKey(String symbol, Timeframe timeframe) {
        return symbol + "/" + timeframe.getCode();
    }

    private List<CandleData> sanitizeCandles(List<CandleData> candles) {
        if (candles == null || candles.isEmpty()) {
            return List.of();
        }
        return candles.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator.comparingLong(CandleData::openTime))
                .toList();
    }

    private StrategyContext buildVotingContext(String symbol, Timeframe timeframe, List<CandleData> candles) {
        CandleData latest = candles.isEmpty() ? null : candles.get(candles.size() - 1);
        double current = latest == null ? 0.0 : latest.closePrice();
        double spread = current > 0.0 ? Math.max(current * 0.0001, 0.00000001) : 0.0;

        return StrategyContext.builder()
                .symbol(parsePair(symbol))
                .timeframe(timeframe)
                .candles(candles)
                .currentPrice(current)
                .bid(current > 0.0 ? current - spread / 2.0 : 0.0)
                .ask(current > 0.0 ? current + spread / 2.0 : 0.0)
                .barsAvailable(candles.size())
                .build();
    }

    private StrategyPerformanceReport selectAssignmentCandidate(
            List<StrategyPerformanceReport> ranked,
            StrategyConsensusResult consensus) {
        if (consensus != null && consensus.isConsensusReached()
                && !"NONE".equalsIgnoreCase(consensus.getSelectedStrategyName())) {
            for (StrategyPerformanceReport report : ranked) {
                if (report != null && Objects.equals(report.getStrategyName(), consensus.getSelectedStrategyName())) {
                    return report;
                }
            }
        }

        StrategyPerformanceReport tradable = rankingEngine.getBestTradable(ranked);
        if (tradable != null) {
            return tradable;
        }

        return ranked == null || ranked.isEmpty() ? null : ranked.getFirst();
    }

    private TradePair parsePair(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return null;
        }
        String[] parts = symbol.replace('_', '/').replace('-', '/').split("/");
        if (parts.length < 2) {
            return null;
        }
        try {
            return new TradePair(parts[0], parts[1]);
        } catch (Exception exception) {
            log.debug("Unable to parse TradePair from {}", symbol, exception);
            return null;
        }
    }

    private double readDoubleProperty(String propertyName, double fallback) {
        String rawValue = System.getProperty(propertyName);
        if (rawValue == null || rawValue.isBlank()) {
            return fallback;
        }
        try {
            return Double.parseDouble(rawValue.trim());
        } catch (NumberFormatException exception) {
            log.warn("Invalid numeric system property {}={}; using {}", propertyName, rawValue, fallback);
            return fallback;
        }
    }

    /**
     * Generate mock candles for testing (TODO: replace with real historical data).
     */
    private List<CandleData> generateMockCandles() {
        List<CandleData> candles = new ArrayList<>();
        double price = 100.0;

        for (int i = 0; i < 200; i++) {
            double change = (Math.random() - 0.5) * 2;
            double open = price;
            double close = price + change;
            double high = Math.max(open, close) + Math.random() * 0.5;
            double low = Math.min(open, close) - Math.random() * 0.5;
            double volume = 1000 + Math.random() * 500;

            candles.add(new CandleData(open, close, high, low, i, volume));
            price = close;
        }

        return candles;
    }
}
