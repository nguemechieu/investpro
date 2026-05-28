package org.investpro.exchange.execution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable record representing the selected execution path for an order.
 *
 * <p>A route is produced by {@link org.investpro.exchange.routing.SmartExecutionRouter}
 * after scoring available exchanges and venues.  It captures the estimated
 * execution quality (fill price, fees, slippage, latency) so the result can be
 * compared against actuals for post-trade analytics.
 *
 * <p>Use {@link #selected} for the common case where a specific exchange is chosen,
 * or construct the record directly for full control.
 */
public record ExecutionRoute(
        @NotNull String routeId,
        @NotNull String requestId,
        @NotNull ExecutionVenue venue,
        @NotNull String exchangeName,
        @Nullable String venueSubId,
        double estimatedFillPrice,
        double estimatedFees,
        double estimatedSlippageBps,
        long estimatedLatencyMs,
        double liquidityScore,
        @NotNull String routingReason,
        @NotNull Instant routedAt
) {

    // ── Static factory methods ────────────────────────────────────────────────

    /**
     * Creates an {@link ExecutionRoute} with the minimal information known at
     * routing time.  Slippage, latency, and liquidity receive default estimates.
     *
     * @param requestId          links back to the originating {@link ExecutionRequest}
     * @param venue              the selected execution venue type
     * @param exchangeName       the selected exchange name (e.g., "COINBASE")
     * @param estimatedFillPrice estimated fill price at time of routing
     * @param estimatedFees      estimated fee amount in quote currency
     * @return a new {@link ExecutionRoute}
     */
    public static @NotNull ExecutionRoute selected(
            @NotNull String requestId,
            @NotNull ExecutionVenue venue,
            @NotNull String exchangeName,
            double estimatedFillPrice,
            double estimatedFees
    ) {
        return new ExecutionRoute(
                UUID.randomUUID().toString(),
                requestId,
                venue,
                exchangeName,
                null,
                estimatedFillPrice,
                estimatedFees,
                0.0,
                50L,
                0.5,
                "Best available exchange by composite score",
                Instant.now()
        );
    }

    // ── Instance query methods ────────────────────────────────────────────────

    /**
     * Returns a brief human-readable summary of this route.
     *
     * @return formatted summary string
     */
    public @NotNull String summary() {
        return "ExecutionRoute[%s req=%s %s/%s%s fillPx=%.4f fees=%.4f slipBps=%.2f latMs=%d liq=%.2f]"
                .formatted(
                        routeId.substring(0, 8),
                        requestId.substring(0, 8),
                        venue.getDisplayName(),
                        exchangeName,
                        venueSubId != null ? "/" + venueSubId : "",
                        estimatedFillPrice,
                        estimatedFees,
                        estimatedSlippageBps,
                        estimatedLatencyMs,
                        liquidityScore
                );
    }
}
