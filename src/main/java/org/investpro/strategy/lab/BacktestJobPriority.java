package org.investpro.strategy.lab;

/**
 * Priority levels for queued backtest jobs.
 *
 * <p>Lower {@link #getLevel()} value = higher scheduling priority.
 * Jobs are dequeued in ascending level order so that interactive
 * (user-visible) tests always run before background optimization sweeps.
 *
 * <pre>
 *   ACTIVE (0)        – user-selected strategy shown in the UI right now
 *   VISIBLE (1)       – strategies visible in ranking / voting tables
 *   PAPER_TRADING (2) – paper-trading validation and learning runs
 *   BACKGROUND (3)    – bulk parameter sweeps / optimization jobs
 * </pre>
 */
public enum BacktestJobPriority {

    /** Active, user-selected strategy – highest scheduling priority. */
    ACTIVE(0),

    /** Strategies currently visible in the UI ranking/voting panels. */
    VISIBLE(1),

    /** Paper-trading evaluation runs. */
    PAPER_TRADING(2),

    /** Background bulk optimization – lowest scheduling priority. */
    BACKGROUND(3);

    /** Numeric priority; lower value = runs sooner. */
    private final int level;

    BacktestJobPriority(int level) {
        this.level = level;
    }

    /** Returns the numeric priority level (lower = higher importance). */
    public int getLevel() {
        return level;
    }
}
