package org.investpro.exchange.execution;

/**
 * Lifecycle status of a trade execution request.
 *
 * <p>State transitions:
 * <pre>
 *   PENDING → ROUTING → SUBMITTED → PARTIAL_FILL → FILLED
 *                              ↘ REJECTED
 *                              ↘ CANCELLED
 *                              ↘ ERROR
 * </pre>
 */
public enum ExecutionStatus {

    /** Request has been created but routing has not started. */
    PENDING(false),

    /** Smart router is selecting the best execution venue. */
    ROUTING(false),

    /** Order has been submitted to the exchange/venue. Awaiting acknowledgement. */
    SUBMITTED(false),

    /** Partial fill received; awaiting the remainder. */
    PARTIAL_FILL(false),

    /** Order fully filled. Terminal state. */
    FILLED(true),

    /** Order rejected by the exchange or router. Terminal state. */
    REJECTED(true),

    /** Order cancelled by user or system. Terminal state. */
    CANCELLED(true),

    /** Unexpected error during submission or confirmation. Terminal state. */
    ERROR(true);

    /** True if no further state transitions are expected. */
    public final boolean terminal;

    ExecutionStatus(boolean terminal) {
        this.terminal = terminal;
    }

    /** Returns true if this is a terminal status. */
    public boolean isTerminal() { return terminal; }

    /** Returns true if the execution completed successfully (fully or partially). */
    public boolean isSuccessful() { return this == FILLED || this == PARTIAL_FILL; }
}
