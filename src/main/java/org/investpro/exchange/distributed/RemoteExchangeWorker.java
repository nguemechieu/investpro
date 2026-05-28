package org.investpro.exchange.distributed;

import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionResult;
import org.investpro.exchange.normalization.NormalizedMarketSnapshot;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for a remote exchange worker node.
 *
 * <p>In a distributed deployment, each exchange adapter can run as an
 * independent worker process (JVM, container, or serverless function).
 * This interface defines the contract that the central orchestrator uses
 * to communicate with remote workers.
 *
 * <p><b>Implementation deferred</b>: networking and serialization are not
 * yet implemented. Future implementations may use gRPC, Kafka, or REST.
 */
public interface RemoteExchangeWorker {

    /** Returns the unique worker identifier. */
    @NotNull String workerId();

    /** Returns the name of the exchange this worker handles. */
    @NotNull String exchangeName();

    /** Returns true if this worker is currently reachable and processing requests. */
    boolean isAlive();

    /**
     * Requests the latest normalized market snapshot from the remote worker.
     *
     * @param symbol the trading pair symbol
     * @return future resolving to a snapshot, or empty if unavailable
     */
    CompletableFuture<Optional<NormalizedMarketSnapshot>> fetchSnapshot(@NotNull String symbol);

    /**
     * Submits an execution request to the remote worker for processing.
     *
     * @param request the trade execution request
     * @return future resolving to the execution result
     */
    CompletableFuture<ExecutionResult> submitExecution(@NotNull ExecutionRequest request);

    /**
     * Gracefully shuts down the remote worker connection.
     *
     * <p>After this call, {@link #isAlive()} must return false.
     */
    void shutdown();
}
