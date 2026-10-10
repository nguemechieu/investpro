package org.investpro.core.concurrent;

import java.util.ArrayDeque;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/** Bounded FIFO lane over a shared pool; preserves event/agent order without a thread per symbol. */
public final class OrderedExecutor implements Executor {
    private final Executor worker;
    private final int capacity;
    private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();
    private boolean draining;

    public OrderedExecutor(Executor worker, int capacity) {
        this.worker = Objects.requireNonNull(worker);
        if (capacity < 1) throw new IllegalArgumentException("Capacity must be positive");
        this.capacity = capacity;
    }

    @Override public synchronized void execute(Runnable task) {
        Objects.requireNonNull(task);
        if (tasks.size() >= capacity) throw new RejectedExecutionException("Ordered workload queue is full");
        tasks.addLast(task);
        if (!draining) {
            draining = true;
            try { worker.execute(this::drain); }
            catch (RejectedExecutionException error) { draining = false; tasks.removeLast(); throw error; }
        }
    }

    private void drain() {
        while (true) {
            Runnable task;
            synchronized (this) {
                task = tasks.pollFirst();
                if (task == null) { draining = false; return; }
            }
            try { task.run(); }
            catch (Throwable error) { Thread thread = Thread.currentThread(); thread.getUncaughtExceptionHandler().uncaughtException(thread, error); }
        }
    }
}
