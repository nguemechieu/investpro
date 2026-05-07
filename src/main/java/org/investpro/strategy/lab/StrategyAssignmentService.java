package org.investpro.strategy.lab;

import lombok.extern.slf4j.Slf4j;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyRegistry;
import org.investpro.strategy.TradingStrategy;
import org.investpro.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

/**
 * Service for managing strategy assignments to symbol/timeframe combinations.
 *
 * Responsibilities:
 * - Auto-assign best strategy from ranking
 * - Auto-assign from consensus result
 * - Manual assignment with optional locking
 * - Unassign strategies
 * - Disable/enable assignments
 * - Resolve assigned strategy for runtime trading
 * - Prevent auto-replacement of manual locked assignments
 */
@Slf4j
public class StrategyAssignmentService {

    private final StrategyAssignmentRepository repository;

    public StrategyAssignmentService() {
        this.repository = StrategyAssignmentRepository.getInstance();
    }

    /**
     * Automatically assign the best strategy from ranked reports.
     *
     * @param symbol        trading symbol
     * @param timeframe     trading timeframe
     * @param rankedReports ranked performance reports (best first)
     * @return the created assignment
     */
    public StrategyAssignment autoAssignBest(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull java.util.List<StrategyPerformanceReport> rankedReports) {
        if (rankedReports == null || rankedReports.isEmpty()) {
            log.warn("No strategies to assign for {}/{}", symbol, timeframe.getCode());
            return null;
        }

        // Find existing assignment
        Optional<StrategyAssignment> existing = repository.getActive(symbol, timeframe);

        // If locked manual assignment exists, don't replace
        if (existing.isPresent() && existing.get().isManual() && existing.get().isLocked()) {
            log.info(
                    "Skipping auto-assignment: locked manual assignment exists for {}/{}",
                    symbol,
                    timeframe.getCode());
            return existing.get();
        }

        // Find best tradable strategy
        StrategyRankingEngine ranking = new StrategyRankingEngine();
        StrategyPerformanceReport best = ranking.getBestTradable(rankedReports);

        if (best == null) {
            log.warn("No tradable strategy found for {}/{}", symbol, timeframe.getCode());

            // Disable existing assignment if any
            if (existing.isPresent()) {
                repository.disable(existing.get().getAssignmentId(), "No tradable strategy found");
            }

            return null;
        }

        // Create new assignment
        StrategyAssignment assignment = StrategyAssignment.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId(best.getStrategyName()) // Store strategy name as ID
                .mode(StrategyAssignment.StrategyAssignmentMode.AUTO)
                .assignedBy(StrategyAssignment.AssignedBy.AUTO)
                .scoreAtAssignment(best.getScore())
                .assignedAt(Instant.now())
                .active(true)
                .reason(String.format(
                        "Auto-assigned: %s (Score: %.1f, WR: %.1f%%, Return: %.1f%%)",
                        best.getStrategyName(),
                        best.getScore(),
                        best.getWinRate() * 100,
                        best.getTotalReturn()))
                .build();

        repository.save(assignment);

        log.info(
                "Auto-assigned best strategy: {}/{} -> {} (score: {:.1f})",
                symbol,
                timeframe.getCode(),
                best.getStrategyName(),
                best.getScore());

        return assignment;
    }

    /**
     * Assign from consensus result.
     */
    public StrategyAssignment assignFromConsensus(@NotNull StrategyConsensusResult consensus) {
        if (!consensus.isConsensusReached() || consensus.getSelectedStrategyName().equals("NONE")) {
            log.warn("Cannot assign: no consensus reached for {}/{}", consensus.getSymbol(),
                    consensus.getTimeframe().getCode());
            return null;
        }

        StrategyAssignment assignment = StrategyAssignment.builder()
                .symbol(consensus.getSymbol())
                .timeframe(consensus.getTimeframe())
                .strategyId(consensus.getSelectedStrategyName())
                .mode(StrategyAssignment.StrategyAssignmentMode.AUTO)
                .assignedBy(StrategyAssignment.AssignedBy.AUTO)
                .scoreAtAssignment(consensus.getConsensusScore())
                .assignedAt(Instant.now())
                .active(true)
                .reason(consensus.getReason())
                .build();

        repository.save(assignment);

        log.info(
                "Assigned from consensus: {}/{} -> {} (confidence: {:.1f%})",
                consensus.getSymbol(),
                consensus.getTimeframe().getCode(),
                consensus.getSelectedStrategyName(),
                consensus.getConsensusConfidence() * 100);

        return assignment;
    }

