package org.investpro.exchange.distributed;

import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * Design-only interface for a distributed reconciliation client.
 *
 * <p>In a multi-node deployment, reconciliation data (balance diffs, fill
 * confirmations, position changes) can be published to a shared topic or
 * durable queue. This interface abstracts that publishing contract.
 *
 * <p><b>Implementation deferred</b>: message broker integration is not yet
 * implemented. Future implementations may use Kafka, RabbitMQ, or Azure
 * Service Bus.
 */
public interface DistributedReconciliationClient {

    /**
     * Publishes a reconciliation event to the distributed topic.
     *
     * @param exchangeName  originating exchange
     * @param eventType     one of: "BALANCE_DRIFT", "POSITION_DRIFT", "FILL_MISSING"
     * @param payload       serialisable payload object
     * @param occurredAt    when the drift was detected
     * @return future that completes when the event is acknowledged
     */
    CompletableFuture<Void> publishReconciliationEvent(
            @NotNull String exchangeName,
            @NotNull String eventType,
            @NotNull Object payload,
            @NotNull Instant occurredAt
    );

    /**
     * Requests the latest reconciliation snapshot from the distributed store.
     *
     * @param exchangeName the exchange to query
     * @return future resolving to a JSON-compatible snapshot object
     */
    CompletableFuture<Object> fetchLatestSnapshot(@NotNull String exchangeName);
}
