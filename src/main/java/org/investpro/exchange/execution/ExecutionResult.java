package org.investpro.exchange.execution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable outcome of an executed trade.
 *
 * <p>Captures fill details, slippage, fees, and venue information for
 * post-trade analysis, reconciliation, and the {@link org.investpro.exchange.reconciliation}
 * pipeline.
 */
public record ExecutionResult(
        @NotNull String resultId,
        @NotNull String requestId,
        @NotNull ExecutionStatus status,
        @NotNull ExecutionVenue venue,
        @NotNull String exchangeName,
        @Nullable String orderId,
        @NotNull List<FillRecord> fills,
        @Nullable BigDecimal totalFeesPaid,
        @Nullable BigDecimal slippageBps,
        @Nullable String rejectionReason,
        @NotNull Instant completedAt
) {

    /**
     * Individual fill record within an execution.
     *
     * @param fillId   exchange-assigned fill identifier
     * @param price    fill price
     * @param quantity fill quantity
     * @param filledAt fill timestamp
     */
    public record FillRecord(
            @NotNull String fillId,
            @NotNull BigDecimal price,
            @NotNull BigDecimal quantity,
            @NotNull Instant filledAt
    ) {}

    public ExecutionResult {
        Objects.requireNonNull(resultId, "resultId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(exchangeName, "exchangeName");
        Objects.requireNonNull(completedAt, "completedAt");
        fills = fills == null ? List.of() : List.copyOf(fills);
    }

    /** Returns the total quantity filled across all fill records. */
    public BigDecimal totalFilledQuantity() {
        return fills.stream()
                .map(FillRecord::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Returns the volume-weighted average fill price, or empty if no fills. */
    public Optional<BigDecimal> averageFillPrice() {
        if (fills.isEmpty()) return Optional.empty();
        BigDecimal totalQty = totalFilledQuantity();
        if (totalQty.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        BigDecimal notional = fills.stream()
                .map(f -> f.price().multiply(f.quantity()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(notional.divide(totalQty, 8, RoundingMode.HALF_UP));
    }

    /** Returns the exchange order ID if available. */
    public Optional<String> getOrderId() { return Optional.ofNullable(orderId); }

    /** Returns total fees paid if known. */
    public Optional<BigDecimal> getTotalFeesPaid() { return Optional.ofNullable(totalFeesPaid); }

    /** Returns slippage in bps if measurable. */
    public Optional<BigDecimal> getSlippageBps() { return Optional.ofNullable(slippageBps); }

    /** Returns the rejection reason if status is {@link ExecutionStatus#REJECTED}. */
    public Optional<String> getRejectionReason() { return Optional.ofNullable(rejectionReason); }

    // ── Factory methods ─────────────────────────────────────────────────────────

    /** Builds a successful result with fills. */
    public static ExecutionResult filled(
            @NotNull String requestId,
            @NotNull ExecutionRoute route,
            @NotNull String orderId,
            @NotNull List<FillRecord> fills,
            @Nullable BigDecimal fees
    ) {
        return new ExecutionResult(
                java.util.UUID.randomUUID().toString(),
                requestId,
                ExecutionStatus.FILLED,
                route.venue(),
                route.exchangeName(),
                orderId,
                fills,
                fees,
                null,
                null,
                Instant.now()
        );
    }

    /** Builds a rejected result with a reason. */
    public static ExecutionResult rejected(
            @NotNull String requestId,
            @NotNull ExecutionVenue venue,
            @NotNull String exchangeName,
            @NotNull String reason
    ) {
        return new ExecutionResult(
                java.util.UUID.randomUUID().toString(),
                requestId,
                ExecutionStatus.REJECTED,
                venue,
                exchangeName,
                null,
                List.of(),
                null,
                null,
                reason,
                Instant.now()
        );
    }
}
