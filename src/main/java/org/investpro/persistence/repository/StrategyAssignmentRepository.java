package org.investpro.persistence.repository;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Repository for managing strategy assignments.
 *
 * Current implementation is in-memory. It is suitable for desktop/runtime usage,
 * but can later be backed by SQLite, Postgres, or another persistent store.
 */
@Slf4j
public final class StrategyAssignmentRepository {

    private static volatile StrategyAssignmentRepository instance;

    private final Map<String, StrategyAssignment> assignmentsById = new ConcurrentHashMap<>();
    private final Map<String, List<StrategyAssignment>> assignmentsBySymbolTimeframe = new ConcurrentHashMap<>();

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
     * Saves an assignment.
     * <p>
     * If an assignment with the same ID already exists, the old version is removed
     * from the symbol/timeframe index before the new one is inserted.
     */
    public void save(@NotNull StrategyAssignment assignment) {
        Objects.requireNonNull(assignment, "assignment must not be null");

        StrategyAssignment previous = assignmentsById.put(assignment.getAssignmentId(), assignment);

        if (previous != null) {
            removeFromSymbolTimeframeIndex(previous);
        }

        String key = getSymbolTimeframeKey(assignment.getSymbol(), assignment.getTimeframe());

        assignmentsBySymbolTimeframe
                .computeIfAbsent(key, ignored -> Collections.synchronizedList(new ArrayList<>()))
                .add(assignment);

        log.info(
                "Saved strategy assignment: id={}, symbol={}, timeframe={}, strategyId={}",
                assignment.getAssignmentId(),
                assignment.getSymbol(),
                assignment.getTimeframe(),
                assignment.getStrategyId()
        );
    }

    /**
     * Gets an assignment by ID.
     */
    @Nullable
    public StrategyAssignment getById(@NotNull String assignmentId) {
        Objects.requireNonNull(assignmentId, "assignmentId must not be null");
        return assignmentsById.get(assignmentId);
    }

    /**
     * Gets the current active assignment for a symbol/timeframe combination.
     *
     * Returns null if no valid, non-expired assignment exists.
     */
    @Nullable
    public StrategyAssignment getActive(@NotNull String symbol, @NotNull Timeframe timeframe) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");

        String key = getSymbolTimeframeKey(symbol, timeframe);
        List<StrategyAssignment> assignments = assignmentsBySymbolTimeframe.get(key);

        if (assignments == null || assignments.isEmpty()) {
            return null;
        }

        synchronized (assignments) {
            return assignments.stream()
                    .filter(Objects::nonNull)
                    .filter(StrategyAssignment::isValid)
                    .filter(assignment -> !assignment.isExpired())
                    .max(Comparator.comparing(StrategyAssignment::getAssignedAt))
                    .orElse(null);
        }
    }

    /**
     * Alias used by StrategySelectionService-style code.
     */
    @Nullable
    public StrategyAssignment getAssignment(@NotNull String symbol, @NotNull Timeframe timeframe) {
        return getActive(symbol, timeframe);
    }

    /**
     * Convenience overload for callers that pass timeframe as a string.
     */
    @Nullable
    public StrategyAssignment getAssignment(@NotNull String symbol, @NotNull String timeframe) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");

        Timeframe parsedTimeframe = parseTimeframe(timeframe);
        return getActive(symbol, parsedTimeframe);
    }

    /**
     * Gets all assignments for a symbol/timeframe pair.
     */
    @NotNull
    public List<StrategyAssignment> getForSymbolAndTimeframe(
            @NotNull String symbol,
            @NotNull Timeframe timeframe
    ) {
        Objects.requireNonNull(symbol, "symbol must not be null");
        Objects.requireNonNull(timeframe, "timeframe must not be null");

        String key = getSymbolTimeframeKey(symbol, timeframe);
        List<StrategyAssignment> assignments = assignmentsBySymbolTimeframe.get(key);

        if (assignments == null || assignments.isEmpty()) {
            return List.of();
        }

        synchronized (assignments) {
            return assignments.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    /**
     * Gets all assignments for a symbol across all timeframes.
     */
    @NotNull
    public List<StrategyAssignment> getForSymbol(@NotNull String symbol) {
        Objects.requireNonNull(symbol, "symbol must not be null");

        return assignmentsBySymbolTimeframe.values().stream()
                .flatMap(list -> {
                    synchronized (list) {
                        return new ArrayList<>(list).stream();
                    }
                })
                .filter(Objects::nonNull)
                .filter(assignment -> assignment.getSymbol().equalsIgnoreCase(symbol))
                .toList();
    }

    /**
     * Same as getForSymbol(). Kept for compatibility with existing callers.
     */
    @NotNull
    public List<StrategyAssignment> getForSymbolAllTimeframes(@NotNull String symbol) {
        return getForSymbol(symbol);
    }

    /**
     * Gets all valid, non-expired assignments.
     */
    @NotNull
    public List<StrategyAssignment> getAllActive() {
        return assignmentsById.values().stream()
                .filter(Objects::nonNull)
                .filter(StrategyAssignment::isValid)
                .filter(assignment -> !assignment.isExpired())
                .toList();
    }

    /**
     * Gets all assignments, including inactive and expired assignments.
     */
    @NotNull
    public List<StrategyAssignment> getAll() {
        return List.copyOf(assignmentsById.values());
    }

    /**
     * Deletes an assignment.
     */
    public void delete(@NotNull String assignmentId) {
        Objects.requireNonNull(assignmentId, "assignmentId must not be null");

        StrategyAssignment removed = assignmentsById.remove(assignmentId);

        if (removed == null) {
            log.debug("Strategy assignment not found for delete: {}", assignmentId);
            return;
        }

        removeFromSymbolTimeframeIndex(removed);
        log.info("Deleted strategy assignment: {}", assignmentId);
    }

    /**
     * Clears all assignments.
     */
    public void clear() {
        assignmentsById.clear();
        assignmentsBySymbolTimeframe.clear();
        log.info("Cleared all strategy assignments");
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

    private void removeFromSymbolTimeframeIndex(@NotNull StrategyAssignment assignment) {
        String key = getSymbolTimeframeKey(assignment.getSymbol(), assignment.getTimeframe());
        List<StrategyAssignment> assignments = assignmentsBySymbolTimeframe.get(key);

        if (assignments == null) {
            return;
        }

        synchronized (assignments) {
            assignments.removeIf(existing ->
                    existing != null
                            && Objects.equals(existing.getAssignmentId(), assignment.getAssignmentId())
            );

            if (assignments.isEmpty()) {
                assignmentsBySymbolTimeframe.remove(key);
            }
        }
    }

    private String getSymbolTimeframeKey(@NotNull String symbol, @NotNull Timeframe timeframe) {
        return normalizeSymbol(symbol) + "_" + timeframe.getCode().toUpperCase(Locale.ROOT);
    }

    private String normalizeSymbol(@NotNull String symbol) {
        return symbol.trim().toUpperCase(Locale.ROOT);
    }

    private @NotNull Timeframe parseTimeframe(@NotNull String timeframe) {
        String normalized = timeframe.trim().toUpperCase(Locale.ROOT);

        for (Timeframe candidate : Timeframe.values()) {
            if (candidate.name().equalsIgnoreCase(normalized)) {
                return candidate;
            }

            if (candidate.getCode().equalsIgnoreCase(normalized)) {
                return candidate;
            }
        }

        throw new IllegalArgumentException("Unsupported timeframe: " + timeframe);
    }
}