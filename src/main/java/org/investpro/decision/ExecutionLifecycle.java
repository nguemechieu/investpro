package org.investpro.decision;

import java.time.Instant;

/**
 * Immutable lifecycle timestamp tracker for an institutional trade decision.
 *
 * <p>An {@code ExecutionLifecycle} records the wall-clock timestamps for each
 * phase transition a decision passes through, from creation to final terminal state
 * (executed, failed, cancelled, or expired). Null timestamps indicate that a particular
 * phase has not yet been reached.</p>
 *
 * <h3>Terminal states (mutually exclusive):</h3>
 * <ul>
 *   <li>{@link #executedAt} — trade was fully filled</li>
 *   <li>{@link #failedAt} — submission failed or venue error</li>
 *   <li>{@link #cancelledAt} — cancelled before fill</li>
 *   <li>{@link #expiredAt} — time-in-force expired without fill</li>
 * </ul>
 *
 * <p>All instances are immutable. Phase transitions produce new instances via
 * the {@code with*()} factory methods.</p>
 *
 * @param createdAt          when the decision was first created
 * @param validatedAt        when structural validation passed
 * @param riskApprovedAt     when risk evaluation returned APPROVED or REDUCED
 * @param aiApprovedAt       when AI reasoning validated the decision
 * @param positionSizedAt    when the position sizing phase completed
 * @param routedAt           when the execution route was selected
 * @param submittedAt        when the order was submitted to the venue
 * @param partiallyFilledAt  when the first partial fill was received
 * @param executedAt         when the order was fully filled
 * @param failedAt           when a terminal failure occurred
 * @param cancelledAt        when the decision was cancelled
 * @param expiredAt          when the time-in-force expired
 */
public record ExecutionLifecycle(
        Instant createdAt,
         Instant validatedAt,
        Instant riskApprovedAt,
         Instant aiApprovedAt,
        Instant positionSizedAt,
         Instant routedAt,
        Instant submittedAt,
         Instant partiallyFilledAt,
         Instant executedAt,
         Instant failedAt,
         Instant cancelledAt,
         Instant expiredAt
) {

    // ─── Compact constructor ───────────────────────────────────────────────────

    public ExecutionLifecycle {
        if (createdAt == null) createdAt = Instant.now();
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /** Returns a lifecycle with only the {@code createdAt} timestamp set. */
    public static ExecutionLifecycle created() {
        return new ExecutionLifecycle(
                Instant.now(), null, null, null, null, null,
                null, null, null, null, null, null);
    }

    /** Returns a lifecycle with {@code createdAt} set to the given timestamp. */
    public static ExecutionLifecycle createdAt(Instant createdAt) {
        return new ExecutionLifecycle(
                createdAt, null, null, null, null, null,
                null, null, null, null, null, null);
    }

    // ─── Transition factories ─────────────────────────────────────────────────

    /** Returns a copy of this lifecycle with {@code validatedAt} set to now. */
    public ExecutionLifecycle withValidated() {
        return new ExecutionLifecycle(
                createdAt, Instant.now(), riskApprovedAt, aiApprovedAt,
                positionSizedAt, routedAt, submittedAt, partiallyFilledAt,
                executedAt, failedAt, cancelledAt, expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code riskApprovedAt} set to now. */
    public ExecutionLifecycle withRiskApproved() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, Instant.now(), aiApprovedAt,
                positionSizedAt, routedAt, submittedAt, partiallyFilledAt,
                executedAt, failedAt, cancelledAt, expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code aiApprovedAt} set to now. */
    public ExecutionLifecycle withAiApproved() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, riskApprovedAt, Instant.now(),
                positionSizedAt, routedAt, submittedAt, partiallyFilledAt,
                executedAt, failedAt, cancelledAt, expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code positionSizedAt} set to now. */
    public ExecutionLifecycle withPositionSized() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, riskApprovedAt, aiApprovedAt,
                Instant.now(), routedAt, submittedAt, partiallyFilledAt,
                executedAt, failedAt, cancelledAt, expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code routedAt} set to now. */
    public ExecutionLifecycle withRouted() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, riskApprovedAt, aiApprovedAt,
                positionSizedAt, Instant.now(), submittedAt, partiallyFilledAt,
                executedAt, failedAt, cancelledAt, expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code submittedAt} set to now. */
    public ExecutionLifecycle withSubmitted() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, riskApprovedAt, aiApprovedAt,
                positionSizedAt, routedAt, Instant.now(), partiallyFilledAt,
                executedAt, failedAt, cancelledAt, expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code executedAt} set to now (terminal). */
    public ExecutionLifecycle withExecuted() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, riskApprovedAt, aiApprovedAt,
                positionSizedAt, routedAt, submittedAt, partiallyFilledAt,
                Instant.now(), failedAt, cancelledAt, expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code failedAt} set to now (terminal). */
    public ExecutionLifecycle withFailed() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, riskApprovedAt, aiApprovedAt,
                positionSizedAt, routedAt, submittedAt, partiallyFilledAt,
                executedAt, Instant.now(), cancelledAt, expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code cancelledAt} set to now (terminal). */
    public ExecutionLifecycle withCancelled() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, riskApprovedAt, aiApprovedAt,
                positionSizedAt, routedAt, submittedAt, partiallyFilledAt,
                executedAt, failedAt, Instant.now(), expiredAt);
    }

    /** Returns a copy of this lifecycle with {@code expiredAt} set to now (terminal). */
    public ExecutionLifecycle withExpired() {
        return new ExecutionLifecycle(
                createdAt, validatedAt, riskApprovedAt, aiApprovedAt,
                positionSizedAt, routedAt, submittedAt, partiallyFilledAt,
                executedAt, failedAt, cancelledAt, Instant.now());
    }

    // ─── Derived properties ───────────────────────────────────────────────────

    /** Returns {@code true} if the decision reached a terminal state. */
    public boolean isTerminal() {
        return executedAt != null || failedAt != null
                || cancelledAt != null || expiredAt != null;
    }

    /** Returns {@code true} if the trade was fully executed. */
    public boolean isSuccessful() {
        return executedAt != null;
    }

    /**
     * Returns the total elapsed time in milliseconds from creation to the first
     * terminal state, or from creation to now if not yet terminal.
     */
    public long elapsedMs() {
        Instant terminal = executedAt != null ? executedAt
                : failedAt != null ? failedAt
                : cancelledAt != null ? cancelledAt
                : expiredAt != null ? expiredAt
                : Instant.now();
        return terminal.toEpochMilli() - createdAt.toEpochMilli();
    }

    /**
     * Returns the number of phases completed (non-null timestamps, excluding createdAt).
     */
    public int phasesCompleted() {
        int count = 0;
        if (validatedAt != null)       count++;
        if (riskApprovedAt != null)    count++;
        if (aiApprovedAt != null)      count++;
        if (positionSizedAt != null)   count++;
        if (routedAt != null)          count++;
        if (submittedAt != null)       count++;
        if (partiallyFilledAt != null) count++;
        if (executedAt != null)        count++;
        return count;
    }
}
