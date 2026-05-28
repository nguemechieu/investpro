package org.investpro.strategy.lab;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Represents a single unit of work inside the {@link BacktestScheduler}.
 *
 * <p>A job wraps a {@link StrategyBacktestRequest}, carries its own
 * {@link BacktestJobPriority} and lifecycle {@link BacktestJobStatus},
 * and exposes a {@link CompletableFuture} that callers can await.
 *
 * <p>Cancellation is cooperative: the runner thread checks
 * {@link Thread#isInterrupted()} at every candle iteration; cancelling a job
 * sets the future's interrupt flag on the assigned thread.
 */
public final class BacktestJob implements Comparable<BacktestJob> {

    /** Unique job identifier. */
    @NotNull
    private final String jobId;

    /** The backtest parameters this job will execute. */
    @NotNull
    private final StrategyBacktestRequest request;

    /** Scheduling priority. */
    @NotNull
    private final BacktestJobPriority priority;

    /** Lifecycle state – volatile for cross-thread visibility. */
    @NotNull
    private final AtomicReference<BacktestJobStatus> status =
            new AtomicReference<>(BacktestJobStatus.QUEUED);

    /** Future resolved when the job finishes (any terminal state). */
    @NotNull
    private final CompletableFuture<StrategyPerformanceReport> future =
            new CompletableFuture<>();

    /** When the job was created / submitted. */
    @NotNull
    private final Instant submittedAt;

    /** When the job started executing (null until it begins). */
    @Nullable
    private volatile Instant startedAt;

    /** When the job reached a terminal state (null until done). */
    @Nullable
    private volatile Instant completedAt;

    /** Worker thread assigned to this job (used for interrupt-based cancellation). */
    @Nullable
    private volatile Thread workerThread;

    /** Optional callback invoked when the job reaches a terminal state. */
    @Nullable
    private final Consumer<BacktestJob> onComplete;

    // ─── Construction ────────────────────────────────────────────────────────

    /**
     * Creates a new job with auto-generated ID and {@link BacktestJobPriority#BACKGROUND} priority.
     *
     * @param request backtest parameters
     */
    public BacktestJob(@NotNull StrategyBacktestRequest request) {
        this(request, BacktestJobPriority.BACKGROUND, null);
    }

    /**
     * Creates a new job with the given priority.
     *
     * @param request    backtest parameters
     * @param priority   scheduling priority
     * @param onComplete optional completion callback (runs on worker thread)
     */
    public BacktestJob(
            @NotNull StrategyBacktestRequest request,
            @NotNull BacktestJobPriority priority,
            @Nullable Consumer<BacktestJob> onComplete) {
        this.jobId = UUID.randomUUID().toString();
        this.request = request;
        this.priority = priority;
        this.onComplete = onComplete;
        this.submittedAt = Instant.now();
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    /**
     * Transitions the job from QUEUED to RUNNING.
     * Records the worker thread so it can be interrupted later.
     */
    void markRunning(@NotNull Thread thread) {
        status.set(BacktestJobStatus.RUNNING);
        this.workerThread = thread;
        this.startedAt = Instant.now();
    }

    /** Transitions to COMPLETED and resolves the future. */
    void markCompleted(@NotNull StrategyPerformanceReport result) {
        status.set(BacktestJobStatus.COMPLETED);
        completedAt = Instant.now();
        future.complete(result);
        if (onComplete != null) {
            onComplete.accept(this);
        }
    }

    /** Transitions to FAILED and completes the future exceptionally. */
    void markFailed(@NotNull Throwable cause) {
        status.set(BacktestJobStatus.FAILED);
        completedAt = Instant.now();
        future.completeExceptionally(cause);
        if (onComplete != null) {
            onComplete.accept(this);
        }
    }

    /**
     * Requests graceful cancellation.
     *
     * <p>If the job is still queued it is immediately marked as cancelled.
     * If running, the worker thread is interrupted so the backtest loop
     * exits at the next candle iteration.
     *
     * @return {@code true} if cancellation was initiated; {@code false} if
     *         the job was already in a terminal state.
     */
    public boolean cancel() {
        BacktestJobStatus current = status.get();
        if (current == BacktestJobStatus.COMPLETED
                || current == BacktestJobStatus.FAILED
                || current == BacktestJobStatus.CANCELLED) {
            return false;
        }

        if (status.compareAndSet(current, BacktestJobStatus.CANCELLED)) {
            Thread t = workerThread;
            if (t != null) {
                t.interrupt();
            }
            completedAt = Instant.now();
            future.cancel(true);
            if (onComplete != null) {
                onComplete.accept(this);
            }
            return true;
        }
        return false;
    }

    // ─── Accessors ───────────────────────────────────────────────────────────

    @NotNull
    public String getJobId() {
        return jobId;
    }

    @NotNull
    public StrategyBacktestRequest getRequest() {
        return request;
    }

    @NotNull
    public BacktestJobPriority getPriority() {
        return priority;
    }

    @NotNull
    public BacktestJobStatus getStatus() {
        return status.get();
    }

    @NotNull
    public CompletableFuture<StrategyPerformanceReport> getFuture() {
        return future;
    }

    @NotNull
    public Instant getSubmittedAt() {
        return submittedAt;
    }

    @Nullable
    public Instant getStartedAt() {
        return startedAt;
    }

    @Nullable
    public Instant getCompletedAt() {
        return completedAt;
    }

    public boolean isTerminal() {
        BacktestJobStatus s = status.get();
        return s == BacktestJobStatus.COMPLETED
                || s == BacktestJobStatus.FAILED
                || s == BacktestJobStatus.CANCELLED;
    }

    /**
     * Duration in milliseconds from submission to completion.
     * Returns -1 if the job has not yet completed.
     */
    public long getDurationMs() {
        if (completedAt == null || startedAt == null) {
            return -1L;
        }
        return completedAt.toEpochMilli() - startedAt.toEpochMilli();
    }

    // ─── Ordering ────────────────────────────────────────────────────────────

    /**
     * Higher-priority (lower level number) jobs sort first.
     * Ties broken by submission time (FIFO within same priority).
     */
    @Override
    public int compareTo(@NotNull BacktestJob other) {
        int cmp = Integer.compare(this.priority.getLevel(), other.priority.getLevel());
        if (cmp != 0) {
            return cmp;
        }
        return this.submittedAt.compareTo(other.submittedAt);
    }

    @Override
    public String toString() {
        return "BacktestJob{"
                + "id=" + jobId.substring(0, 8)
                + ", strategy=" + request.getStrategyName()
                + ", symbol=" + request.getSymbol()
                + ", tf=" + request.getTimeframe().getCode()
                + ", priority=" + priority
                + ", status=" + status.get()
                + "}";
    }
}
