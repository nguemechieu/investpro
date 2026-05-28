package org.investpro.exchange.distributed;

import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionResult;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for a cloud-hosted execution node.
 *
 * <p>A cloud execution node is a dedicated compute unit that accepts execution
 * requests routed from the central {@code SmartExecutionRouter}. It may run
 * in a different availability zone or region to reduce latency to a specific
 * exchange's co-location point.
 *
 * <p><b>Implementation deferred</b>: cloud deployment and networking are not
 * yet implemented.
 */
public interface CloudExecutionNode {

    /** Returns the cloud node identifier (e.g., "aws-us-east-1-coinbase"). */
    @NotNull String nodeId();

    /** Returns the exchanges this node is co-located or optimised for. */
    @NotNull List<String> supportedExchanges();

    /** Returns the estimated round-trip latency to this node in ms. */
    long estimatedLatencyMs();

    /** Returns true if the node is healthy and accepting requests. */
    boolean isHealthy();

    /**
     * Routes an execution request to this cloud node.
     *
     * @param request the trade execution request
     * @return future resolving to the execution result
     */
    CompletableFuture<ExecutionResult> route(@NotNull ExecutionRequest request);
}
