package org.investpro.exchange.distributed;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for triggering and querying distributed order reconciliation.
 *
 * <p>Reconciliation ensures that the local order state (open orders, fills, positions)
 * is consistent with the exchange's authoritative records.  In a distributed deployment,
 * each exchange worker performs independent reconciliation; this client provides a
 * centralised API for orchestrating and monitoring the process.
 *
 * <p>Typical usage:
 * <ol>
 *   <li>Call {@link #submitReconciliationRequest} after detecting a potential inconsistency.</li>
 *   <li>Poll {@link #isReconciliationComplete} until the reconciliation finishes.</li>
 *   <li>Call {@link #getReconciliationReport} to inspect any discrepancies found.</li>
 * </ol>
 *
 * <p><b>Design-only interface — no production implementation exists yet.</b>
 *
 * @see RemoteExchangeWorker
 * @see CloudExecutionNode
 */
public interface DistributedReconciliationClient {

    /**
     * Submits a reconciliation request for the given exchange.
     *
     * <p>The reconciliation process will compare local state with exchange
     * records and trigger corrective actions for any discrepancies found.
     *
     * @param exchangeName the exchange to reconcile (e.g., "COINBASE")
     * @return a future that completes when the reconciliation request has been accepted
     */
    CompletableFuture<Void> submitReconciliationRequest(String exchangeName);

    /**
     * Checks whether the reconciliation process for the given exchange has completed.
     *
     * @param exchangeName the exchange to check
     * @return a future resolving to {@code true} if reconciliation is complete,
     *         {@code false} if it is still in progress
     */
    CompletableFuture<Boolean> isReconciliationComplete(String exchangeName);

    /**
     * Returns the reconciliation report for the given exchange.
     *
     * <p>The report is a generic {@code Map<String, Object>} to allow flexibility
     * in the structure.  Expected keys include:
     * <ul>
     *   <li>{@code "exchangeName"} — exchange identifier</li>
     *   <li>{@code "reconciliationId"} — unique run identifier</li>
     *   <li>{@code "discrepancyCount"} — number of differences found</li>
     *   <li>{@code "correctiveActions"} — list of actions taken</li>
     *   <li>{@code "completedAt"} — ISO-8601 timestamp of completion</li>
     * </ul>
     *
     * @param exchangeName the exchange to retrieve the report for
     * @return a future resolving to the reconciliation report
     */
    CompletableFuture<Map<String, Object>> getReconciliationReport(String exchangeName);
}
