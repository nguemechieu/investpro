package org.investpro.strategy;

import lombok.extern.slf4j.Slf4j;
import org.investpro.models.trading.TradePair;

import java.time.Instant;
import java.util.*;

/**
 * StrategySelectionService manages strategy assignments to trading pairs and
 * timeframes.
 *
 * Responsibilities:
 * - Query and manage current strategy assignments
 * - Support manual strategy assignment (with optional lock)
 * - Track assignment history for audit
 * - Disable strategies when needed
 * - Store and retrieve backtest results
 * - Rank strategies based on performance
 *
 * Singleton pattern: Use getInstance() to access.
 *
 * Thread-safe for concurrent access.
 */
@Slf4j
public class StrategySelectionService {
    private static volatile StrategySelectionService instance;
    private final StrategyAssignmentRepository assignmentRepository;

    // In-memory cache of assignments
    private final Map<String, StrategyAssignment> assignmentCache = Collections.synchronizedMap(
            new LinkedHashMap<>());

    // In-memory history of assignment changes
    private final List<HistoryEntry> assignmentHistory = Collections.synchronizedList(
            new ArrayList<>());

    // Backtest results cache: key = symbol + "_" + timeframe
    private final Map<String, List<StrategyBacktestResult>> backtestResults = Collections
            .synchronizedMap(new LinkedHashMap<>());

    private StrategySelectionService() {
        this.assignmentRepository = StrategyAssignmentRepository.getInstance();
        loadAssignmentsFromRepository();
    }

    /**
     * Get singleton instance.
     *
     * @return StrategySelectionService instance
     */
    public static StrategySelectionService getInstance() {
        if (instance == null) {
            synchronized (StrategySelectionService.class) {
                if (instance == null) {
                    instance = new StrategySelectionService();
                }
            }
        }
        return instance;
    }

    /**
     * Get current strategy assignment for symbol/timeframe.
     *
     * @param symbol    Trading symbol (e.g., BTC/USD)
     * @param timeframe Timeframe (e.g., 1h, 4h)
     * @return StrategyAssignment if exists and valid, null otherwise
     */
    public StrategyAssignment getCurrentAssignment(String symbol, String timeframe) {
        String key = getCacheKey(symbol, timeframe);
        StrategyAssignment assignment = assignmentCache.get(key);

        if (assignment != null && assignment.isValid() &&
                assignment.getMode() != StrategyAssignment.StrategyAssignmentMode.DISABLED) {
            return assignment;
        }

        // Try to load from repository if not in cache
        assignment = assignmentRepository.getAssignment(symbol, timeframe);
        if (assignment != null) {
            assignmentCache.put(key, assignment);
        }

        return assignment;
    }

    /**
     * Manually assign a strategy to symbol/timeframe.
     *
     * @param symbol     Trading symbol
     * @param timeframe  Timeframe
     * @param strategyId Strategy ID to assign
     * @param locked     If true, prevents auto-replacement
     * @param reason     Reason for manual assignment
     */
    public synchronized void manuallyAssign(
            String symbol, String timeframe, String strategyId,
            boolean locked, String reason) {

        // Verify strategy exists
        TradingStrategy strategy = StrategyRegistry.getInstance().getStrategy(strategyId);
        if (strategy == null) {
            log.warn("Cannot assign unknown strategy: {}", strategyId);
            return;
        }

        StrategyAssignment assignment = StrategyAssignment.builder()
                .assignmentId(StrategyAssignment.generateId())
                .symbol(symbol)
                .strategyId(strategyId)
                .mode(StrategyAssignment.StrategyAssignmentMode.MANUAL)
                .assignedBy(StrategyAssignment.AssignedBy.USER)
                .locked(locked)
                .reason(reason)
                .active(true)
                .assignedAt(Instant.now())
                .build();

        String key = getCacheKey(symbol, timeframe);
        assignmentCache.put(key, assignment);
        assignmentRepository.save(assignment);

        // Record in history
        recordHistory(symbol, timeframe, strategyId, "MANUAL_ASSIGN: " + reason);
        log.info("Manually assigned {} to {}/{} (locked={})",
                strategyId, symbol, timeframe, locked);
    }

