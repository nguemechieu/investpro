package org.investpro.exchange.distributed;

import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionResult;
import org.investpro.exchange.runtime.ExchangeRuntimeState;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for a cloud-hosted execution node.
 *
 * <p><b>Design-only interface for future cloud execution node implementations.</b>
 */
public interface CloudExecutionNode {

    String getNodeId();

    String getRegion();

    List<String> getSupportedExchanges();

    boolean isHealthy();

    CompletableFuture<ExecutionResult> routeExecution(ExecutionRequest request);

    CompletableFuture<Map<String, ExchangeRuntimeState>> getNodeRuntimeStates();
}
