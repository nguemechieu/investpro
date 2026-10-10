package org.investpro.ui.utils;

import javafx.animation.*;
import javafx.application.Platform;
import javafx.util.Duration;
import org.investpro.core.concurrent.AppExecutors;
import org.slf4j.LoggerFactory;
import java.util.concurrent.*;

/** Background watchdog; never queues diagnostic work onto an already stalled UI. */
public final class UiFreezeDetector implements AutoCloseable {
    private volatile long lastPulse;
    private long lastWarning;
    private Timeline pulse;
    private ScheduledFuture<?> monitor;
    public void start() {
        if (!Platform.isFxApplicationThread()) throw new IllegalStateException("Start watchdog on JavaFX thread");
        if (pulse != null) return;
        Thread ui = Thread.currentThread();
        lastPulse = System.nanoTime();
        pulse = new Timeline(new KeyFrame(Duration.millis(250), _ -> lastPulse = System.nanoTime()));
        pulse.setCycleCount(Animation.INDEFINITE); pulse.play();
        monitor = AppExecutors.SCHEDULER.scheduleWithFixedDelay(() -> {
            long now = System.nanoTime();
            long delay = TimeUnit.NANOSECONDS.toMillis(now - lastPulse);
            if (delay > 1500 && now - lastWarning > TimeUnit.SECONDS.toNanos(30)) {
                lastWarning = now;
                LoggerFactory.getLogger(UiFreezeDetector.class).warn("JavaFX heartbeat delayed {} ms. UI stack: {}",
                        delay, java.util.Arrays.toString(ui.getStackTrace()));
            }
        }, 1, 1, TimeUnit.SECONDS);
    }
    @Override public void close() {
        if (monitor != null) monitor.cancel(false);
        if (pulse != null) {
            if (Platform.isFxApplicationThread()) pulse.stop(); else Platform.runLater(pulse::stop);
        }
    }
}
