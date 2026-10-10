package org.investpro.ui.utils;

import javafx.application.Platform;
import org.investpro.core.concurrent.AppExecutors;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Coalesced snapshots plus a bounded FIFO for discrete presentation events. */
public final class UiUpdateBatcher implements AutoCloseable {
    private final ConcurrentHashMap<Object, Runnable> pending = new ConcurrentHashMap<>();
    private final ArrayBlockingQueue<Runnable> events = new ArrayBlockingQueue<>(2048);
    private final AtomicBoolean queued = new AtomicBoolean();
    private final Consumer<Runnable> dispatch;
    private final ScheduledFuture<?> timer;
    private volatile boolean closed;

    public UiUpdateBatcher() { this(Platform::runLater); }
    UiUpdateBatcher(Consumer<Runnable> dispatch) {
        this.dispatch = dispatch;
        timer = AppExecutors.SCHEDULER.scheduleWithFixedDelay(this::flush, 200, 200, TimeUnit.MILLISECONDS);
    }
    public void latest(Object key, Runnable update) {
        if (closed) return;
        if (pending.size() >= 2048 && !pending.containsKey(key))
            throw new RejectedExecutionException("UI snapshot queue is full");
        pending.put(key, update);
    }
    public void event(Runnable update) {
        if (closed) return;
        if (!events.offer(update)) throw new RejectedExecutionException("UI event queue is full");
    }
    void flush() {
        if (closed || (pending.isEmpty() && events.isEmpty()) || !queued.compareAndSet(false, true)) return;
        try {
            dispatch.accept(() -> {
                try {
                    if (closed) return;
                    for (int i = 0; i < 64; i++) {
                        var event = events.poll();
                        if (event == null) break;
                        event.run();
                    }
                    int count = 0;
                    for (var entry : pending.entrySet()) {
                        if (count++ >= 128) break;
                        if (pending.remove(entry.getKey(), entry.getValue())) entry.getValue().run();
                    }
                } finally { queued.set(false); }
            });
        } catch (IllegalStateException error) { queued.set(false); }
    }
    @Override public void close() { closed = true; timer.cancel(false); pending.clear(); events.clear(); }
}
