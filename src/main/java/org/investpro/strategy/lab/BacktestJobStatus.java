package org.investpro.strategy.lab;

/**
 * Lifecycle states of a {@link BacktestJob}.
 *
 * <pre>
 *   QUEUED     → RUNNING → COMPLETED
 *                       ↘ FAILED
 *                       ↘ CANCELLED
 *   QUEUED     → CANCELLED  (cancelled before dequeue)
 * </pre>
 */
public enum BacktestJobStatus {

    /** Job is waiting in the priority queue. */
    QUEUED,

    /** Job is currently executing on a worker thread. */
    RUNNING,

    /** Job finished successfully and produced a result. */
    COMPLETED,

    /** Job threw an unhandled exception during execution. */
    FAILED,

    /** Job was explicitly cancelled (graceful interruption). */
    CANCELLED
}
