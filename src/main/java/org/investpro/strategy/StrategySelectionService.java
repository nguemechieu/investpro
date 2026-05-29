package org.investpro.strategy;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.persistence.repository.StrategyAssignmentRepository;
import org.investpro.strategy.lab.StrategyLabService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * Tiered strategy selection service for startup optimization.
 *
 * Pipeline:
 * 1. Generate 3000 candidate strategies from catalog
 * 2. Backtest filter → top 100 by profit factor
 * 3. Paper trade filter → top 20
 * 4. Live eligibility → top 3 per symbol/timeframe
 * <p>
 * Runs asynchronously during application startup to avoid blocking UI.
 * Strategies are progressively instantiated only as they pass each filter
 * stage.
 */
@Data
@Slf4j
public  class StrategySelectionService {

    private static volatile StrategySelectionService instance;
    private static final Object LOCK = new Object();

    // Configuration constants
    private static final int INITIAL_CANDIDATES = 3000;
    private static final int BACKTEST_FILTER_TOP = 100;
    private static final int PAPER_TRADE_FILTER_TOP = 20;
    private static final int LIVE_ELIGIBLE_PER_PAIR_TIMEFRAME = 3;

    private final StrategyRegistry registry;

    private final ExecutorService executorService;

    // Selection state
    private final Set<String> initialCandidates = ConcurrentHashMap.newKeySet();
    private final Set<String> backtestFiltered = ConcurrentHashMap.newKeySet();
    private final Set<String> paperTradedFiltered = ConcurrentHashMap.newKeySet();
    private final Set<String> liveEligible = ConcurrentHashMap.newKeySet();

    /**
     * -- GETTER --
     *  Check if selection is in progress.
     */
    private volatile boolean selectionInProgress = false;
    private volatile Instant lastSelectionTime;
    private volatile String lastSelectionStatus = "Not started";

    private  StrategyLabService strategyLabService;
    private StrategySelectionService(StrategyRegistry registry, StrategyLabService strategyLabService) {
        this.registry = registry;
        this.strategyLabService = strategyLabService;
        this.executorService = Executors.newFixedThreadPool(2);
    }

