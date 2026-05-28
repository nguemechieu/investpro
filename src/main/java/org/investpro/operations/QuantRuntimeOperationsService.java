package org.investpro.operations;

import org.investpro.execution.lifecycle.ExecutionLifecycleMonitor;
import org.investpro.portfolio.intelligence.PortfolioIntelligenceService;
import org.investpro.strategy.lab.AdaptiveBacktestScheduler;
import org.investpro.strategy.provider.StrategyProviderRegistry;
import org.investpro.telemetry.EventBusMetricsEngine;
import org.investpro.telemetry.RuntimeTelemetryService;
import org.investpro.telemetry.StrategyProfiler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Non-blocking aggregator for the real-time system operations board.
 */
public final class QuantRuntimeOperationsService {
    private static final QuantRuntimeOperationsService INSTANCE = new QuantRuntimeOperationsService();

    private final RuntimeTelemetryService runtimeTelemetry = RuntimeTelemetryService.getInstance();
    private final EventBusMetricsEngine eventBusMetrics = EventBusMetricsEngine.getInstance();
    private final StrategyProviderRegistry strategyProviderRegistry = StrategyProviderRegistry.getInstance();
    private final StrategyProfiler strategyProfiler = StrategyProfiler.getInstance();
    private final ExecutionLifecycleMonitor lifecycleMonitor = ExecutionLifecycleMonitor.getInstance();
    private final PortfolioIntelligenceService portfolioIntelligence = PortfolioIntelligenceService.getInstance();

    private QuantRuntimeOperationsService() {
    }

    public static QuantRuntimeOperationsService getInstance() {
        return INSTANCE;
    }

    public QuantRuntimeOperationsSnapshot snapshot() {
        AdaptiveBacktestScheduler scheduler = AdaptiveBacktestScheduler.getInstance();
        var runtime = runtimeTelemetry.snapshot();
        var schedulerStats = scheduler.stats();
        List<String> warnings = new ArrayList<>();

        if (runtime.heapUtilization() > 0.85) {
            warnings.add("Heap pressure is high");
        }
        if (runtime.systemCpuLoad() > 0.85) {
            warnings.add("CPU saturation is high");
        }
        if (schedulerStats.getQueued() > schedulerStats.getMaxWorkers() * 20) {
            warnings.add("Strategy Lab queue pressure is high");
        }
        if (eventBusMetrics.snapshot().slowConsumerEvents() > 0) {
            warnings.add("Slow event-bus consumers detected");
        }

        return new QuantRuntimeOperationsSnapshot(
                runtime,
                eventBusMetrics.snapshot(),
                schedulerStats,
                scheduler.lastDecision(),
                strategyProviderRegistry.descriptors(),
                strategyProfiler.topByAverageLatency(10),
                lifecycleMonitor.active(),
                portfolioIntelligence.snapshot(),
                warnings,
                Instant.now());
    }
}
