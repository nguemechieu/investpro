package org.investpro.distributed;

import org.investpro.enums.timeframe.Timeframe;
import org.investpro.strategy.provider.StrategyComplexity;

import java.time.Instant;
import java.util.Map;

/**
 * Serializable strategy simulation work item for local or future remote workers.
 */
public record SimulationTask(
        String taskId,
        String strategyId,
        String symbol,
        Timeframe timeframe,
        long startEpochSecond,
        long endEpochSecond,
        int requestedBars,
        StrategyComplexity complexity,
        Map<String, String> parameters,
        Instant createdAt) {

    public SimulationTask {
        taskId = safe(taskId);
        strategyId = safe(strategyId);
        symbol = safe(symbol);
        complexity = complexity == null ? StrategyComplexity.MEDIUM : complexity;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        createdAt = createdAt == null ? Instant.now() : createdAt;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
