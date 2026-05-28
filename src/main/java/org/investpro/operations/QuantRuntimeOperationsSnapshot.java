package org.investpro.operations;

import org.investpro.execution.lifecycle.ExecutionLifecycleStatus;
import org.investpro.portfolio.intelligence.PortfolioIntelligenceSnapshot;
import org.investpro.strategy.lab.AdaptiveSchedulerDecision;
import org.investpro.strategy.lab.SchedulerStats;
import org.investpro.strategy.provider.StrategyDescriptor;
import org.investpro.telemetry.EventBusMetricsSnapshot;
import org.investpro.telemetry.RuntimeMetricSnapshot;
import org.investpro.telemetry.StrategyProfileSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * Unified runtime operations view for the institutional operations board.
 */
public record QuantRuntimeOperationsSnapshot(
        RuntimeMetricSnapshot runtime,
        EventBusMetricsSnapshot eventBus,
        SchedulerStats scheduler,
        AdaptiveSchedulerDecision schedulerDecision,
        List<StrategyDescriptor> strategyDescriptors,
        List<StrategyProfileSnapshot> slowestStrategies,
        List<ExecutionLifecycleStatus> activeExecutions,
        PortfolioIntelligenceSnapshot portfolio,
        List<String> warnings,
        Instant capturedAt) {

    public QuantRuntimeOperationsSnapshot {
        strategyDescriptors = strategyDescriptors == null ? List.of() : List.copyOf(strategyDescriptors);
        slowestStrategies = slowestStrategies == null ? List.of() : List.copyOf(slowestStrategies);
        activeExecutions = activeExecutions == null ? List.of() : List.copyOf(activeExecutions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        capturedAt = capturedAt == null ? Instant.now() : capturedAt;
    }
}
