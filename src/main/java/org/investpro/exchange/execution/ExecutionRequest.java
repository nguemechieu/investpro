package org.investpro.exchange.execution;

import org.investpro.models.trading.TradePair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable execution request describing a single order to be routed and executed.
 *
 * <p>Use the static factory methods {@link #marketOrder} and {@link #limitOrder}
 * for common order types, or construct the record directly for full control.
 *
 * <p>Side note: {@link Side} is defined as a local enum within this file until
 * {@code org.investpro.enums.Side} is available.
 */
public record ExecutionRequest(
        @NotNull String requestId,
        @NotNull TradePair tradePair,
        @NotNull Side side,
        double quantity,
        double limitPrice,
        double stopPrice,
        @NotNull String orderType,
        @NotNull ExecutionVenue preferredVenue,
        @Nullable String exchangeName,
        boolean allowVenueSwitch,
        @NotNull Map<String, String> constraints,
        @NotNull Instant createdAt
) {

    // ── Local Side enum — use org.investpro.enums.Side when available ─────────
    public enum Side {
        BUY, SELL
    }

    // ── Compact constructor validation ────────────────────────────────────────

    public ExecutionRequest {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got: " + quantity);
        }
        constraints = Map.copyOf(constraints);
    }

    // ── Static factory methods ────────────────────────────────────────────────

    /**
     * Creates a market order execution request.
     *
     * @param tradePair the instrument to trade
     * @param side      BUY or SELL
     * @param quantity  order size in base currency units
     * @return a new {@link ExecutionRequest} for a market order
     */
    public static @NotNull ExecutionRequest marketOrder(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            double quantity
    ) {
        return new ExecutionRequest(
                UUID.randomUUID().toString(),
                tradePair,
                side,
                quantity,
                Double.NaN,
                Double.NaN,
                "MARKET",
                ExecutionVenue.CENTRALIZED,
                null,
                true,
                Map.of(),
                Instant.now()
        );
    }

    /**
     * Creates a limit order execution request.
     *
     * @param tradePair  the instrument to trade
     * @param side       BUY or SELL
     * @param quantity   order size in base currency units
     * @param limitPrice the limit price; order will not fill above (BUY) or below (SELL) this value
     * @return a new {@link ExecutionRequest} for a limit order
     */
    public static @NotNull ExecutionRequest limitOrder(
            @NotNull TradePair tradePair,
            @NotNull Side side,
            double quantity,
            double limitPrice
    ) {
        return new ExecutionRequest(
                UUID.randomUUID().toString(),
                tradePair,
                side,
                quantity,
                limitPrice,
                Double.NaN,
                "LIMIT",
                ExecutionVenue.CENTRALIZED,
                null,
                true,
                Map.of(),
                Instant.now()
        );
    }

    // ── Instance query methods ────────────────────────────────────────────────

    /**
     * Returns {@code true} if this is a market order (limitPrice is 0 or NaN).
     */
    public boolean isMarketOrder() {
        return Double.isNaN(limitPrice) || limitPrice == 0.0;
    }

    /**
     * Returns {@code true} if this is a limit or stop-limit order.
     */
    public boolean isLimitOrder() {
        return !isMarketOrder();
    }

    /**
     * Returns a brief human-readable summary of this request.
     *
     * @return formatted summary string
     */
    public @NotNull String summary() {
        return "ExecutionRequest[%s %s %s %.6f @ %s venue=%s exchange=%s]".formatted(
                requestId.substring(0, 8),
                side,
                tradePair,
                quantity,
                isMarketOrder() ? "MARKET" : String.valueOf(limitPrice),
                preferredVenue.getDisplayName(),
                exchangeName != null ? exchangeName : "(auto)"
        );
    }
}
