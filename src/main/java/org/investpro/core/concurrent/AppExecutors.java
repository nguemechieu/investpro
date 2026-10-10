package org.investpro.core.concurrent;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Application-owned workload isolation. Bot stop must not shut these workers down. */
public final class AppExecutors {
    public static final ThreadPoolExecutor MARKET_DATA = pool("market-data", 4, 128);
    // Separate from polling workers, which may wait for these HTTP requests.
    public static final ThreadPoolExecutor COINBASE_REST = pool("coinbase-rest", 2, 128);
    public static final ThreadPoolExecutor STRATEGY = pool("strategy", 4, 256);
    public static final ThreadPoolExecutor TRADING = pool("trading", 2, 64);
    public static final ThreadPoolExecutor RISK = pool("risk", 2, 64);
    public static final ThreadPoolExecutor BACKTEST = pool("backtest", 2, 32);
    public static final ThreadPoolExecutor IO = pool("io", 4, 128);
    public static final ThreadPoolExecutor ASSISTANT = pool("assistant", 2, 32);
    public static final ScheduledThreadPoolExecutor SCHEDULER = new ScheduledThreadPoolExecutor(2, named("scheduler"));
    static { SCHEDULER.setRemoveOnCancelPolicy(true); }

    private AppExecutors() {}

    private static final ConcurrentLinkedQueue<CompletableFuture<?>> cleanups = new ConcurrentLinkedQueue<>();

    /** Registered teardown finishes before application-owned workers are stopped. */
    public static CompletableFuture<Void> cleanup(Runnable operation) {
        var result = submit(IO, () -> { operation.run(); return (Void) null; });
        cleanups.add(result);
        return result;
    }

    public static CompletableFuture<Void> finishShutdown() {
        return CompletableFuture.allOf(cleanups.toArray(CompletableFuture[]::new))
                .handle((_, error) -> { shutdown(); return null; });
    }

    static ThreadPoolExecutor pool(String name, int workers, int capacity) {
        return new ThreadPoolExecutor(workers, workers, 30, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacity), named(name), new ThreadPoolExecutor.AbortPolicy());
    }

    private static ThreadFactory named(String name) {
        AtomicInteger count = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, "investpro-" + name + "-" + count.incrementAndGet());
            thread.setDaemon(true); return thread;
        };
    }

    /** Rejection is a failed future, never caller-thread execution or an unanswered future. */
    public static <T> CompletableFuture<T> submit(Executor executor, Callable<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            try { result.complete(operation.call()); }
            catch (InterruptedException error) { Thread.currentThread().interrupt(); result.completeExceptionally(error); }
            catch (Throwable error) { result.completeExceptionally(error); }
            return null;
        }) {
            @Override protected void done() { if (isCancelled()) result.cancel(false); }
        };
        result.whenComplete((_, error) -> {
            if (result.isCancelled() || error instanceof TimeoutException) {
                task.cancel(true);
                if (executor instanceof ThreadPoolExecutor pool) pool.remove(task);
            }
        });
        try { executor.execute(task); }
        catch (RejectedExecutionException error) { result.completeExceptionally(error); }
        return result;
    }

    public static void shutdown() {
        SCHEDULER.shutdownNow();
        for (ExecutorService worker : new ExecutorService[]{MARKET_DATA, COINBASE_REST, STRATEGY, TRADING, RISK, BACKTEST, IO, ASSISTANT})
            for (Runnable pending : worker.shutdownNow())
                if (pending instanceof Future<?> future) future.cancel(true);
    }
}
