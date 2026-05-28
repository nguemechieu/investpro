package org.investpro.exchange.distributed;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for triggering and querying distributed order reconciliation.
 *
 * <p><b>Design-only interface — no production implementation exists yet.</b>
 */
public interface DistributedReconciliationClient {

    CompletableFuture<Void> submitReconciliationRequest(String exchangeName);

    CompletableFuture<Boolean> isReconciliationComplete(String exchangeName);

    CompletableFuture<Map<String, Object>> getReconciliationReport(String exchangeName);
}