    /**
     * Disable strategy for symbol/timeframe.
     *
     * @param symbol    Trading symbol
     * @param timeframe Timeframe
     * @param reason    Reason for disabling
     */
    public synchronized void disableStrategy(
            String symbol, String timeframe, String reason) {

        String key = getCacheKey(symbol, timeframe);
        StrategyAssignment current = assignmentCache.get(key);

        if (current == null) {
            current = assignmentRepository.getAssignment(symbol, timeframe);
        }

        if (current != null) {
            StrategyAssignment disabled = current.toBuilder()
                    .mode(StrategyAssignment.StrategyAssignmentMode.DISABLED)
                    .reason(reason)
                    .build();

            assignmentCache.put(key, disabled);
            assignmentRepository.save(disabled);
            recordHistory(symbol, timeframe, current.getStrategyId(),
                    "DISABLED: " + reason);

            log.info("Disabled strategy for {}/{}: {}", symbol, timeframe, reason);
        }
    }

    /**
     * Store backtest result for later ranking/analysis.
     *
     * @param result StrategyBacktestResult to store
     */
    public void storeBacktestResult(StrategyBacktestResult result) {
        if (result == null) {
            return;
        }

        String key = getCacheKey(result.getSymbol(), result.getTimeframe());
        backtestResults.computeIfAbsent(key, k -> new ArrayList<>()).add(result);

        log.info("Stored backtest result for {} {} (strategy={})",
                result.getSymbol(), result.getTimeframe(), result.getStrategyId());
    }

    /**
     * Get all backtest results for symbol/timeframe.
     *
     * @param symbol    Trading symbol
     * @param timeframe Timeframe
     * @return List of StrategyBacktestResult (may be empty)
     */
    public List<StrategyBacktestResult> getBacktestResults(String symbol, String timeframe) {
        String key = getCacheKey(symbol, timeframe);
        return backtestResults.getOrDefault(key, List.of());
    }

    /**
     * Get assignment history for symbol/timeframe.
     *
     * @param symbol    Trading symbol
     * @param timeframe Timeframe
     * @return StrategyAssignmentHistory with all changes
     */
    public StrategyAssignmentHistory getAssignmentHistory(String symbol, String timeframe) {
        String symbol_tf = symbol + "/" + timeframe;

        List<HistoryEntry> filtered = assignmentHistory.stream()
                .filter(h -> h.symbolTimeframe.equals(symbol_tf))
                .toList();

        return new StrategyAssignmentHistory(filtered);
    }

    /**
     * Load all assignments from repository into cache.
     */
    private void loadAssignmentsFromRepository() {
        // Note: StrategyAssignmentRepository.getAll() may not exist yet
        // This is a placeholder for future database integration
        log.debug("Loading assignments from repository (currently empty)");
    }

    /**
     * Record a history entry for audit trail.
     */
    private void recordHistory(String symbol, String timeframe, String strategyId, String note) {
        HistoryEntry entry = new HistoryEntry(
                symbol + "/" + timeframe,
                strategyId,
                note,
                Instant.now());
        assignmentHistory.add(entry);
    }

    /**
     * Generate cache key for symbol/timeframe.
     */
    private String getCacheKey(String symbol, String timeframe) {
        return symbol + "_" + timeframe;
    }

    /**
     * History entry for audit trail.
     */
    public static class HistoryEntry {
        private final String symbolTimeframe;
        private final String strategyId;
        private final String note;
        private final Instant timestamp;

        public HistoryEntry(String symbolTimeframe, String strategyId, String note, Instant timestamp) {
            this.symbolTimeframe = symbolTimeframe;
            this.strategyId = strategyId;
            this.note = note;
            this.timestamp = timestamp;
        }

        public String getSymbolTimeframe() {
            return symbolTimeframe;
        }

        public String getStrategyId() {
            return strategyId;
        }

        public String getNote() {
            return note;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return String.format("[%s] %s -> %s: %s", timestamp, symbolTimeframe, strategyId, note);
        }
    }

    /**
     * Assignment history for a symbol/timeframe.
     */
    public static class StrategyAssignmentHistory {
        private final List<HistoryEntry> entries;

        public StrategyAssignmentHistory(List<HistoryEntry> entries) {
            this.entries = new ArrayList<>(entries);
        }

        public List<HistoryEntry> getEntries() {
            return new ArrayList<>(entries);
        }

        public int size() {
            return entries.size();
        }
    }
}
