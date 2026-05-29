package org.investpro.strategy.lifecycle;

/**
 * AI decision on an individual trading signal before execution routing.
 *
 * <p><strong>IMPORTANT:</strong> AI may review signals and return a decision,
 * but NEVER places orders. The execution engine makes all final trading decisions.</p>
 */
public enum AISignalDecision {
    APPROVE("Signal approved - conditions favorable for execution"),
    REJECT("Signal rejected - conditions unfavorable"),
    REDUCE_SIZE("Signal approved but position size should be reduced"),
    WAIT("Signal timing is poor - wait for better entry conditions"),
    LOW_CONFIDENCE("AI confidence too low to provide reliable guidance");

    /** Human-readable description of this signal decision. */
    public final String description;

    AISignalDecision(String description) {
        this.description = description;
    }

    /**
     * @return true if this decision allows the signal to proceed through the execution pipeline.
     *         REDUCE_SIZE allows execution with adjusted sizing.
     */
    public boolean allowsExecution() {
        return this == APPROVE || this == REDUCE_SIZE;
    }

    /** @return true if this decision explicitly blocks execution routing. */
    public boolean blocksExecution() {
        return this == REJECT;
    }
}
