package org.investpro.exchange.execution;

/**
 * Lifecycle status of an execution request.
 *
 * <p>Statuses progress from {@link #PENDING} through submission and fill states.
 * Terminal statuses ({@link #isTerminal()} returns {@code true}) indicate that
 * no further state transitions are expected for that order.
 *
 * <p>Typical happy-path transitions:
 * <pre>
 *   PENDING → ROUTING → SUBMITTED → FILLED
 * </pre>
 *
 * <p>Failure paths:
 * <pre>
 *   PENDING → ROUTING → SUBMITTED → REJECTED
 *   PENDING → ROUTING → SUBMITTED → CANCELLED
 *   PENDING → ROUTING → ERROR
 *   SUBMITTED → PARTIAL_FILL → CANCELLED
 * </pre>
 */
public enum ExecutionStatus {

    /** Request has been created but not yet routed to a venue. */
    PENDING(false),

    /** Router is evaluating available venues and selecting the best path. */
    ROUTING(false),

    /** Order has been submitted to the target exchange and is awaiting acknowledgement. */
    SUBMITTED(false),

    /**
     * Order has been partially filled.  The remaining open quantity is still
     * working at the exchange.  This is a non-terminal intermediate state.
     */
    PARTIAL_FILL(false),

    /** Order has been completely filled.  Terminal — no further changes expected. */
    FILLED(true),

    /** Order was rejected by the exchange (e.g., insufficient funds, invalid params). Terminal. */
    REJECTED(true),

    /** Order was cancelled — either by the system, the user, or the exchange. Terminal. */
    CANCELLED(true),

    /**
     * A system-level or infrastructure error occurred during routing or submission.
     * Terminal — the caller should inspect the error code and message for details.
     */
    ERROR(true);

    // ─────────────────────────────────────────────────────────────────────────

    private final boolean terminal;

    ExecutionStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /**
     * Returns {@code true} if this status represents a final state from which
     * no further order lifecycle transitions are expected.
     */
    public boolean isTerminal() {
        return terminal;
    }
}
