package org.investpro.service;

import lombok.extern.slf4j.Slf4j;
import org.investpro.research.StrategyBacktestResult;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyAssignmentRepository;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.TradingStrategy;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for selecting and assigning the best strategy for each symbol/timeframe.
 * Manages automatic selection, manual override, and strategy assignment history.
 */
@Slf4j
public class StrategySelectionService {
    private static StrategySelectionService instance;
    private final StrategyRegistry registry;
    private final StrategyAssignmentRepository assignmentRepository;
    private final Map<String, List<StrategyBacktestResult>> backtestResults = new ConcurrentHashMap<>();
    private final Map<String, StrategyAssignmentHistory> assignmentHistory = new ConcurrentHashMap<>();

    private StrategySelectionService() {
        this.registry = StrategyRegistry.getInstance();
        this.assignmentRepository = StrategyAssignmentRepository.getInstance();
    }

    public static synchronized StrategySelectionService getInstance() {
        if (instance == null) {
            instance = new StrategySelectionService();
        }
        return instance;
    }

    /**
     * Gets the currently active assignment for a symbol/timeframe.
     */
    @Nullable
    public StrategyAssignment getCurrentAssignment(@NotNull String symbol, @NotNull Timeframe timeframe) {
        StrategyAssignment assignment = assignmentRepository.getActive(symbol, timeframe);
        if (assignment != null && assignment.isValid()) {
            return assignment;
        }
        return null;
    }

    /**
     * Automatically selects and assigns the best strategy for a symbol/timeframe.
     * Respects manual locks and returns the assigned strategy.
     */
    @NotNull
    public StrategyAssignment selectAndAssign(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull List<StrategyBacktestResult> candidateResults) {

        // Check for existing locked assignment
        StrategyAssignment current = getCurrentAssignment(symbol, timeframe);
        if (current != null && current.isLocked()) {
            log.info("Symbol {} timeframe {} has locked assignment: {}", symbol, timeframe.getCode(), current.getStrategyId());
            return current;
        }

        // Find best strategy from results
        StrategyBacktestResult best = candidateResults.stream()
                .filter(r -> r.meetsQualityStandards())
                .max(Comparator.comparingDouble(r -> r.getScore() != null ? r.getScore().getTotalScore() : 0.0))
                .orElse(null);

        if (best == null) {
            // No strategy meets quality standards - return current or create disabled
            if (current != null) {
                return current;
            }
            log.warn("No strategy meets quality standards for {} {}", symbol, timeframe.getCode());
            return createDisabledAssignment(symbol, timeframe, "No qualifying strategies");
        }

        // Create new assignment
        StrategyAssignment assignment = StrategyAssignment.builder()
                .assignmentId(StrategyAssignment.generateId())
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId(best.getStrategyId())
                .mode(StrategyAssignment.StrategyAssignmentMode.AUTO)
                .assignedBy(StrategyAssignment.AssignedBy.AUTO)
                .scoreAtAssignment(best.getScore() != null ? best.getScore().getTotalScore() : 0.0)
                .reason("Automatic selection based on backtest results")
                .active(true)
                .build();

        assignmentRepository.save(assignment);
        recordHistory(assignment, "Auto-selected best strategy");
        log.info("Auto-assigned strategy for {} {}: {}", symbol, timeframe.getCode(), best.getStrategyId());

        return assignment;
    }

    /**
     * Manually assigns a strategy to a symbol/timeframe with optional lock.
     */
    @NotNull
    public StrategyAssignment manuallyAssign(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyId,
            boolean lock,
            @NotNull String reason) {

        TradingStrategy strategy = registry.getStrategy(strategyId);
        if (strategy == null) {
            throw new IllegalArgumentException("Strategy not found: " + strategyId);
        }

        StrategyAssignment assignment = StrategyAssignment.builder()
                .assignmentId(StrategyAssignment.generateId())
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId(strategyId)
                .mode(StrategyAssignment.StrategyAssignmentMode.MANUAL)
                .assignedBy(StrategyAssignment.AssignedBy.USER)
                .reason(reason)
                .active(true)
                .locked(lock)
                .build();

        assignmentRepository.save(assignment);
        recordHistory(assignment, "Manual assignment" + (lock ? " (locked)" : ""));
        log.info("Manual assignment for {} {}: {} (locked={})", symbol, timeframe.getCode(), strategyId, lock);

        return assignment;
    }

    /**
     * Disables a strategy for a specific symbol/timeframe.
     */
    public void disableStrategy(@NotNull String symbol, @NotNull Timeframe timeframe, @NotNull String reason) {
        StrategyAssignment disabled = createDisabledAssignment(symbol, timeframe, reason);
        assignmentRepository.save(disabled);
        recordHistory(disabled, "Disabled: " + reason);
        log.info("Disabled strategy for {} {}: {}", symbol, timeframe.getCode(), reason);
    }

    /**
     * Stores backtest results for a strategy.
     */
    public void storeBacktestResult(@NotNull StrategyBacktestResult result) {
        String key = result.getSymbol() + "_" + result.getTimeframe().getCode();
        backtestResults.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(result);
    }

    /**
     * Gets all backtest results for a symbol/timeframe.
     */
    @NotNull
    public List<StrategyBacktestResult> getBacktestResults(@NotNull String symbol, @NotNull Timeframe timeframe) {
        String key = symbol + "_" + timeframe.getCode();
        return new ArrayList<>(backtestResults.getOrDefault(key, new ArrayList<>()));
    }

    /**
     * Gets assignment history.
     */
    @NotNull
    public StrategyAssignmentHistory getAssignmentHistory(@NotNull String symbol, @NotNull Timeframe timeframe) {
        String key = symbol + "_" + timeframe.getCode();
        return assignmentHistory.getOrDefault(key, new StrategyAssignmentHistory());
    }

    // Private helpers

    private StrategyAssignment createDisabledAssignment(String symbol, Timeframe timeframe, String reason) {
        return StrategyAssignment.builder()
                .assignmentId(StrategyAssignment.generateId())
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId("NONE")
                .mode(StrategyAssignment.StrategyAssignmentMode.DISABLED)
                .assignedBy(StrategyAssignment.AssignedBy.SYSTEM)
                .reason(reason)
                .active(false)
                .build();
    }

    private void recordHistory(@NotNull StrategyAssignment assignment, String note) {
        String key = assignment.getSymbol() + "_" + assignment.getTimeframe().getCode();
        StrategyAssignmentHistory history = assignmentHistory.computeIfAbsent(key, k -> new StrategyAssignmentHistory());
        history.addEntry(assignment, note);
    }

    /**
     * Simple history tracker for assignment changes.
     */
    public static class StrategyAssignmentHistory {
        private final List<HistoryEntry> entries = Collections.synchronizedList(new ArrayList<>());

        void addEntry(StrategyAssignment assignment, String note) {
            entries.add(new HistoryEntry(assignment.getStrategyId(), assignment.getMode(), Instant.now(), note));
        }

        public List<HistoryEntry> getEntries() {
            return new ArrayList<>(entries);
        }

        public record HistoryEntry(String strategyId, StrategyAssignment.StrategyAssignmentMode mode, Instant timestamp, String note) {
        }
    }
}
