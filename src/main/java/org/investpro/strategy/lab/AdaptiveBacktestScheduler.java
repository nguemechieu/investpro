package org.investpro.strategy.lab;

import lombok.extern.slf4j.Slf4j;
import org.investpro.config.AppConfig;
import org.investpro.telemetry.RuntimeMetricSnapshot;
import org.investpro.telemetry.RuntimeTelemetryService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resource-aware controller around the stable {@link BacktestScheduler}.
 *
 * <p>This class does not replace the existing scheduler API. It adapts worker
 * concurrency using heap, CPU, and queue pressure, preparing Strategy Lab for
 * heavier local and future distributed workloads.</p>
 */
@Slf4j
public final class AdaptiveBacktestScheduler {
    private static volatile AdaptiveBacktestScheduler instance;

    private final BacktestScheduler delegate;
    private final RuntimeTelemetryService telemetry;
    private final int minWorkers;
    private final int maxWorkers;
    private final double highHeapThreshold;
    private final double highCpuThreshold;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private volatile AdaptiveSchedulerDecision lastDecision;
    private ScheduledExecutorService controller;

    private AdaptiveBacktestScheduler(BacktestScheduler delegate) {
        this.delegate = delegate;
        this.telemetry = RuntimeTelemetryService.getInstance();
        int cores = Runtime.getRuntime().availableProcessors();
        this.minWorkers = Math.max(1, AppConfig.getInt("strategy.lab.adaptive.minWorkers", 1));
        this.maxWorkers = Math.max(minWorkers, AppConfig.getInt("strategy.lab.adaptive.maxWorkers", Math.max(1, cores / 2)));
        this.highHeapThreshold = AppConfig.getDouble("strategy.lab.adaptive.highHeapThreshold", 0.82);
        this.highCpuThreshold = AppConfig.getDouble("strategy.lab.adaptive.highCpuThreshold", 0.85);
        this.lastDecision = new AdaptiveSchedulerDecision(minWorkers, false, false, List.of("Not started"), null);
    }

    public static AdaptiveBacktestScheduler getInstance() {
        AdaptiveBacktestScheduler local = instance;
        if (local == null) {
            synchronized (AdaptiveBacktestScheduler.class) {
                local = instance;
                if (local == null) {
                    local = new AdaptiveBacktestScheduler(BacktestScheduler.getInstance());
                    instance = local;
                }
            }
        }
        return local;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        controller = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "adaptive-backtest-scheduler");
            t.setDaemon(true);
            return t;
        });
        controller.scheduleAtFixedRate(this::rebalanceSafely, 2, 5, TimeUnit.SECONDS);
        log.info("AdaptiveBacktestScheduler started: workers={}..{}", minWorkers, maxWorkers);
    }

    public void stop() {
        started.set(false);
        if (controller != null) {
            controller.shutdownNow();
        }
    }

    public BacktestJob submit(@NotNull StrategyBacktestRequest request, @NotNull BacktestJobPriority priority) {
        start();
        return delegate.submit(request, priority);
    }

    public SchedulerStats stats() {
        return delegate.getStats();
    }

    public AdaptiveSchedulerDecision lastDecision() {
        return lastDecision;
    }

    private void rebalanceSafely() {
        try {
            rebalance();
        } catch (Exception exception) {
            log.debug("Adaptive scheduler rebalance failed: {}", exception.getMessage(), exception);
        }
    }

    private void rebalance() {
        SchedulerStats stats = delegate.getStats();
        RuntimeMetricSnapshot runtime = telemetry.snapshot();
        double cpu = runtime.systemCpuLoad() >= 0.0 ? runtime.systemCpuLoad() : stats.getSystemCpuLoad();
        double heap = runtime.heapUtilization();
        List<String> warnings = new ArrayList<>(3);

        int recommended = stats.getMaxWorkers();
        boolean stressed = false;
        if (heap >= highHeapThreshold) {
            recommended = Math.max(minWorkers, recommended - 1);
            warnings.add("High heap pressure");
            stressed = true;
        }
        if (cpu >= highCpuThreshold) {
            recommended = Math.max(minWorkers, recommended - 1);
            warnings.add("High CPU pressure");
            stressed = true;
        }
        if (!stressed && stats.getQueued() > stats.getRunning() * 4 && recommended < maxWorkers) {
            recommended++;
        }

        if (recommended != stats.getMaxWorkers()) {
            delegate.setMaxWorkers(recommended);
        }

        boolean pauseLowPriority = stressed && stats.getQueued() > 0;
        lastDecision = new AdaptiveSchedulerDecision(recommended, pauseLowPriority, stressed, warnings, null);
    }
}
