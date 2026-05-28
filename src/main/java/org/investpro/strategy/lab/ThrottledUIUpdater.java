package org.investpro.strategy.lab;

import javafx.application.Platform;
import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Throttles JavaFX UI updates so that {@link Platform#runLater(Runnable)} is
 * never called more often than once per configured interval.
 *
 * <h2>Problem</h2>
 * When hundreds of backtests complete rapidly, naive code calls
 * {@code Platform.runLater()} for every single result. This floods the JavaFX
 * event queue, makes the UI unresponsive, and can consume as much CPU as the
 * backtest computation itself.
 *
 * <h2>Solution</h2>
 * The updater collects notifications on background threads into a shared
 * accumulator, then drains the accumulator in a <em>single</em>
 * {@code Platform.runLater()} call at most once per {@code uiUpdateThrottleMs}.
 *
 * <h2>Usage</h2>
 * <pre>
 *   ThrottledUIUpdater updater = new ThrottledUIUpdater(myPanel::refreshStats);
 *   scheduler.addStatsListener(stats -> updater.enqueue(stats));
 *   // later…
 *   updater.shutdown();
 * </pre>
 *
 * <h2>Configuration</h2>
 * {@code strategy.lab.uiUpdateThrottleMs} (default 300 ms)
 */
@Slf4j
public final class ThrottledUIUpdater<T> {

    private static final String CFG_THROTTLE_MS = "strategy.lab.uiUpdateThrottleMs";
    private static final long DEFAULT_THROTTLE_MS = 300L;

    /** Pending items accumulated since the last UI flush. */
    @SuppressWarnings("unused")
    private final List<T> pendingItems = new ArrayList<>();

    /** Guard for the pendingItems list. */
    @SuppressWarnings("unused")
    private final Object lock = new Object();

    /** Whether a flush is already scheduled. */
    private final AtomicBoolean flushScheduled = new AtomicBoolean(false);

    /** Latest snapshot reference (replaces older pending values for "last wins" semantics). */
    private final AtomicReference<T> latestSnapshot = new AtomicReference<>();

    /** How often to flush to the JavaFX thread (milliseconds). */
    private final long throttleMs;

    /** The action to run on the JavaFX thread when a flush occurs. */
    private final Consumer<T> uiAction;

    /** Whether to use "last wins" mode (only pass the latest value, not a list). */
    @SuppressWarnings("unused")
    private final boolean lastWinsMode;

    /** Timer used to schedule the deferred flush. */
    private final ScheduledExecutorService timer;

    /**
     * Creates a throttled updater that calls {@code uiAction} with the
     * <em>latest</em> value at most once per {@code throttleMs} milliseconds.
     *
     * @param uiAction   action executed on the JavaFX Application Thread
     */
    public ThrottledUIUpdater(@org.jetbrains.annotations.NotNull Consumer<T> uiAction) {
        this.uiAction = uiAction;
        this.throttleMs = AppConfig.getLong(CFG_THROTTLE_MS, DEFAULT_THROTTLE_MS);
        this.lastWinsMode = true;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "strategy-lab-ui-throttler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Creates a throttled updater with a custom throttle interval.
     *
     * @param uiAction    action executed on the JavaFX Application Thread
     * @param throttleMs  minimum milliseconds between UI flushes
     */
    public ThrottledUIUpdater(
            @org.jetbrains.annotations.NotNull Consumer<T> uiAction,
            long throttleMs) {
        this.uiAction = uiAction;
        this.throttleMs = Math.max(50, throttleMs);
        this.lastWinsMode = true;
        this.timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "strategy-lab-ui-throttler");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Enqueues a new value.  Thread-safe; may be called from any thread.
     *
     * <p>If a flush is not already scheduled, schedules one for
     * {@link #throttleMs} milliseconds in the future.  Repeated calls before
     * the flush fires simply update the "latest" value and do not add extra
     * {@code Platform.runLater()} calls.
     *
     * @param value the value to deliver to the UI action
     */
    public void enqueue(T value) {
        if (value == null) {
            return;
        }
        latestSnapshot.set(value);

        if (flushScheduled.compareAndSet(false, true)) {
            timer.schedule(this::flush, throttleMs, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Forces an immediate flush regardless of the throttle timer.
     * Useful when the user explicitly requests a refresh.
     */
    public void forceFlush() {
        T value = latestSnapshot.getAndSet(null);
        if (value != null) {
            flushScheduled.set(false);
            Platform.runLater(() -> {
                try {
                    uiAction.accept(value);
                } catch (Exception e) {
                    log.error("ThrottledUIUpdater: flush error", e);
                }
            });
        }
    }

    /**
     * Shuts down the internal timer.
     * Any pending value is flushed synchronously before shutdown.
     */
    public void shutdown() {
        forceFlush();
        timer.shutdown();
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private void flush() {
        flushScheduled.set(false);
        T value = latestSnapshot.getAndSet(null);
        if (value == null) {
            return;
        }
        Platform.runLater(() -> {
            try {
                uiAction.accept(value);
            } catch (Exception e) {
                log.error("ThrottledUIUpdater: UI action error", e);
            }
        });
    }
}
