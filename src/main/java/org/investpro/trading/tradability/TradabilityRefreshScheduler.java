package org.investpro.trading.tradability;

import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Refresh scheduler for tradability snapshots.
 */
@Slf4j
public final class TradabilityRefreshScheduler {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tradability-refresh-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Supplier<?> refreshAction;

    public TradabilityRefreshScheduler(Supplier<?> refreshAction) {
        this.refreshAction = Objects.requireNonNull(refreshAction, "refreshAction must not be null");
    }

    public void start(long intervalMinutes) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        scheduler.scheduleAtFixedRate(this::refreshSafely, intervalMinutes, intervalMinutes, TimeUnit.MINUTES);
    }

    public void refreshNow() {
        refreshSafely();
    }

    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
    }

    private void refreshSafely() {
        try {
            refreshAction.get();
        } catch (Exception exception) {
            log.warn("Tradability refresh failed", exception);
        }
    }
}