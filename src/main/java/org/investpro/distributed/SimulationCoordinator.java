package org.investpro.distributed;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Coordinator contract for local-first, future-distributed simulation.
 */
public interface SimulationCoordinator {
    void registerWorker(DistributedWorkerNode node);

    Optional<DistributedWorkerNode> selectWorker(SimulationTask task);

    CompletableFuture<SimulationTaskResult> submit(SimulationTask task);

    List<DistributedWorkerNode> workers();
}