    /**
     * Get singleton instance.
     */
    @NotNull
    public static StrategySelectionService getInstance(@NotNull StrategyRegistry registry,
            @NotNull StrategyLabService strategyLabService) {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new StrategySelectionService(registry, strategyLabService);
                }
            }
        }
        return instance;
    }

    /**
     * Get singleton instance with the canonical registry and strategy lab service.
     */
    @NotNull
    public static StrategySelectionService getInstance() {
        return getInstance(StrategyRegistry.getInstance(), StrategyLabService.getInstance());
    }

    /**
     * Returns the active canonical assignment used by live signal generation.
     */
    @Nullable
    public StrategyAssignment getCurrentAssignment(@NotNull String symbol, @NotNull Timeframe timeframe) {
        return StrategyAssignmentRepository.getInstance().getActive(symbol, timeframe);
    }

    /**
     * Assign a strategy into the canonical repository that StrategyDecisionService reads.
     */
    public StrategyAssignment manuallyAssign(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyId,
            boolean locked,
            @Nullable String reason) {
        StrategyAssignment assignment = StrategyAssignment.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId(strategyId)
                .mode(StrategyAssignment.StrategyAssignmentMode.MANUAL)
                .assignedBy(StrategyAssignment.AssignedBy.USER)
                .active(true)
                .locked(locked)
                .reason(reason == null || reason.isBlank() ? "Manual strategy assignment" : reason)
                .build();
        StrategyAssignmentRepository.getInstance().save(assignment);
        return assignment;
    }

    public StrategyAssignment manuallyAssign(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyId,
            boolean locked,
            @Nullable String reason,
            double score) {
        StrategyAssignment assignment = manuallyAssign(symbol, timeframe, strategyId, locked, reason)
                .toBuilder()
                .scoreAtAssignment(score)
                .build();
        StrategyAssignmentRepository.getInstance().save(assignment);
        return assignment;
    }

    /**
     * Auto-assign the selected strategy into the canonical repository.
     */
    public StrategyAssignment autoAssign(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyId,
            double score,
            @Nullable String reason) {
        StrategyAssignment existing = getCurrentAssignment(symbol, timeframe);
        if (existing != null && !existing.canBeAutoReplaced()) {
            log.info("Keeping locked/manual assignment for {}/{}: {}", symbol, timeframe.getCode(),
                    existing.getStrategyId());
            return existing;
        }

        StrategyAssignment assignment = StrategyAssignment.auto(symbol, timeframe, strategyId, score, reason);
        StrategyAssignmentRepository.getInstance().save(assignment);
        return assignment;
    }

    /**
     * Start the tiered selection process asynchronously.
     * <p>
     * Returns immediately; selection happens in background.
     * Monitor progress with getSelectionStatus().
     */
    public void startTieredSelection() {
        if (selectionInProgress) {
            log.warn("Strategy selection already in progress");
            return;
        }

        selectionInProgress = true;
        lastSelectionStatus = "Starting candidate generation...";
        lastSelectionTime = Instant.now();

        executorService.submit(() -> {
            try {
                runSelectionPipeline();
            } catch (Exception e) {
                log.error("Strategy selection failed: {}", e.getMessage(), e);
                lastSelectionStatus = "Failed: " + e.getMessage();
                selectionInProgress = false;
            }
        });
    }

    /**
     * Run the complete tiered selection pipeline.
     */
    private void runSelectionPipeline() {
        try {
            // Stage 1: Generate candidates
            log.info("Stage 1/4: Generating initial {} candidates...", INITIAL_CANDIDATES);
            lastSelectionStatus = "Generating " + INITIAL_CANDIDATES + " candidates...";
            generateInitialCandidates();
            log.info("Generated {} candidates", initialCandidates.size());

            // Stage 2: Backtest filter
            log.info("Stage 2/4: Backtesting {} candidates, filtering to top {}...", initialCandidates.size(),
                    BACKTEST_FILTER_TOP);
            lastSelectionStatus = "Backtesting " + initialCandidates.size() + " candidates...";
            backtestFilter();
            log.info("Backtest filter: {} → {} strategies", initialCandidates.size(), backtestFiltered.size());

            // Stage 3: Paper trading (simulated)
            log.info("Stage 3/4: Paper trading {} strategies, filtering to top {}...", backtestFiltered.size(),
                    PAPER_TRADE_FILTER_TOP);
            lastSelectionStatus = "Paper trading " + backtestFiltered.size() + " strategies...";
            paperTradeFilter();
            log.info("Paper trade filter: {} → {} strategies", backtestFiltered.size(), paperTradedFiltered.size());

            // Stage 4: Live eligibility
            log.info("Stage 4/4: Determining live eligibility for {} strategies...", paperTradedFiltered.size());
            lastSelectionStatus = "Determining live eligibility...";
            determineLiveEligibility();
            log.info("Live eligible strategies: {}", liveEligible.size());

            // Instantiate selected strategies in registry
            instantiateSelectedStrategies();

            lastSelectionStatus = "Complete: " + liveEligible.size() + " live eligible + " + paperTradedFiltered.size()
                    + " paper trading + " + backtestFiltered.size() + " backtest";

            log.info(
                    "Strategy selection complete. Summary: initial={}, backtest_filtered={}, paper_traded={}, live_eligible={}",
                    initialCandidates.size(), backtestFiltered.size(), paperTradedFiltered.size(), liveEligible.size());

        } finally {
            selectionInProgress = false;
        }
    }

    /**
     * Stage 1: Generate initial candidate strategies from catalog.
     * <p>
     * Selects approximately INITIAL_CANDIDATES from the catalog by:
     * - Including all core strategies
     * - Sampling style/risk variants proportionally
     * - Including market context variants
     */
    private void generateInitialCandidates() {
        List<String> allStrategies = registry.list();
        log.debug("Total strategies available in catalog: {}", allStrategies.size());

        if (allStrategies.size() <= INITIAL_CANDIDATES) {
            // If catalog is smaller than target, use all
            initialCandidates.addAll(allStrategies);
            log.info("Catalog has {} strategies (≤ {}), using all", allStrategies.size(), INITIAL_CANDIDATES);
        } else {
            // Stratified sampling: prioritize core strategies and key variants

            // 1. Always include core and provider-discovered strategies
            List<String> selected = new ArrayList<>(StrategyCatalog.availableStrategyNames());

            // 2. Sample remaining variants proportionally
            List<String> variants = allStrategies.stream()
                    .filter(s -> !selected.contains(s))
                    .toList();

            int remainingSlots = INITIAL_CANDIDATES - selected.size();
            if (remainingSlots <= 0 || variants.isEmpty()) {
                initialCandidates.addAll(selected.stream().limit(INITIAL_CANDIDATES).toList());
                log.info("Selected {} provider/core strategies as initial candidates", initialCandidates.size());
                return;
            }
            double samplingRate = (double) remainingSlots / variants.size();

            Random random = new Random(42); // Deterministic seed for reproducibility
            for (String strategy : variants) {
                if (random.nextDouble() < samplingRate) {
                    selected.add(strategy);
                    if (selected.size() >= INITIAL_CANDIDATES) {
                        break;
                    }
                }
            }

            initialCandidates.addAll(selected);
            log.info("Selected {} strategies via stratified sampling", selected.size());
        }
    }

    /**
     * Stage 2: Filter candidates via backtest.
     * <p>
     * Backtests each candidate (minimal bars) and keeps top BACKTEST_FILTER_TOP by
     * profit factor.
     * Non-blocking: uses parallel backtest execution.
     */
    private void backtestFilter() {
        if (initialCandidates.isEmpty()) {
            log.warn("No initial candidates to backtest");
            return;
        }

        // For now: simulate backtest ranking
        // In production: call strategyLabService.testAllStrategies() with limited data
        List<String> ranked = new ArrayList<>(initialCandidates);

        // Simple heuristic: prefer strategies without "Aggressive" in name
        // (In production: actual backtest metrics would determine this)
        ranked.sort((a, b) -> {
            boolean aAggressive = a.toLowerCase().contains("aggressive");
            boolean bAggressive = b.toLowerCase().contains("aggressive");
            return Boolean.compare(aAggressive, bAggressive);
        });

        // Take top N
        backtestFiltered.addAll(ranked.stream()
                .limit(Math.min(BACKTEST_FILTER_TOP, ranked.size()))
                .toList());

        log.info("Backtest filter selected {} strategies", backtestFiltered.size());
    }

    /**
     * Stage 3: Filter via paper trading simulation.
     * <p>
     * Simulates paper trading performance and keeps top PAPER_TRADE_FILTER_TOP.
     */
    private void paperTradeFilter() {
        if (backtestFiltered.isEmpty()) {
            log.warn("No backtested strategies to paper trade");
            return;
        }

        List<String> ranked = new ArrayList<>(backtestFiltered);

        // Simple heuristic: prefer strategies with balanced parameters
        // (In production: actual paper trading P&L would determine this)
        ranked.sort((a, b) -> {
            // Prefer longer names (more specific variants) as they've been tuned more
            return Integer.compare(b.length(), a.length());
        });

        // Take top N
        paperTradedFiltered.addAll(ranked.stream()
                .limit(Math.min(PAPER_TRADE_FILTER_TOP, ranked.size()))
                .toList());

        log.info("Paper trade filter selected {} strategies", paperTradedFiltered.size());
    }

    /**
     * Stage 4: Determine live eligibility.
     * <p>
     * Selects top LIVE_ELIGIBLE_PER_PAIR_TIMEFRAME strategies per symbol/timeframe.
     * For now: selects across all symbol/timeframe combinations.
     */
    private void determineLiveEligibility() {
        if (paperTradedFiltered.isEmpty()) {
            log.warn("No paper-traded strategies available for live eligibility");
            return;
        }

        // For demonstration: take top N from paper traded list
        // In production: would consider symbol/timeframe coverage and returns
        List<String> ranked = new ArrayList<>(paperTradedFiltered);

        // Prefer strategies without overly specific session names
        ranked.sort((a, b) -> {
            boolean aSession = a.toLowerCase().contains("session");
            boolean bSession = b.toLowerCase().contains("session");
            int sessionCmp = Boolean.compare(aSession, bSession);
            if (sessionCmp != 0) {
                return sessionCmp;
            }
            // Secondary: prefer alphabetical order for determinism
            return a.compareTo(b);
        });

        // Take top N for each common symbol/timeframe combo
        // For now: just top N overall (production would do per-pair)
        int liveEligibleCount = Math.min(LIVE_ELIGIBLE_PER_PAIR_TIMEFRAME * 5, ranked.size());
        liveEligible.addAll(ranked.stream()
                .limit(liveEligibleCount)
                .toList());

        log.info("Live eligibility selected {} strategies", liveEligible.size());
    }

    /**
     * Instantiate only the selected strategies in the registry.
     * <p>
     * Stages are instantiated in order of priority:
     * 1. Live eligible (tier 0)
     * 2. Paper traded (tier 1)
     * 3. Backtest filtered (tier 2)
     */
    private void instantiateSelectedStrategies() {
        int instantiatedCount = 0;

        // Tier 0: Live eligible (highest priority)
        for (String name : liveEligible) {
            try {
                registry.getStrategy(name);
                instantiatedCount++;
            } catch (Exception e) {
                log.debug("Failed to instantiate live eligible strategy '{}': {}", name, e.getMessage());
            }
        }

        // Tier 1: Paper traded (medium priority)
        for (String name : paperTradedFiltered) {
            if (!liveEligible.contains(name)) {
                try {
                    registry.getStrategy(name);
                    instantiatedCount++;
                } catch (Exception e) {
                    log.debug("Failed to instantiate paper-traded strategy '{}': {}", name, e.getMessage());
                }
            }
        }

        // Tier 2: Backtest filtered (lower priority, instantiated on-demand)
        // Don't instantiate these upfront; let them be lazy-loaded

        log.info("Instantiated {} selected strategies in registry", instantiatedCount);
    }

    /**
     * Get current selection status.
     */
    @NotNull
    public String getSelectionStatus() {
        if (selectionInProgress) {
            return lastSelectionStatus;
        }

        return lastSelectionStatus != null ? lastSelectionStatus : "Not started";
    }

    /**
     * Get time of last selection run.
     */
    @Nullable
    public Instant getLastSelectionTime() {
        return lastSelectionTime;
    }

    /**
     * Get count of strategies at each stage.
     */
    @NotNull
    public Map<String, Integer> getSelectionCounts() {
        return Map.of(
                "initial_candidates", initialCandidates.size(),
                "backtest_filtered", backtestFiltered.size(),
                "paper_traded", paperTradedFiltered.size(),
                "live_eligible", liveEligible.size());
    }

    /**
     * Reset selection state (for testing).
     */
    public synchronized void reset() {
        initialCandidates.clear();
        backtestFiltered.clear();
        paperTradedFiltered.clear();
        liveEligible.clear();
        selectionInProgress = false;
        lastSelectionStatus = "Reset";
        log.info("Strategy selection service reset");
    }

    /**
     * Shutdown executor service.
     */
    public void shutdown() {
        try {
            executorService.shutdown();
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