    /**
     * Manually assign a strategy.
     *
     * @param symbol       trading symbol
     * @param timeframe    trading timeframe
     * @param strategyName strategy variant name
     * @param reason       assignment reason
     * @param locked       if true, prevent auto-replacement
     * @return the created assignment
     */
    public StrategyAssignment manuallyAssign(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @NotNull String strategyName,
            @Nullable String reason,
            boolean locked) {
        StrategyAssignment assignment = StrategyAssignment.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .strategyId(strategyName)
                .mode(StrategyAssignment.StrategyAssignmentMode.MANUAL)
                .assignedBy(StrategyAssignment.AssignedBy.USER)
                .assignedAt(Instant.now())
                .active(true)
                .reason(reason != null ? reason : "Manual assignment by user")
                .locked(locked)
                .build();

        repository.save(assignment);

        log.info(
                "Manually assigned strategy: {}/{} -> {} (locked: {})",
                symbol,
                timeframe.getCode(),
                strategyName,
                locked);

        return assignment;
    }

    /**
     * Unassign a strategy.
     */
    public void unassign(@NotNull String symbol, @NotNull Timeframe timeframe, @Nullable String reason) {
        String finalReason = reason != null ? reason : "Unassigned by user";
        repository.unassign(symbol, timeframe, finalReason);

        log.info(
                "Unassigned strategy: {}/{} - {}",
                symbol,
                timeframe.getCode(),
                finalReason);
    }

    /**
     * Disable an assignment.
     */
    public void disableAssignment(@NotNull String assignmentId, @NotNull String reason) {
        repository.disable(assignmentId, reason);

        log.info("Disabled assignment: {} - {}", assignmentId, reason);
    }

    /**
     * Get active assignment for symbol/timeframe.
     */
    public Optional<StrategyAssignment> getActiveAssignment(
            @NotNull String symbol,
            @NotNull Timeframe timeframe) {
        return repository.getActive(symbol, timeframe);
    }

    /**
     * Resolve assigned strategy for trading.
     *
     * Returns the TradingStrategy instance that should be used for this
     * symbol/timeframe,
     * or null if no assignment exists or is disabled.
     */
    @Nullable
    public TradingStrategy resolveAssignedStrategy(
            @NotNull String symbol,
            @NotNull Timeframe timeframe) {
        Optional<StrategyAssignment> assignment = getActiveAssignment(symbol, timeframe);

        if (assignment.isEmpty()) {
            log.debug("No assignment for {}/{}", symbol, timeframe.getCode());
            return null;
        }

        StrategyAssignment assign = assignment.get();

        if (assign.isDisabled()) {
            log.debug("Assignment disabled for {}/{}: {}", symbol, timeframe.getCode(), assign.getDisableReason());
            return null;
        }

        // Resolve strategy from registry
        try {
            String strategyName = assign.getStrategyId();
            TradingStrategy strategy = StrategyRegistry.getInstance().getStrategy(strategyName);

            if (strategy == null) {
                log.warn("Could not resolve strategy: {}", strategyName);
                return null;
            }

            log.debug("Resolved strategy for {}/{}: {}", symbol, timeframe.getCode(), strategyName);
            return strategy;

        } catch (Exception e) {
            log.error("Error resolving strategy for {}/{}", symbol, timeframe.getCode(), e);
            return null;
        }
    }

    /**
     * Get repository for direct access.
     */
    public StrategyAssignmentRepository getRepository() {
        return repository;
    }
}
