package org.investpro.strategy.lab;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory repository for strategy assignments.
 *
 * Stores and retrieves strategy assignments for symbol/timeframe combinations.
 * Can later be backed by a database or cache.
 *
 * Thread-safe using ConcurrentHashMap.
 */
@Slf4j
public class StrategyAssignmentRepository {

    private static volatile StrategyAssignmentRepository instance;

    /**
     * Assignments keyed by composite key: "symbol/timeframe"
     */
    private final Map<String, StrategyAssignment> assignments = new ConcurrentHashMap<>();

    /**
     * History of assignments keyed by composite key
     */
    private final Map<String, List<StrategyAssignment>> history = new ConcurrentHashMap<>();

    private StrategyAssignmentRepository() {
    }

    public static StrategyAssignmentRepository getInstance() {
        StrategyAssignmentRepository local = instance;

        if (local == null) {
            synchronized (StrategyAssignmentRepository.class) {
                local = instance;

                if (local == null) {
                    local = new StrategyAssignmentRepository();
                    instance = local;
                }
            }
        }

        return local;
    }

    /**
     * Save an assignment.
     */
    public synchronized void save(@NotNull StrategyAssignment assignment) {
        String key = makeKey(assignment.getSymbol(), assignment.getTimeframe());
        assignments.put(key, assignment);

        // Track history
        history.computeIfAbsent(key, k -> new ArrayList<>()).add(assignment);

        log.info(
                "Saved strategy assignment: {} - {} on {}/{}",
                assignment.getAssignmentId(),
                assignment.getStrategyId(),
                assignment.getSymbol(),
                assignment.getTimeframe().getCode());
    }

    /**
     * Get active assignment for symbol/timeframe.
     */
    public Optional<StrategyAssignment> getActive(@NotNull String symbol, @NotNull Timeframe timeframe) {
        String key = makeKey(symbol, timeframe);
        StrategyAssignment assignment = assignments.get(key);

        if (assignment != null && assignment.isValid()) {
            return Optional.of(assignment);
        }

        return Optional.empty();
    }

    /**
     * Get history of assignments for symbol/timeframe.
     */
    public List<StrategyAssignment> getHistory(@NotNull String symbol, @NotNull Timeframe timeframe) {
        String key = makeKey(symbol, timeframe);
        return new ArrayList<>(history.getOrDefault(key, List.of()));
    }

    /**
     * Disable an assignment by ID.
     */
    public synchronized void disable(@NotNull String assignmentId, @NotNull String reason) {
        for (StrategyAssignment assignment : assignments.values()) {
            if (assignment.getAssignmentId().equals(assignmentId)) {
                StrategyAssignment disabled = assignment.disabled(reason);
                String key = makeKey(assignment.getSymbol(), assignment.getTimeframe());
                assignments.put(key, disabled);

                history.computeIfAbsent(key, k -> new ArrayList<>()).add(disabled);

                log.info("Disabled assignment: {} - {}", assignmentId, reason);
                return;
            }
        }

        log.warn("Assignment not found: {}", assignmentId);
    }

    /**
     * Unassign a symbol/timeframe combination.
     */
    public synchronized void unassign(@NotNull String symbol, @NotNull Timeframe timeframe, @NotNull String reason) {
        String key = makeKey(symbol, timeframe);
        StrategyAssignment assignment = assignments.remove(key);

        if (assignment != null) {
            StrategyAssignment disabled = assignment.disabled(reason);
            history.computeIfAbsent(key, k -> new ArrayList<>()).add(disabled);

            log.info("Unassigned strategy: {}/{} - {}", symbol, timeframe.getCode(), reason);
        }
    }

    /**
     * Get all active assignments.
     */
    public Collection<StrategyAssignment> getAllActive() {
        return assignments.values().stream()
                .filter(StrategyAssignment::isValid)
                .collect(Collectors.toList());
    }

    /**
     * Get all assignments (active and inactive).
     */
    public Collection<StrategyAssignment> getAll() {
        return new ArrayList<>(assignments.values());
    }

    /**
     * Check if assignment exists for symbol/timeframe.
     */
    public boolean exists(@NotNull String symbol, @NotNull Timeframe timeframe) {
        String key = makeKey(symbol, timeframe);
        return assignments.containsKey(key);
    }

    /**
     * Clear all assignments (for testing).
     */
    public synchronized void clear() {
        assignments.clear();
        history.clear();
    }

    /**
     * Make composite key for symbol/timeframe.
     */
    private String makeKey(String symbol, Timeframe timeframe) {
        return StrategyAssignment.key(symbol, timeframe);
    }
}
