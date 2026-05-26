package org.investpro.strategy.lab;

import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.data.CandleData;
import org.investpro.strategy.StrategyCatalog;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySelectionService;
import org.investpro.models.trading.TradePair;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.persistence.repository.StrategyAssignmentRepository;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.*;

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

    @SuppressWarnings("unused")
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

        // Delegate all concurrent execution to the central BacktestScheduler.
        // The scheduler enforces concurrency limits, queue bounds, priorities,
        // and resource protection – StrategyLabService never creates raw threads.
        this.executorService = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "strategy-lab-service");
            t.setDaemon(true);
            return t;
        });

        log.debug("StrategyLabService initialized");
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
     * Evaluate one symbol across multiple timeframes, save each timeframe ranking,
     * and return the best symbol-level assignment by score. This is the live-trading
     * preparation path: no live orders should depend on a strategy that has not
     * passed this real-candle evaluation first.
     */
    public CompletableFuture<StrategyAssignment> evaluateAndAssignBestAcrossTimeframes(
            @NotNull String symbol,
            @NotNull Map<Timeframe, List<CandleData>> candlesByTimeframe) {
        List<String> allStrategyNames = new ArrayList<>(StrategyCatalog.availableStrategyNames());
        return evaluateAndAssignBestAcrossTimeframes(symbol, candlesByTimeframe, allStrategyNames);
    }

    /**
     * Same as evaluateAndAssignBestAcrossTimeframes, scoped to a strategy subset.
     */
    public CompletableFuture<StrategyAssignment> evaluateAndAssignBestAcrossTimeframes(
            @NotNull String symbol,
            @NotNull Map<Timeframe, List<CandleData>> candlesByTimeframe,
            @NotNull List<String> strategyNames) {
        if (candlesByTimeframe == null || candlesByTimeframe.isEmpty()) {
            log.warn("Cannot evaluate {}: no timeframe candle sets available", symbol);
            return CompletableFuture.completedFuture(null);
        }

        // Launch all timeframe evaluations CONCURRENTLY — no serial blocking.
        List<CompletableFuture<StrategyAssignment>> timeframeFutures = new ArrayList<>();
        for (Map.Entry<Timeframe, List<CandleData>> entry : candlesByTimeframe.entrySet()) {
            Timeframe timeframe = entry.getKey();
            List<CandleData> candles = sanitizeCandles(entry.getValue());
            if (timeframe == null || candles.size() < 50) {
                log.debug("Skipping {}/{} strategy evaluation: candles={}",
                        symbol, timeframe == null ? "unknown" : timeframe.getCode(), candles.size());
                continue;
            }
            // evaluateAndAssignBest already returns a CompletableFuture — compose, don't block
            CompletableFuture<StrategyAssignment> tf = evaluateAndAssignBest(symbol, timeframe, candles, strategyNames)
                    .exceptionally(ex -> {
                        log.warn("Strategy evaluation failed for {}/{}: {}",
                                symbol, timeframe.getCode(), ex.getMessage());
                        return null;
                    });
            timeframeFutures.add(tf);
        }

        if (timeframeFutures.isEmpty()) {
            log.warn("No eligible timeframes for {} after candle validation", symbol);
            return CompletableFuture.completedFuture(null);
        }

        // Wait for all timeframes, then pick the best assignment by score.
        return CompletableFuture.allOf(timeframeFutures.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    StrategyAssignment bestAssignment = null;
                    for (CompletableFuture<StrategyAssignment> f : timeframeFutures) {
                        StrategyAssignment assignment = f.getNow(null);
                        if (assignment != null
                                && (bestAssignment == null
                                || assignment.getScoreAtAssignment() > bestAssignment.getScoreAtAssignment())) {
                            bestAssignment = assignment;
                        }
                    }
                    if (bestAssignment == null) {
                        log.warn("No strategy assignment created for {} after all timeframe backtests", symbol);
                    } else {
                        log.info("Best symbol-level assignment for {}: {} {} score={}",
                                symbol,
                                bestAssignment.getStrategyId(),
                                bestAssignment.getTimeframe().getCode(),
                                bestAssignment.getScoreAtAssignment());
                    }
                    return bestAssignment;
                });
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
            BacktestScheduler scheduler = BacktestScheduler.getInstance();
            List<CompletableFuture<StrategyPerformanceReport>> futures = new ArrayList<>();

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
                    BacktestJob job = scheduler.submit(request, BacktestJobPriority.BACKGROUND);
                    futures.add(job.getFuture());
                } catch (Exception exception) {
                    log.debug("Strategy submission failed for {}/{} strategy={}: {}",
                            symbol, timeframe.getCode(), strategyName, exception.getMessage());
                }
            }

            for (CompletableFuture<StrategyPerformanceReport> future : futures) {
                try {
                    StrategyPerformanceReport r = future.get(2, TimeUnit.MINUTES);
                    if (r != null) {
                        results.add(r);
                    }
                } catch (Exception e) {
                    log.debug("Backtest future error: {}", e.getMessage());
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

            double preferredScore = readDoubleProperty("investpro.strategy.minStrategyScore", 60.0);
            double hardMinimumScore = readDoubleProperty("investpro.strategy.hardMinStrategyScore", 40.0);
            if (selected.getScore() < hardMinimumScore) {
                boolean allowResearchAssignment = AppConfig.getBoolean(
                        "investpro.strategy.allowResearchAssignmentBelowHardMin",
                        true);
                if (!allowResearchAssignment || selected.getTotalTrades() <= 0) {
                    log.warn("Best strategy for {}/{} did not meet hard minimum score: {} < {}",
                            symbol, timeframe.getCode(), selected.getScore(), hardMinimumScore);
                    return null;
                }
                log.warn("Assigning best research strategy for {}/{} below hard minimum: {} < {} strategy={}",
                        symbol, timeframe.getCode(), selected.getScore(), hardMinimumScore, selected.getStrategyName());
            }

            boolean autoAssignBest = AppConfig.getBoolean("investpro.strategy.autoAssignBest", true);
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
            if (selected.getScore() < preferredScore) {
                reason = reason + " (below preferred score %.1f; hard minimum %.1f)"
                        .formatted(preferredScore, hardMinimumScore);
                log.warn("Best strategy for {}/{} is below preferred score but above hard minimum: {} < {}; assigning {}",
                        symbol, timeframe.getCode(), selected.getScore(), preferredScore, selected.getStrategyName());
            }
            if (selected.getScore() < hardMinimumScore) {
                reason = "Research assignment below hard minimum: %.1f < %.1f; paper/backtest only until reviewed. %s"
                        .formatted(selected.getScore(), hardMinimumScore, reason);
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
     *
     * <p>Each strategy/timeframe combination is submitted as a separate
     * {@link BacktestJob} to the {@link BacktestScheduler} which enforces
     * concurrency limits, queue bounds, and resource protection.
     * The outer CompletableFuture resolves when <em>all</em> jobs are done.
     */
    private CompletableFuture<List<StrategyPerformanceReport>> testStrategiesAsync(
            @NotNull String symbol,
            @NotNull List<Timeframe> timeframes,
            @NotNull List<String> strategyNames) {
        return CompletableFuture.supplyAsync(() -> {
            BacktestScheduler scheduler = BacktestScheduler.getInstance();
            List<CompletableFuture<StrategyPerformanceReport>> futures = new ArrayList<>();

            for (Timeframe timeframe : timeframes) {
                for (String strategyName : strategyNames) {
                    try {
                        StrategyBacktestRequest request = StrategyBacktestRequest.builder()
                                .symbol(symbol)
                                .timeframe(timeframe)
                                .strategyName(strategyName)
                                .candles(generateMockCandles())
                                .initialCapital(10000.0)
                                .commissionRate(0.001)
                                .slippageRate(0.0002)
                                .maxTrades(Integer.MAX_VALUE)
                                .allowShorts(true)
                                .useRiskManagement(true)
                                .build();

                        BacktestJob job = scheduler.submit(request, BacktestJobPriority.VISIBLE);
                        futures.add(job.getFuture());
                    } catch (RejectedExecutionException ex) {
                        log.warn("Backtest queue full – skipping {}/{} {}: {}",
                                symbol, timeframe.getCode(), strategyName, ex.getMessage());
                    } catch (Exception e) {
                        log.debug("Failed to submit backtest for {} {}/{}: {}",
                                strategyName, symbol, timeframe.getCode(), e.getMessage());
                    }
                }
            }

            // Collect all results (blocks until each job completes or is cancelled)
            List<StrategyPerformanceReport> allResults = new ArrayList<>();
            for (CompletableFuture<StrategyPerformanceReport> future : futures) {
                try {
                    StrategyPerformanceReport report = future.get();
                    if (report != null) {
                        allResults.add(report);
                    }
                } catch (Exception e) {
                    log.debug("Backtest future failed: {}", e.getMessage());
                }
            }

            // Rank and cache results per timeframe
            for (Timeframe timeframe : timeframes) {
                String cacheKey = makeKey(symbol, timeframe);
                List<StrategyPerformanceReport> forTf = allResults.stream()
                        .filter(r -> timeframe.equals(r.getTimeframe()))
                        .toList();

                List<StrategyPerformanceReport> ranked = rankingEngine.rank(forTf);
                rankingsCache.put(cacheKey, ranked);

                StrategyConsensusResult consensus = votingEngine.vote(
                        symbol, timeframe, null, ranked, 5);
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
        return AppConfig.getDouble(propertyName, fallback);
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
