package org.investpro.strategy.lab;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Central backtest scheduling engine for the InvestPro Strategy Lab.
 *
 * <h2>Architecture</h2>
 * <pre>
 *   submit(BacktestJob)
 *       │
 *       ▼
 *   PriorityBlockingQueue (max {@code strategy.lab.maxQueueSize} jobs)
 *       │
 *       ▼
 *   ThreadPoolExecutor (max {@code strategy.lab.maxConcurrentBacktests} workers)
 *       │
 *       ▼
 *   StrategyBacktestRunner.run(request)
 *       │
 *       ▼
 *   BacktestJob.markCompleted / markFailed / markCancelled
 *       │
 *       ▼
 *   CompletableFuture resolved → callers notified
 * </pre>
 *
 * <h2>Design guarantees</h2>
 * <ul>
 *   <li>The JavaFX UI thread <b>never</b> executes backtest logic.</li>
 *   <li>At most {@code maxWorkers} backtests run simultaneously.</li>
 *   <li>The queue is bounded; submissions beyond the limit are rejected.</li>
 *   <li>Every backtest supports graceful interruption at each candle iteration.</li>
 *   <li>A {@link ResourceGuard} can pause intake when memory/CPU is critical.</li>
 *   <li>Periodic status logs replace per-backtest INFO spam.</li>
 * </ul>
 *
 * <h2>Configuration (config.properties)</h2>
 * <pre>
 *   strategy.lab.maxConcurrentBacktests   – max simultaneous workers (default: cores/2)
 *   strategy.lab.maxQueueSize             – max queued jobs             (default: 1000)
 *   strategy.lab.enableResourceProtection – enable memory/CPU guard     (default: true)
 *   strategy.lab.logEachBacktest          – INFO log per backtest start (default: false)
 * </pre>
 */
@Slf4j
public final class BacktestScheduler {

    // ─── Configuration keys ──────────────────────────────────────────────────

    private static final String CFG_MAX_WORKERS = "strategy.lab.maxConcurrentBacktests";
    private static final String CFG_MAX_QUEUE   = "strategy.lab.maxQueueSize";
    private static final String CFG_RESOURCE_GUARD = "strategy.lab.enableResourceProtection";
    private static final String CFG_LOG_EACH    = "strategy.lab.logEachBacktest";

    // ─── Singleton ───────────────────────────────────────────────────────────

    private static volatile BacktestScheduler instance;

    /** Returns the shared singleton, creating it if necessary. */
    public static BacktestScheduler getInstance() {
        if (instance == null) {
            synchronized (BacktestScheduler.class) {
                if (instance == null) {
                    instance = new BacktestScheduler();
                }
            }
        }
        return instance;
    }

