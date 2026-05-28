package org.investpro.decision;

/**
 * Full lifecycle status of a {@link BotTradeDecision}.
 *
 * <p>Tracks the decision from creation through risk evaluation, AI validation,
 * execution planning, and final outcome. Designed for audit trails and
 * distributed institutional architectures.</p>
 */
public enum DecisionStatus {

    /** Decision object created; awaiting validation. */
    CREATED,

    /** Signal and market data validated; moving to risk evaluation. */
    VALIDATED,

    /** Risk evaluation rejected this trade. No execution will occur. */
    RISK_REJECTED,

    /** AI reasoning layer vetoed this trade. No execution will occur. */
    AI_REJECTED,

    /** Risk and AI checks passed; execution plan generated and awaiting submission. */
    EXECUTION_PENDING,

    /** Order submitted and confirmed on the venue. */
    EXECUTED,

    /** Execution failed after approval (connectivity, rejection by exchange, etc.). */
    FAILED,

    /** Decision was cancelled before execution (e.g., market moved, timeout). */
    CANCELLED,

    /** Decision expired before it could be acted upon (TTL exceeded). */
    EXPIRED;

    /** Returns true if this status represents a terminal (non-actionable) state. */
    public boolean isTerminal() {
        return this == RISK_REJECTED || this == AI_REJECTED ||
               this == EXECUTED || this == FAILED ||
               this == CANCELLED || this == EXPIRED;
    }

    /** Returns true if this decision was rejected at any stage. */
    public boolean isRejected() {
        return this == RISK_REJECTED || this == AI_REJECTED;
    }

    /** Returns true if this decision was successfully executed. */
    public boolean isSuccessful() {
        return this == EXECUTED;
    }
}
