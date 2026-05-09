package org.investpro.strategy.lab;

import lombok.Builder;
import lombok.Getter;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.enums.timeframe.Timeframe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Current state snapshot of Strategy Lab for UI display.
 *
 * Contains:
 * - Latest strategy rankings
 * - Latest consensus result
 * - Current active assignment
 * - Test execution status
 *
 * Updated whenever tests complete or assignments change.
 */
@Getter
@Builder(toBuilder = true)
public class StrategyLabSnapshot {

    /**
     * Symbol being analyzed.
     */
    @NotNull
    private final String symbol;

    /**
     * Timeframe being analyzed.
     */
    @NotNull
    private final Timeframe timeframe;

    /**
     * Latest strategy rankings (sorted best-first).
     */
    @Builder.Default
    private final List<StrategyPerformanceReport> rankings = new ArrayList<>();

    /**
     * Latest consensus result.
     */
    @Nullable
    private final StrategyConsensusResult consensus;

    /**
     * Currently active strategy assignment.
     */
    @Nullable
    private final StrategyAssignment activeAssignment;

    /**
     * True if tests are currently running.
     */
    @Builder.Default
    private final boolean running = false;

    /**
     * Progress of current test run (0.0 to 1.0).
     */
    @Builder.Default
    private final double progress = 0.0;

    /**
     * Status message (e.g., "Testing Breakout strategy...").
     */
    @Nullable
    private final String statusMessage;

    /**
     * When this snapshot was last updated.
     */
    @Builder.Default
    private final Instant updatedAt = Instant.now();

    /**
     * Get top-ranked strategy.
     */
    @Nullable
    public StrategyPerformanceReport getTopStrategy() {
        if (rankings.isEmpty())
            return null;
        return rankings.get(0);
    }

    /**
     * True if we have recent rankings.
     */
    public boolean hasRankings() {
        return !rankings.isEmpty();
    }

    /**
     * True if consensus was reached.
     */
    public boolean hasConsensus() {
        return consensus != null && consensus.isConsensusReached();
    }

    /**
     * True if strategy is assigned.
     */
    public boolean hasAssignment() {
        return activeAssignment != null && activeAssignment.isValid();
    }

    /**
     * Number of strategies ranked.
     */
    public int getRankingCount() {
        return rankings.size();
    }
}
