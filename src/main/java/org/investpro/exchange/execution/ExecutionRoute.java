package org.investpro.exchange.execution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable description of the route selected by {@link org.investpro.exchange.routing.SmartExecutionRouter}.
 *
 * <p>Contains scoring metadata so callers can audit why a route was chosen.
 */
public record ExecutionRoute(
        @NotNull String routeId,
        @NotNull String requestId,
        @NotNull ExecutionVenue venue,
        @NotNull String exchangeName,
        @Nullable BigDecimal estimatedSpreadBps,
        @Nullable BigDecimal estimatedFeeBps,
        @Nullable BigDecimal estimatedSlippageBps,
        @Nullable Long estimatedLatencyMs,
        @Nullable BigDecimal availableLiquidity,
        double routeScore,
        @NotNull Instant selectedAt,
        @NotNull ExecutionMode executionMode
) {

    public ExecutionRoute {
        Objects.requireNonNull(routeId, "routeId");
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(venue, "venue");
        Objects.requireNonNull(exchangeName, "exchangeName");
        Objects.requireNonNull(selectedAt, "selectedAt");
        Objects.requireNonNull(executionMode, "executionMode");
    }

    /** Source compatibility for callers predating explicit execution mode. */
    public ExecutionRoute(String routeId, String requestId, ExecutionVenue venue, String exchangeName,
                          BigDecimal spread, BigDecimal fee, BigDecimal slippage, Long latency,
                          BigDecimal liquidity, double score, Instant selectedAt) {
        this(routeId, requestId, venue, exchangeName, spread, fee, slippage, latency, liquidity,
                score, selectedAt, ExecutionMode.LIVE);
    }

    /** Returns available liquidity at the time of routing. */
    public Optional<BigDecimal> getAvailableLiquidity() { return Optional.ofNullable(availableLiquidity); }

    /** Returns the total cost estimate (spread + fee + slippage) in bps. */
    public Optional<BigDecimal> totalCostBps() {
        if (estimatedSpreadBps == null || estimatedFeeBps == null || estimatedSlippageBps == null) {
            return Optional.empty();
        }
        return Optional.of(estimatedSpreadBps.add(estimatedFeeBps).add(estimatedSlippageBps));
    }
}
