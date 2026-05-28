package org.investpro.exchange.distributed;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionResult;

/**
 * Design-only interface for a cloud-hosted execution node.
 *
 * <p>A {@code CloudExecutionNode} represents a geographically distributed
 * execution host that manages one or more {@link RemoteExchangeWorker} instances.
 * Nodes may be deployed in cloud regions close to specific exchange matching
 * engines to minimise latency.
 *
 * <p>The {@link org.investpro.exchange.routing.SmartExecutionRouter} will
 * eventually select nodes based on latency and supported exchanges in addition
 * to the current per-exchange scoring.
 *
 * <p><b>Design-only interface for future cloud execution node implementations.</b>
 * No production implementation exists yet.
 *
 * @see RemoteExchangeWorker
 * @see DistributedReconciliationClient
 */
public interface CloudExecutionNode {

    /**
     * Returns the unique identifier for this cloud execution node.
     * Node IDs should be stable across restarts and are used for audit logging.
     *
     * @return the node ID string (e.g., "node-us-east-1-a")
     */
    String getNodeId();

    /**
     * Returns the cloud region or data-centre where this node is deployed.
     *
     * @return the region string (e.g., "us-east-1", "eu-west-2")
     */
    String getRegion();

    /**
     * Returns the list of exchange names supported by this node.
     * Only exchanges in this list can be routed to this node.
     *
     * @return unmodifiable list of supported exchange names
     */
    List<String> getSupportedExchanges();

    /**
     * Returns {@code true} if this node is currently healthy and accepting
     * execution requests.  Unhealthy nodes should not receive new routing.
     *
     * @return {@code true} if the node is healthy
     */
    boolean isHealthy();

    /**
     * Routes an execution request to the most appropriate worker on this node.
     *
     * @param request the execution request to route and execute
     * @return a future resolving to the {@link ExecutionResult}
     */
    CompletableFuture<ExecutionResult> routeExecution(ExecutionRequest request);

    /**
     * Returns the runtime states of all exchange workers managed by this node.
     *
     * @return a future resolving to a map of exchange name → {@link ExchangeRuntimeState}
     */
    CompletableFuture<Map<String, ExchangeRuntimeState>> getNodeRuntimeStates();
}
