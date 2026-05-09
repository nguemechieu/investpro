package org.investpro.strategy.lab;

import lombok.Builder;
import lombok.Getter;
import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.StrategyAssignment;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable current-state snapshot of Strategy Lab for UI display.
 *
 * Contains:
 * - Latest strategy rankings
 * - Latest consensus result
 * - Current active assignment
 * - Test execution status
 *
 * This object is intentionally immutable so the JavaFX UI can safely read it
 * without accidentally modifying Strategy Lab state.
 */
@Getter
@Builder(toBuilder = true)
public final class StrategyLabSnapshot {

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
     * Latest strategy rankings, sorted best-first.
     */
    @Builder.Default
    @NotNull
    private final List<StrategyPerformanceReport> rankings = List.of();

    /**
     * Latest consensus result.
     *
     * This may exist even when no strong consensus was reached.
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
     * Progress of current test run.
     *
     * Expected range: 0.0 to 1.0.
     */
    @Builder.Default
    private final double progress = 0.0;

    /**
     * Status message, for example:
     * "Testing Breakout strategy..."
     */
    @NotNull
    @Builder.Default
    private final String statusMessage = "Ready";

    /**
     * When this snapshot was last updated.
     */
    @NotNull
    @Builder.Default
    private final Instant updatedAt = Instant.now();

    public StrategyLabSnapshot(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @Nullable List<StrategyPerformanceReport> rankings,
            @Nullable StrategyConsensusResult consensus,
            @Nullable StrategyAssignment activeAssignment,
            boolean running,
            double progress,
            @Nullable String statusMessage,
            @Nullable Instant updatedAt
    ) {
        this.symbol = requireText(symbol, "symbol");
        this.timeframe = Objects.requireNonNull(timeframe, "timeframe must not be null");
        this.rankings = normalizeRankings(rankings);
        this.consensus = consensus;
        this.activeAssignment = activeAssignment;
        this.running = running;
        this.progress = clamp(progress, 0.0, 1.0);
        this.statusMessage = normalizeStatus(statusMessage);
        this.updatedAt = updatedAt == null ? Instant.now() : updatedAt;
    }

    public static StrategyLabSnapshot empty(@NotNull String symbol, @NotNull Timeframe timeframe) {
        return StrategyLabSnapshot.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .rankings(List.of())
                .consensus(null)
                .activeAssignment(null)
                .running(false)
                .progress(0.0)
                .statusMessage("Ready")
                .updatedAt(Instant.now())
                .build();
    }

    public static StrategyLabSnapshot running(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @Nullable String statusMessage,
            double progress
    ) {
        return StrategyLabSnapshot.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .rankings(List.of())
                .consensus(null)
                .activeAssignment(null)
                .running(true)
                .progress(progress)
                .statusMessage(statusMessage == null || statusMessage.isBlank()
                        ? "Testing strategies..."
                        : statusMessage)
                .updatedAt(Instant.now())
                .build();
    }

    public static StrategyLabSnapshot completed(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @Nullable List<StrategyPerformanceReport> rankings,
            @Nullable StrategyConsensusResult consensus,
            @Nullable StrategyAssignment activeAssignment
    ) {
        return StrategyLabSnapshot.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .rankings(rankings == null ? List.of() : rankings)
                .consensus(consensus)
                .activeAssignment(activeAssignment)
                .running(false)
                .progress(1.0)
                .statusMessage("Ready")
                .updatedAt(Instant.now())
                .build();
    }

    public static StrategyLabSnapshot failed(
            @NotNull String symbol,
            @NotNull Timeframe timeframe,
            @Nullable String errorMessage
    ) {
        return StrategyLabSnapshot.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .rankings(List.of())
                .consensus(null)
                .activeAssignment(null)
                .running(false)
                .progress(0.0)
                .statusMessage(errorMessage == null || errorMessage.isBlank()
                        ? "Strategy Lab failed."
                        : errorMessage)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Get top-ranked strategy.
     */
    @Nullable
    public StrategyPerformanceReport getTopStrategy() {
        return rankings.isEmpty() ? null : rankings.getFirst();
    }

    /**
     * True if we have recent rankings.
     */
    public boolean hasRankings() {
        return !rankings.isEmpty();
    }

    /**
     * True if a consensus object exists.
     *
     * Use this in the UI when you only need to know if consensus data exists.
     */
    public boolean hasConsensusData() {
        return consensus != null;
    }

    /**
     * True if consensus exists and passed the threshold.
     */
    public boolean isConsensusReached() {
        return consensus != null && consensus.isConsensusReached();
    }

    /**
     * Compatibility alias.
     *
     * Prefer hasConsensusData() for UI checks.
     */
    public boolean hasConsensus() {
        return hasConsensusData();
    }

    /**
     * True if a strategy is assigned.
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

    /**
     * Compatibility alias for existing UI code.
     */
    @Nullable
    public StrategyAssignment getAssignment() {
        return activeAssignment;
    }

    /**
     * Explicit active assignment getter.
     */
    @Nullable
    public StrategyAssignment getActiveAssignment() {
        return activeAssignment;
    }

    public boolean isEmpty() {
        return !hasRankings() && !hasConsensusData() && !hasAssignment();
    }

    public boolean isComplete() {
        return !running && progress >= 1.0;
    }

    public boolean isFailed() {
        return !running
                && statusMessage != null
                && statusMessage.toLowerCase().contains("fail");
    }

    private static List<StrategyPerformanceReport> normalizeRankings(
            @Nullable List<StrategyPerformanceReport> input
    ) {
        if (input == null || input.isEmpty()) {
            return List.of();
        }

        List<StrategyPerformanceReport> copy = new ArrayList<>();

        for (StrategyPerformanceReport report : input) {
            if (report != null) {
                copy.add(report);
            }
        }

        copy.sort(
                Comparator.comparingDouble(StrategyPerformanceReport::getScore)
                        .reversed()
        );

        return List.copyOf(copy);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim();
    }

    private static String normalizeStatus(@Nullable String value) {
        return value == null || value.isBlank()
                ? "Ready"
                : value.trim();
    }

    private static double clamp(double value, double min, double max) {
        if (!Double.isFinite(value)) {
            return min;
        }

        return Math.max(min, Math.min(max, value));
    }
}