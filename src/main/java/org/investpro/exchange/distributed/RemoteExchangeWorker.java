package org.investpro.exchange.distributed;

import org.investpro.exchange.execution.ExecutionRequest;
import org.investpro.exchange.execution.ExecutionResult;
import org.investpro.exchange.runtime.ExchangeRuntimeState;

import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for a remote exchange worker process.
 *
 * <p><b>This interface is design-only and has no production implementation yet.</b>
 */
public interface RemoteExchangeWorker {

    CompletableFuture<ExecutionResult> execute(ExecutionRequest request);

    CompletableFuture<ExchangeRuntimeState> getWorkerState();

    String getWorkerId();

    String getExchangeName();

    boolean isAvailable();

    void shutdown();
}
