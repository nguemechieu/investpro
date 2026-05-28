package org.investpro.exchange.execution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record representing the outcome of an executed order.
 *
 * <p>Results are produced after the execution engine receives a fill confirmation
 * (or error) from the target exchange.  Use the static factory methods for
 * common result types:
 * <ul>
 *   <li>{@link #success} — fully filled order</li>
 *   <li>{@link #failure} — rejected or error order</li>
 *   <li>{@link #pending} — submitted but not yet confirmed</li>
 * </ul>
 */
public record ExecutionResult(
        @NotNull String resultId,
        @NotNull String requestId,
        @NotNull String routeId,
        @NotNull ExecutionStatus status,
        @Nullable String orderId,
        @Nullable String exchangeOrderId,
        double filledQuantity,
        double averageFillPrice,
        double totalFees,
        double slippageBps,
        @Nullable String errorCode,
        @Nullable String errorMessage,
        int httpStatus,
        @NotNull Instant submittedAt,
        @Nullable Instant completedAt,
        @NotNull ExecutionVenue venue,
        @NotNull String exchangeName
) {

    // ── Static factory methods ────────────────────────────────────────────────

    public static @NotNull ExecutionResult success(
            @NotNull String requestId,
            @NotNull String routeId,
            @NotNull String orderId,
            double filledQty,
            double avgPrice,
            double fees
    ) {
        Instant now = Instant.now();
        return new ExecutionResult(
                UUID.randomUUID().toString(),
                requestId,
                routeId,
                ExecutionStatus.FILLED,
                orderId,
                orderId,
                filledQty,
                avgPrice,
                fees,
                0.0,
                null,
                null,
                200,
                now,
                now,
                ExecutionVenue.CENTRALIZED,
                "UNKNOWN"
        );
    }

    public static @NotNull ExecutionResult failure(
            @NotNull String requestId,
            @NotNull String routeId,
            @Nullable String errorCode,
            @Nullable String errorMessage,
            int httpStatus
    ) {
        Instant now = Instant.now();
        return new ExecutionResult(
                UUID.randomUUID().toString(),
                requestId,
                routeId,
                ExecutionStatus.ERROR,
                null,
                null,
                0.0,
                0.0,
                0.0,
                0.0,
                errorCode,
                errorMessage,
                httpStatus,
                now,
                now,
                ExecutionVenue.CENTRALIZED,
                "UNKNOWN"
        );
    }

    public static @NotNull ExecutionResult pending(
            @NotNull String requestId,
            @NotNull String routeId,
            @NotNull String orderId
    ) {
        return new ExecutionResult(
                UUID.randomUUID().toString(),
                requestId,
                routeId,
                ExecutionStatus.SUBMITTED,
                orderId,
                orderId,
                0.0,
                0.0,
                0.0,
                0.0,
                null,
                null,
                202,
                Instant.now(),
                null,
                ExecutionVenue.CENTRALIZED,
                "UNKNOWN"
        );
    }

    // ── Instance query methods ────────────────────────────────────────────────

    public boolean isSuccessful() {
        return status == ExecutionStatus.FILLED || status == ExecutionStatus.PARTIAL_FILL;
    }

    public boolean isFailed() {
        return status == ExecutionStatus.ERROR || status == ExecutionStatus.REJECTED;
    }

    public boolean isPending() {
        return status == ExecutionStatus.PENDING
                || status == ExecutionStatus.ROUTING
                || status == ExecutionStatus.SUBMITTED;
    }

    public @NotNull String summary() {
        return "ExecutionResult[%s status=%s orderId=%s filled=%.6f avgPx=%.4f fees=%.4f exchange=%s]"
                .formatted(
                        resultId.substring(0, 8),
                        status,
                        orderId != null ? orderId : "null",
                        filledQuantity,
                        averageFillPrice,
                        totalFees,
                        exchangeName
                );
    }
}
