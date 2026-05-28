package org.investpro.execution.lifecycle;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Point-in-time status of an order or blockchain execution.
 */
public record ExecutionLifecycleStatus(
        String clientOrderId,
        String brokerOrderId,
        String venueId,
        String symbol,
        ExecutionLifecycleState state,
        BigDecimal requestedQuantity,
        BigDecimal filledQuantity,
        BigDecimal averageFillPrice,
        String reason,
        int blockchainConfirmations,
        Instant updatedAt,
        Map<String, Object> metadata) {

    public ExecutionLifecycleStatus {
        clientOrderId = safe(clientOrderId);
        brokerOrderId = safe(brokerOrderId);
        venueId = safe(venueId);
        symbol = safe(symbol);
        state = state == null ? ExecutionLifecycleState.CREATED : state;
        requestedQuantity = value(requestedQuantity);
        filledQuantity = value(filledQuantity);
        averageFillPrice = value(averageFillPrice);
        reason = safe(reason);
        updatedAt = updatedAt == null ? Instant.now() : updatedAt;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public boolean terminal() {
        return state == ExecutionLifecycleState.FILLED
                || state == ExecutionLifecycleState.REJECTED
                || state == ExecutionLifecycleState.CANCELLED
                || state == ExecutionLifecycleState.EXPIRED
                || state == ExecutionLifecycleState.BLOCKCHAIN_FAILED;
    }

    private static BigDecimal value(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
