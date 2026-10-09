package org.investpro.exchange.consumers;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Coalesces display snapshots only; execution, fill, trade and candle events retain their own delivery. */
final class LatestUiUpdates {
    private final ConcurrentHashMap<String, Runnable> pending = new ConcurrentHashMap<>();
    private final AtomicBoolean scheduled = new AtomicBoolean();
    private final Consumer<Runnable> dispatcher;

    LatestUiUpdates(Consumer<Runnable> dispatcher) { this.dispatcher = dispatcher; }

    void submit(String key, Runnable update) {
        pending.put(key, update);
        schedule();
    }

    private void schedule() {
        if (!scheduled.compareAndSet(false, true)) return;
        try { dispatcher.accept(this::drain); }
        catch (RuntimeException exception) { scheduled.set(false); throw exception; }
    }

    private void drain() {
        try {
            int processed = 0;
            for (var entry : pending.entrySet()) {
                if (processed++ >= 64) break;
                if (pending.remove(entry.getKey(), entry.getValue())) entry.getValue().run();
            }
        } finally {
            scheduled.set(false);
            if (!pending.isEmpty()) schedule();
        }
    }
}
