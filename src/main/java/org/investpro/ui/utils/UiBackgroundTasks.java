package org.investpro.ui.utils;

import java.util.concurrent.*;

/** Bounded workers for UI-initiated reads. Saturation never runs work on the caller thread. */
public final class UiBackgroundTasks {
    private static final ThreadPoolExecutor WORKERS = new ThreadPoolExecutor(4, 4, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "ui-background-load");
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());

    private UiBackgroundTasks() {}

    public static <T> CompletableFuture<T> submit(Callable<T> operation) {
        return submit(WORKERS, operation);
    }

    static <T> CompletableFuture<T> submit(ThreadPoolExecutor executor, Callable<T> operation) {
        CompletableFuture<T> result = new CompletableFuture<>();
        FutureTask<Void> task = new FutureTask<>(() -> {
            try { result.complete(operation.call()); }
            catch (InterruptedException exception) { Thread.currentThread().interrupt(); result.completeExceptionally(exception); }
            catch (Throwable exception) { result.completeExceptionally(exception); }
            return null;
        });
        result.whenComplete((_, error) -> {
            if (result.isCancelled() || error instanceof TimeoutException) {
                task.cancel(true);
                executor.remove(task);
            }
        });
        try { executor.execute(task); }
        catch (RejectedExecutionException exception) { result.completeExceptionally(exception); }
        return result;
    }
}