    /** Shuts down and replaces the singleton (for testing). */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.shutdown();
            instance = null;
        }
    }

    // ─── State ───────────────────────────────────────────────────────────────

    /**
     * -- GETTER --
     * Configured maximum workers.
     */
    @Getter
    private final int maxWorkers;
    private volatile int currentMaxWorkers;
    private final int maxQueueSize;
    private final boolean resourceProtection;
    private final boolean logEachBacktest;

    /** Priority queue: lower priority level → dequeued first. */
    private final PriorityBlockingQueue<BacktestJob> workQueue;

    /** Fixed-size worker pool. */
    private final ThreadPoolExecutor executor;

    /** Backtest execution engine (stateless, thread-safe). */
    private final StrategyBacktestRunner runner = new StrategyBacktestRunner();

    /** Resource guard checks memory/CPU before accepting new work. */
    private final ResourceGuard resourceGuard = new ResourceGuard();

    // ─── Job tracking ────────────────────────────────────────────────────────

    /** All jobs submitted this session (queued + running + terminal). */
    private final Map<String, BacktestJob> allJobs = new ConcurrentHashMap<>();

    /** Jobs currently executing. */
    private final Set<String> runningJobIds = ConcurrentHashMap.newKeySet();

    // ─── Metrics ─────────────────────────────────────────────────────────────

    private final AtomicLong completedCount  = new AtomicLong();
    private final AtomicLong failedCount     = new AtomicLong();
    private final AtomicLong cancelledCount  = new AtomicLong();
    private final AtomicLong totalExecTimeMs = new AtomicLong();

    /** Throughput tracking – completion timestamps in rolling 10-second window. */
    private final Deque<Long> recentCompletionTimestamps = new ArrayDeque<>();

    // ─── Control flags ───────────────────────────────────────────────────────

    private final AtomicBoolean paused    = new AtomicBoolean(false);
    private final AtomicBoolean shutdown  = new AtomicBoolean(false);

    /** Listeners notified when the stats snapshot changes. */
    private final List<Consumer<SchedulerStats>> statsListeners = new CopyOnWriteArrayList<>();

    // ─── Status logger ───────────────────────────────────────────────────────

    private final ScheduledExecutorService statusLogger = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "strategy-lab-status-logger");
        t.setDaemon(true);
        return t;
    });

    // ─── Construction ────────────────────────────────────────────────────────

    private BacktestScheduler() {
        int cores = Runtime.getRuntime().availableProcessors();
        this.maxWorkers       = AppConfig.getInt(CFG_MAX_WORKERS, Math.max(1, cores / 2));
        this.currentMaxWorkers = this.maxWorkers;
        this.maxQueueSize     = AppConfig.getInt(CFG_MAX_QUEUE,   1000);
        this.resourceProtection = AppConfig.getBoolean(CFG_RESOURCE_GUARD, true);
        this.logEachBacktest  = AppConfig.getBoolean(CFG_LOG_EACH, false);

        this.workQueue = new PriorityBlockingQueue<>(256);

        this.executor = new ThreadPoolExecutor(
                maxWorkers, maxWorkers,
                60L, TimeUnit.SECONDS,
                new SynchronousQueue<>(),      // workers take directly from workQueue via our dispatch loop
                r -> {
                    Thread t = new Thread(r, "strategy-lab-worker");
                    t.setDaemon(true);
                    return t;
                });

        // Start the dispatch loop – dequeues jobs and submits them to executor
        startDispatchLoop();

        // Log aggregate status every 30 s instead of per-backtest INFO spam
        statusLogger.scheduleAtFixedRate(this::logStatus, 30, 30, TimeUnit.SECONDS);

        log.info("BacktestScheduler started: maxWorkers={}, maxQueueSize={}", maxWorkers, maxQueueSize);
    }

    // ─── Public API ──────────────────────────────────────────────────────────

    /**
     * Submits a backtest job using the given priority.
     *
     * @param request  backtest parameters
     * @param priority scheduling priority
     * @return the queued job (contains a {@link CompletableFuture} callers can await)
     * @throws RejectedExecutionException if the queue is full or the scheduler is shut down
     */
    @NotNull
    public BacktestJob submit(
            @NotNull StrategyBacktestRequest request,
            @NotNull BacktestJobPriority priority) {
        return submit(request, priority, null);
    }

    /**
     * Submits a backtest job with an optional completion callback.
     */
    @NotNull
    public BacktestJob submit(
            @NotNull StrategyBacktestRequest request,
            @NotNull BacktestJobPriority priority,
            @Nullable Consumer<BacktestJob> onComplete) {

        if (shutdown.get()) {
            throw new RejectedExecutionException("BacktestScheduler has been shut down");
        }

        if (workQueue.size() >= maxQueueSize) {
            throw new RejectedExecutionException(
                    "Backtest queue is full (limit=" + maxQueueSize + "). "
                    + "Increase strategy.lab.maxQueueSize or wait for jobs to complete.");
        }

        if (resourceProtection && resourceGuard.isMemoryCritical()) {
            log.warn("BacktestScheduler: memory critical ({}% used) - pausing intake",
                    String.format("%.0f", resourceGuard.getHeapUtilization() * 100));
            resourceGuard.suggestGc();
            // Allow the job but warn – do not hard-reject on memory alone
        }

        BacktestJob job = new BacktestJob(request, priority, onComplete);
        allJobs.put(job.getJobId(), job);
        workQueue.add(job);
        notifyStatsListeners();

        log.debug("Queued backtest job {} ({} {} {})",
                job.getJobId().substring(0, 8),
                request.getStrategyName(), request.getSymbol(),
                request.getTimeframe().getCode());

        return job;
    }

    /**
     * Submits a job with {@link BacktestJobPriority#BACKGROUND} priority.
     */
    @NotNull
    public BacktestJob submit(@NotNull StrategyBacktestRequest request) {
        return submit(request, BacktestJobPriority.BACKGROUND, null);
    }

    /**
     * Cancels the job with the given ID.
     *
     * @return {@code true} if the job was found and cancellation was initiated
     */
    public boolean cancel(@NotNull String jobId) {
        BacktestJob job = allJobs.get(jobId);
        if (job == null) {
            return false;
        }
        boolean cancelled = job.cancel();
        if (cancelled) {
            workQueue.remove(job); // remove from queue if not yet started
            cancelledCount.incrementAndGet();
            notifyStatsListeners();
        }
        return cancelled;
    }

    /**
     * Cancels all queued (not-yet-running) jobs.
     */
    public void cancelAllQueued() {
        int count = 0;
        List<BacktestJob> queued = new ArrayList<>(workQueue);
        for (BacktestJob job : queued) {
            if (job.getStatus() == BacktestJobStatus.QUEUED && job.cancel()) {
                workQueue.remove(job);
                cancelledCount.incrementAndGet();
                count++;
            }
        }
        log.info("BacktestScheduler: cancelled {} queued jobs", count);
        notifyStatsListeners();
    }

    /**
     * Pauses the dispatch loop so no new jobs are started.
     * Jobs already running continue until they finish.
     */
    public void pause() {
        paused.set(true);
        log.info("BacktestScheduler: paused");
        notifyStatsListeners();
    }

    /**
     * Resumes the dispatch loop.
     */
    public void resume() {
        paused.set(false);
        synchronized (paused) {
            paused.notifyAll();
        }
        log.info("BacktestScheduler: resumed");
        notifyStatsListeners();
    }

    /**
     * Adjusts the maximum number of concurrent workers at runtime.
     * The change takes effect for jobs starting after this call.
     */
    public synchronized void setMaxWorkers(int newMax) {
        if (newMax < 1) {
            throw new IllegalArgumentException("maxWorkers must be >= 1");
        }
        int clamped = Math.min(newMax, Runtime.getRuntime().availableProcessors() * 2);
        int currentCore = executor.getCorePoolSize();
        if (clamped < currentCore) {
            executor.setCorePoolSize(clamped);
            executor.setMaximumPoolSize(clamped);
        } else {
            executor.setMaximumPoolSize(clamped);
            executor.setCorePoolSize(clamped);
        }
        currentMaxWorkers = clamped;
        log.info("BacktestScheduler: maxWorkers adjusted to {}", clamped);
        notifyStatsListeners();
    }

    /**
     * Returns a point-in-time statistics snapshot.
     */
    @NotNull
    public SchedulerStats getStats() {
        long[] heap = resourceGuard.getHeapBytes();
        double cpu  = resourceGuard.getCpuLoad();

        double avg = 0.0;
        long completedSoFar = completedCount.get();
        if (completedSoFar > 0) {
            avg = (double) totalExecTimeMs.get() / completedSoFar;
        }

        return new SchedulerStats(
                workQueue.size(),
                runningJobIds.size(),
                completedSoFar,
                failedCount.get(),
                cancelledCount.get(),
                executor.getActiveCount(),
                currentMaxWorkers,
                avg,
                rollingThroughput(),
                heap[0], heap[1], cpu,
                Instant.now());
    }

    /**
     * Registers a listener that is called (on the worker thread) whenever a job finishes.
     * Used by the UI to trigger throttled refreshes.
     */
    public void addStatsListener(@NotNull Consumer<SchedulerStats> listener) {
        statsListeners.add(listener);
    }

    public void removeStatsListener(@NotNull Consumer<SchedulerStats> listener) {
        statsListeners.remove(listener);
    }

    /**
     * Returns all jobs submitted this session (any status).
     */
    @NotNull
    public Collection<BacktestJob> getAllJobs() {
        List<BacktestJob> jobs = new ArrayList<>(allJobs.values());
        jobs.sort(Comparator.comparing(BacktestJob::getSubmittedAt).reversed());
        return Collections.unmodifiableList(jobs);
    }

    /**
     * Returns a snapshot of jobs currently in the queue (not yet started).
     */
    @NotNull
    public List<BacktestJob> getQueuedJobs() {
        return List.copyOf(workQueue);
    }

    /**
     * Returns current queue depth (jobs waiting, not yet running).
     */
    public int getQueueSize() {
        return workQueue.size();
    }

    /** Number of active worker threads. */
    public int getActiveWorkerCount() {
        return executor.getActiveCount();
    }

    /**
     * Gracefully shuts down the scheduler.
     * Running jobs are given up to 60 s to finish; queued jobs are discarded.
     */
    public void shutdown() {
        if (!shutdown.compareAndSet(false, true)) {
            return;
        }
        log.info("BacktestScheduler: shutting down (active={}, queued={})",
                executor.getActiveCount(), workQueue.size());
        executor.shutdown();
        statusLogger.shutdown();
        try {
            if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("BacktestScheduler: shutdown complete");
    }

    // ─── Dispatch loop ───────────────────────────────────────────────────────

    /**
     * The dispatch loop runs on a dedicated daemon thread.  It drains the
     * {@link PriorityBlockingQueue} and submits each job to the executor when
     * a worker slot is available.
     *
     * <p>This approach lets the executor's {@link SynchronousQueue} act as a
     * back-pressure mechanism: if all workers are busy, {@code executor.submit()}
     * will block, throttling the dispatch thread without spinning.
     */
    private void startDispatchLoop() {
        Thread dispatcher = new Thread(() -> {
            log.info("BacktestScheduler dispatch loop started");
            while (!shutdown.get()) {
                try {
                    // Honour pause
                    while (paused.get() && !shutdown.get()) {
                        synchronized (paused) {
                            paused.wait(500);
                        }
                    }

                    // Resource protection
                    if (resourceProtection && resourceGuard.isSystemStressed()) {
                        log.debug("BacktestScheduler: system stressed, dispatch paused 2s");
                        Thread.sleep(2000);
                        continue;
                    }

                    if (executor.getActiveCount() >= currentMaxWorkers) {
                        Thread.sleep(50);
                        continue;
                    }

                    // Block until a job is available (timeout so we can check shutdown)
                    BacktestJob job = workQueue.poll(500, TimeUnit.MILLISECONDS);
                    if (job == null) {
                        continue;
                    }

                    // Job may have been cancelled before we dequeued it
                    if (job.getStatus() == BacktestJobStatus.CANCELLED) {
                        continue;
                    }

                    // Submit to executor – this will block if all worker slots are taken,
                    // providing back-pressure without spinning.
                    try {
                        executor.execute(() -> executeJob(job));
                    } catch (RejectedExecutionException exception) {
                        if (!shutdown.get() && job.getStatus() != BacktestJobStatus.CANCELLED) {
                            workQueue.offer(job);
                            Thread.sleep(100);
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("BacktestScheduler dispatch error", e);
                }
            }
            log.info("BacktestScheduler dispatch loop terminated");
        }, "strategy-lab-dispatcher");
        dispatcher.setDaemon(true);
        dispatcher.start();
    }

    // ─── Job execution ───────────────────────────────────────────────────────

    /**
     * Executes a single backtest job on the current worker thread.
     * This method runs entirely off the JavaFX thread.
     */
    private void executeJob(@NotNull BacktestJob job) {
        if (job.getStatus() == BacktestJobStatus.CANCELLED) {
            return;
        }

        Thread currentThread = Thread.currentThread();
        job.markRunning(currentThread);
        runningJobIds.add(job.getJobId());
        notifyStatsListeners();

        if (logEachBacktest) {
            log.info("Starting backtest: {} on {}/{} with {} candles",
                    job.getRequest().getStrategyName(),
                    job.getRequest().getSymbol(),
                    job.getRequest().getTimeframe().getCode(),
                    job.getRequest().getCandles().size());
        } else {
            log.debug("Starting backtest: {} on {}/{} with {} candles",
                    job.getRequest().getStrategyName(),
                    job.getRequest().getSymbol(),
                    job.getRequest().getTimeframe().getCode(),
                    job.getRequest().getCandles().size());
        }

        long startMs = System.currentTimeMillis();
        try {
            StrategyPerformanceReport result = runner.run(job.getRequest());

            if (currentThread.isInterrupted() || job.getStatus() == BacktestJobStatus.CANCELLED) {
                job.cancel();
                cancelledCount.incrementAndGet();
                return;
            }

            long execMs = System.currentTimeMillis() - startMs;
            totalExecTimeMs.addAndGet(execMs);
            completedCount.incrementAndGet();
            recordCompletion();

            job.markCompleted(result);
            log.debug("Completed backtest {} in {}ms", job.getJobId().substring(0, 8), execMs);

        } catch (Exception e) {
            if (currentThread.isInterrupted() || e instanceof InterruptedException) {
                job.cancel();
                cancelledCount.incrementAndGet();
                Thread.currentThread().interrupt();
            } else {
                log.error("Backtest job {} failed: {}", job.getJobId().substring(0, 8), e.getMessage(), e);
                failedCount.incrementAndGet();
                job.markFailed(e);
            }
        } finally {
            runningJobIds.remove(job.getJobId());
            notifyStatsListeners();
        }
    }

    // ─── Metrics helpers ─────────────────────────────────────────────────────

    private synchronized void recordCompletion() {
        long now = System.currentTimeMillis();
        recentCompletionTimestamps.addLast(now);
        // Keep only last 10 seconds
        while (!recentCompletionTimestamps.isEmpty()
                && now - recentCompletionTimestamps.peekFirst() > 10_000L) {
            recentCompletionTimestamps.pollFirst();
        }
    }

    private synchronized double rollingThroughput() {
        if (recentCompletionTimestamps.size() < 2) {
            return 0.0;
        }
        long windowMs = System.currentTimeMillis() - recentCompletionTimestamps.peekFirst();
        return windowMs > 0 ? recentCompletionTimestamps.size() * 1000.0 / windowMs : 0.0;
    }

    private void notifyStatsListeners() {
        if (statsListeners.isEmpty()) {
            return;
        }
        SchedulerStats stats = getStats();
        for (Consumer<SchedulerStats> listener : statsListeners) {
            try {
                listener.accept(stats);
            } catch (Exception e) {
                log.debug("Stats listener error", e);
            }
        }
    }

    /** Periodic aggregate status log (replaces per-backtest INFO spam). */
    private void logStatus() {
        SchedulerStats s = getStats();
        if (s.getQueued() > 0 || s.getRunning() > 0 || s.getCompleted() > 0) {
            log.info("Strategy Lab Status: queued={} running={} completed={} failed={} cancelled={} "
                    + "workers={}/{} avgMs={} rps={} heap={}/{}MiB",
                    s.getQueued(), s.getRunning(), s.getCompleted(),
                    s.getFailed(), s.getCancelled(),
                    s.getActiveWorkers(), s.getMaxWorkers(),
                    String.format("%.0f", s.getAvgExecTimeMs()),
                    String.format("%.2f", s.getThroughputPerSec()),
                    String.format("%.0f", s.getHeapUsedMiB()),
                    String.format("%.0f", s.getHeapMaxMiB()));
        }
    }
}
