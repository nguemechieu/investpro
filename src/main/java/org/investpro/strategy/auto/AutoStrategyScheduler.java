package org.investpro.strategy.auto;

import org.investpro.config.AppConfig;
import org.investpro.strategy.StrategyAssignment;
import org.investpro.strategy.StrategyDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Supplier;

public class AutoStrategyScheduler {

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "auto-strategy-scheduler");
        thread.setDaemon(true);
        return thread;
    });
    private ScheduledFuture<?> scheduledTask;
    private final List<CompletableFuture<AutoStrategyRunResult>> runningCycles = new ArrayList<>();
    private final AtomicBoolean runInProgress = new AtomicBoolean(false);

    public synchronized void start(Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        stop();
        if (!AppConfig.getBoolean("autoStrategy.enabled", true)) {
            return;
        }
        long intervalMinutes = Math.max(1, AppConfig.getLong("autoStrategy.scanIntervalMinutes", 60));
        scheduledTask = scheduler.scheduleWithFixedDelay(task, 0L, intervalMinutes, TimeUnit.MINUTES);
    }

    public synchronized void startAutoImprovement(
            AutoStrategyLab lab,
            Supplier<List<StrategyGenerationContext>> contextSupplier,
            Function<StrategyGenerationContext, StrategyAssignment> currentAssignmentResolver,
            Function<StrategyGenerationContext, StrategyDefinition> currentStrategyResolver) {
        Objects.requireNonNull(lab, "lab must not be null");
        Objects.requireNonNull(contextSupplier, "contextSupplier must not be null");
        start(() -> runScheduledBatch(lab, contextSupplier, currentAssignmentResolver, currentStrategyResolver));
    }

    private void runScheduledBatch(
            AutoStrategyLab lab,
            Supplier<List<StrategyGenerationContext>> contextSupplier,
            Function<StrategyGenerationContext, StrategyAssignment> currentAssignmentResolver,
            Function<StrategyGenerationContext, StrategyDefinition> currentStrategyResolver) {
        if (!runInProgress.compareAndSet(false, true)) {
            return;
        }
        List<StrategyGenerationContext> contexts = contextSupplier.get();
        if (contexts == null || contexts.isEmpty()) {
            runInProgress.set(false);
            return;
        }
        boolean userApproved = AppConfig.getBoolean("autoStrategy.allowLiveAutoAssignment", false)
                && !AppConfig.getBoolean("autoStrategy.requirePaperBeforeLive", true);
        synchronized (this) {
            runningCycles.clear();
            for (StrategyGenerationContext context : contexts) {
                StrategyAssignment currentAssignment = currentAssignmentResolver == null
                        ? null
                        : currentAssignmentResolver.apply(context);
                StrategyDefinition currentStrategy = currentStrategyResolver == null
                        ? null
                        : currentStrategyResolver.apply(context);
                runningCycles.add(lab.runImprovementCycle(context, currentAssignment, currentStrategy, userApproved));
            }
            CompletableFuture
                    .allOf(runningCycles.toArray(CompletableFuture[]::new))
                    .whenComplete((ignored, throwable) -> runInProgress.set(false));
        }
    }

    public synchronized void stop() {
        if (scheduledTask != null) {
            scheduledTask.cancel(false);
            scheduledTask = null;
        }
        for (CompletableFuture<AutoStrategyRunResult> runningCycle : runningCycles) {
            runningCycle.cancel(true);
        }
        runningCycles.clear();
        runInProgress.set(false);
    }

    public void shutdown() {
        stop();
        scheduler.shutdownNow();
    }
}
