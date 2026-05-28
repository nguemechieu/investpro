package org.investpro.distributed;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-process coordinator used until remote worker networking is added.
 */
public final class LocalSimulationCoordinator implements SimulationCoordinator {
    private final Map<String, DistributedWorkerNode> workers = new ConcurrentHashMap<>();

    public LocalSimulationCoordinator() {
        registerWorker(new DistributedWorkerNode(
                "local",
                "localhost",
                Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
                DistributedWorkerStatus.READY,
                0.0,
                0.0,
                Instant.now(),
                Map.of("mode", "local")));
    }

    @Override
    public void registerWorker(DistributedWorkerNode node) {
        if (node != null && !node.nodeId().isBlank()) {
            workers.put(node.nodeId(), node);
        }
    }

    @Override
    public Optional<DistributedWorkerNode> selectWorker(SimulationTask task) {
        return workers.values().stream()
                .filter(DistributedWorkerNode::acceptsWork)
                .min(Comparator.comparingDouble(DistributedWorkerNode::cpuLoad));
    }

    @Override
    public CompletableFuture<SimulationTaskResult> submit(SimulationTask task) {
        DistributedWorkerNode worker = selectWorker(task).orElseThrow(
                () -> new IllegalStateException("No simulation worker available"));
        return CompletableFuture.completedFuture(new SimulationTaskResult(
                task.taskId(), worker.nodeId(), true, 0.0,
                "Accepted by local coordinator; execution adapter not attached yet",
                Map.of("strategyId", task.strategyId()), Instant.now()));
    }

    @Override
    public List<DistributedWorkerNode> workers() {
        return new ArrayList<>(workers.values());
    }
}
