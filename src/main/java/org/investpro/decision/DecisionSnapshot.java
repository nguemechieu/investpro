package org.investpro.decision;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * Compact, serializable snapshot of an {@link ExecutionDecision} for distributed workers,
 * replay engines, and audit trails.
 *
 * <p>A {@code DecisionSnapshot} is produced from a full {@link ExecutionDecision} and strips
 * the detailed reasoning and score breakdowns based on the selected {@link SnapshotMode}.
 * This minimizes serialization overhead in Strategy Lab distributed workers.</p>
 *
 * <h3>Snapshot modes:</h3>
 * <ul>
 *   <li>{@link SnapshotMode#FULL} — complete decision data including reasoning</li>
 *   <li>{@link SnapshotMode#LIGHTWEIGHT} — minimal fields for high-throughput simulations</li>
 *   <li>{@link SnapshotMode#REPLAY} — sufficient data to deterministically replay the decision</li>
 *   <li>{@link SnapshotMode#ARCHIVE} — compact for long-term storage, excludes scores</li>
 * </ul>
 *
 * @param decisionId        unique decision identifier (sequence number or UUID string)
 * @param status            final lifecycle status at snapshot time
 * @param mode              execution mode that produced the decision
 * @param action            the final action decided (BUY, SELL, SKIP, etc.)
 * @param confidence        confidence score at decision time (0.0–1.0)
 * @param explanation       human-readable summary of why this decision was taken
 * @param snapshotMode      mode controlling which fields are included
 * @param snapshotAt        when this snapshot was created
 * @param reasoningSummary  AI/rule reasoning summary (null in LIGHTWEIGHT/ARCHIVE modes)
 * @param scoreBreakdown    score breakdown string (null in LIGHTWEIGHT/ARCHIVE modes)
 */
public record DecisionSnapshot(
        String decisionId,
        DecisionStatus status,
        DecisionMode mode,
        String action,
        double confidence,
        String explanation,
        SnapshotMode snapshotMode,
        Instant snapshotAt,
        @Nullable String reasoningSummary,
        @Nullable String scoreBreakdown
) {

    // ─── Inner enum ───────────────────────────────────────────────────────────

    /** Controls the level of detail included in the snapshot. */
    public enum SnapshotMode {
        /** Full snapshot including all fields. Highest memory cost. */
        FULL,
        /** Lightweight: only decision ID, status, action, confidence. */
        LIGHTWEIGHT,
        /** Sufficient for deterministic replay of the decision. */
        REPLAY,
        /** Archive: minimal, excludes score breakdowns and reasoning. */
        ARCHIVE
    }

    // ─── Compact constructor ───────────────────────────────────────────────────

    public DecisionSnapshot {
        if (decisionId == null || decisionId.isBlank()) decisionId = "unknown";
        if (status == null)       status = DecisionStatus.CREATED;
        if (mode == null)         mode = DecisionMode.SIMULATION;
        if (action == null)       action = "UNKNOWN";
        if (explanation == null)  explanation = "";
        if (snapshotMode == null) snapshotMode = SnapshotMode.LIGHTWEIGHT;
        if (snapshotAt == null)   snapshotAt = Instant.now();
        confidence = Math.max(0.0, Math.min(1.0, confidence));
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Creates a {@link SnapshotMode#LIGHTWEIGHT} snapshot from an {@link ExecutionDecision}.
     * Suitable for simulation workers — minimal allocations, no reasoning or score data.
     */
    public static DecisionSnapshot lightweight(ExecutionDecision decision) {
        return new DecisionSnapshot(
                decision.decisionId(),
                decision.status(),
                decision.mode(),
                decision.status().name(),
                decision.intent() != null ? decision.intent().confidence() : 0.0,
                decision.explanation(),
                SnapshotMode.LIGHTWEIGHT,
                Instant.now(),
                null,
                null);
    }

    /**
     * Creates a {@link SnapshotMode#FULL} snapshot from an {@link ExecutionDecision}.
     * Includes reasoning summary and score breakdown.
     */
    public static DecisionSnapshot full(ExecutionDecision decision) {
        String reasoning = decision.reasoning() != null
                ? decision.reasoning().reasoningSummary() : null;
        String scores = decision.scoreBreakdown() != null
                ? decision.scoreBreakdown().toString() : null;
        return new DecisionSnapshot(
                decision.decisionId(),
                decision.status(),
                decision.mode(),
                decision.status().name(),
                decision.intent() != null ? decision.intent().confidence() : 0.0,
                decision.explanation(),
                SnapshotMode.FULL,
                Instant.now(),
                reasoning,
                scores);
    }

    /**
     * Creates an {@link SnapshotMode#ARCHIVE} snapshot — minimal fields for long-term storage.
     */
    public static DecisionSnapshot archive(ExecutionDecision decision) {
        return new DecisionSnapshot(
                decision.decisionId(),
                decision.status(),
                decision.mode(),
                decision.status().name(),
                decision.intent() != null ? decision.intent().confidence() : 0.0,
                decision.explanation(),
                SnapshotMode.ARCHIVE,
                Instant.now(),
                null,
                null);
    }

    // ─── Derived properties ───────────────────────────────────────────────────

    /** Returns {@code true} if this snapshot includes reasoning data. */
    public boolean hasReasoning() { return reasoningSummary != null && !reasoningSummary.isBlank(); }

    /** Returns {@code true} if this snapshot includes score breakdown data. */
    public boolean hasScores() { return scoreBreakdown != null && !scoreBreakdown.isBlank(); }

    /** Returns {@code true} if the decision reached a successful terminal state. */
    public boolean wasApproved() {
        return status == DecisionStatus.EXECUTION_PENDING || status == DecisionStatus.EXECUTED;
    }
}
