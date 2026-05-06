package org.investpro.strategy;

import lombok.extern.slf4j.Slf4j;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Repository for managing strategy assignments.
 * In-memory implementation suitable for production use with optional
 * persistence.
 */
@Slf4j
public class StrategyAssignmentRepository {
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory
            .getLogger(StrategyAssignmentRepository.class);
    private static StrategyAssignmentRepository instance;
    private final Map<String, StrategyAssignment> assignmentsById = new ConcurrentHashMap<>();
    private final Map<String, List<StrategyAssignment>> assignmentsBySymbol = new ConcurrentHashMap<>();

    private StrategyAssignmentRepository() {
    }

    public static synchronized StrategyAssignmentRepository getInstance() {
        if (instance == null) {
            instance = new StrategyAssignmentRepository();
        }
        return instance;
    }

    /**
     * Saves an assignment (insert or update).
     */
    public void save(@NotNull StrategyAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment must not be null");
        String id = assignment.getAssignmentId();
        assignmentsById.put(id, assignment);

        String key = getSymbolTimeframeKey(assignment.getSymbol(), assignment.getTimeframe());
        assignmentsBySymbol.computeIfAbsent(key, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(assignment);

        log.info("Saved assignment: {}", assignment);
    }

    /**
     * Gets an assignment by ID.
     */
    @Nullable
    public StrategyAssignment getById(@NotNull String assignmentId) {
        return assignmentsById.get(assignmentId);
    }

    /**
     * Gets the active assignment for a symbol/timeframe combination.
     * Returns null if no active assignment exists.
     */
    @Nullable
    public StrategyAssignment getActive(@NotNull String symbol, @NotNull Timeframe timeframe) {
        String key = getSymbolTimeframeKey(symbol, timeframe);
        List<StrategyAssignment> assignments = assignmentsBySymbol.getOrDefault(key, new ArrayList<>());
        return assignments.stream()
                .filter(StrategyAssignment::isValid)
                .filter(StrategyAssignment::isExpired)
                .findFirst()
                .orElse(null);
    }

    /**
     * Gets all assignments for a symbol.
     */
    @NotNull
    public List<StrategyAssignment> getForSymbol(@NotNull String symbol) {
        return assignmentsBySymbol.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getSymbol().equals(symbol))
                .collect(Collectors.toList());
    }

    /**
     * Gets all assignments for a symbol across all timeframes.
     */
    @NotNull
    public List<StrategyAssignment> getForSymbolAllTimeframes(@NotNull String symbol) {
        return assignmentsBySymbol.values().stream()
                .flatMap(List::stream)
                .filter(a -> a.getSymbol().equalsIgnoreCase(symbol))
                .collect(Collectors.toList());
    }

    /**
     * Gets all active assignments.
     */
    @NotNull
    public List<StrategyAssignment> getAllActive() {
        return assignmentsById.values().stream()
                .filter(StrategyAssignment::isValid)
                .collect(Collectors.toList());
    }

    /**
     * Gets all assignments (including inactive/expired).
     */
    @NotNull
    public List<StrategyAssignment> getAll() {
        return new ArrayList<>(assignmentsById.values());
    }

    /**
     * Deletes an assignment.
     */
    public void delete(@NotNull String assignmentId) {
        StrategyAssignment removed = assignmentsById.remove(assignmentId);
        if (removed != null) {
            String key = getSymbolTimeframeKey(removed.getSymbol(), removed.getTimeframe());
            List<StrategyAssignment> list = assignmentsBySymbol.get(key);
            if (list != null) {
                list.removeIf(a -> a.getAssignmentId().equals(assignmentId));
            }
            log.info("Deleted assignment: {}", assignmentId);
        }
    }

    /**
     * Clears all assignments.
     */
    public void clear() {
        assignmentsById.clear();
        assignmentsBySymbol.clear();
        log.info("Cleared all assignments");
    }

    /**
     * Gets count of active assignments.
     */
    public int getActiveCount() {
        return getAllActive().size();
    }

    /**
     * Gets count of all assignments.
     */
    public int getTotalCount() {
        return assignmentsById.size();
    }

    private String getSymbolTimeframeKey(String symbol, Timeframe timeframe) {
        return symbol + "_" + timeframe.getCode();
    }
}
