package org.investpro.distributed;

import java.time.Instant;
import java.util.Map;

public record SimulationTaskResult(
        String taskId,
        String workerNodeId,
        boolean successful,
        double score,
        String summary,
        Map<String, Object> metrics,
        Instant completedAt) {

    public SimulationTaskResult {
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
        completedAt = completedAt == null ? Instant.now() : completedAt;
    }
}
