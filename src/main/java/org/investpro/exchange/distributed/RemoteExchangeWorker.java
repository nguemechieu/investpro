package org.investpro.exchange.distributed;

import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionResult;

import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for a remote exchange worker process.
 *
 * <p>In the future distributed architecture, each {@code RemoteExchangeWorker}
 * represents a single worker process (potentially on a remote host or container)
 * that is dedicated to a single exchange connection.  Workers communicate with the
 * main process via a message bus or RPC layer (implementation TBD).
 *
 * <p>The interface is intentionally thin so that multiple transport implementations
 * (gRPC, Redis Streams, AMQP, etc.) can satisfy it without coupling to a specific
 * technology.
 *
 * <p><b>This interface is design-only and has no production implementation yet.</b>
 * It is provided to anchor the distributed execution architecture so that dependent
 * interfaces and orchestration code can reference a stable contract.
 *
 * @see CloudExecutionNode
 * @see DistributedReconciliationClient
 */
public interface RemoteExchangeWorker {

    /**
     * Submits an execution request to this worker and returns the result asynchronously.
     *
     * @param request the execution request to process
     * @return a future resolving to the {@link ExecutionResult}
     */
    CompletableFuture<ExecutionResult> execute(ExecutionRequest request);

    /**
     * Returns the current runtime state of this worker.
     *
     * <p>State information includes connectivity health, pending order counts,
     * and circuit breaker status for the exchange this worker manages.
     *
     * @return a future resolving to the worker's {@link ExchangeRuntimeState}
     */
    CompletableFuture<ExchangeRuntimeState> getWorkerState();

    /**
     * Returns the unique identifier for this worker instance.
     * Worker IDs are stable across reconnects and should be persisted for
     * reconciliation and audit purposes.
     *
     * @return the worker ID string
     */
    String getWorkerId();

    /**
     * Returns the name of the exchange this worker is dedicated to.
     * Each worker manages exactly one exchange connection.
     *
     * @return the exchange name (e.g., "COINBASE", "BINANCE")
     */
    String getExchangeName();

    /**
     * Returns {@code true} if this worker is currently available to accept
     * execution requests.  A worker may be unavailable due to circuit breaker
     * state, scheduled maintenance, or network partitions.
     *
     * @return {@code true} if execution requests can be sent to this worker
     */
    boolean isAvailable();

    /**
     * Initiates a graceful shutdown of this worker.
     * In-flight requests should be allowed to complete; new requests should be rejected.
     */
    void shutdown();
}
