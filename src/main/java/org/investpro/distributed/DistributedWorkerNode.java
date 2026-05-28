package org.investpro.distributed;

import java.time.Instant;
import java.util.Map;

/**
 * Future remote simulation worker contract. No networking is implemented yet.
 */
public record DistributedWorkerNode(
        String nodeId,
        String host,
        int maxConcurrentTasks,
        DistributedWorkerStatus status,
        double cpuLoad,
        double heapUtilization,
        Instant lastHeartbeat,
        Map<String, String> capabilities) {

    public DistributedWorkerNode {
        nodeId = safe(nodeId);
        host = safe(host);
        maxConcurrentTasks = Math.max(1, maxConcurrentTasks);
        status = status == null ? DistributedWorkerStatus.STARTING : status;
        lastHeartbeat = lastHeartbeat == null ? Instant.EPOCH : lastHeartbeat;
        capabilities = capabilities == null ? Map.of() : Map.copyOf(capabilities);
    }

    public boolean acceptsWork() {
        return status == DistributedWorkerStatus.READY
                && cpuLoad < 0.85
                && heapUtilization < 0.85;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
