package org.investpro.exchange.execution;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable request to execute a trade across any supported venue.
 *
 * <p>Build via {@link #builder(String, Side, BigDecimal)} and pass to
 * {@link org.investpro.exchange.routing.SmartExecutionRouter}.
 *
 * <p>Note: if {@code org.investpro.enums.Side} exists in the codebase,
 * replace the inner {@link Side} enum with that import.
 */
public record ExecutionRequest(
        @NotNull String requestId,
        @NotNull String symbol,
        @NotNull Side side,
        @NotNull BigDecimal quantity,
        @Nullable BigDecimal limitPrice,
        @NotNull ExecutionVenue preferredVenue,
        boolean allowFallback,
        @Nullable String preferredExchange,
        @Nullable BigDecimal maxSlippageBps,
        @Nullable BigDecimal maxFeeBps,
        boolean paperMode,
        @NotNull Instant createdAt
) {

    /**
     * Trade direction.
     *
     * <p>Swap for {@code org.investpro.enums.Side} if that type already exists.
     */
    public enum Side {
        BUY, SELL
    }

    public ExecutionRequest {
        Objects.requireNonNull(requestId, "requestId");
        Objects.requireNonNull(symbol, "symbol");
        Objects.requireNonNull(side, "side");
        Objects.requireNonNull(quantity, "quantity");
        Objects.requireNonNull(preferredVenue, "preferredVenue");
        Objects.requireNonNull(createdAt, "createdAt");
        if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
    }

    /** Returns the limit price if this is a limit order. */
    public Optional<BigDecimal> getLimitPrice() { return Optional.ofNullable(limitPrice); }

    /** Returns true if this is a market order. */
    public boolean isMarketOrder() { return limitPrice == null; }

    /** Returns the preferred exchange name if specified. */
    public Optional<String> getPreferredExchange() { return Optional.ofNullable(preferredExchange); }

    /** Returns the max slippage constraint if specified. */
    public Optional<BigDecimal> getMaxSlippageBps() { return Optional.ofNullable(maxSlippageBps); }

    /** Returns the max fee constraint if specified. */
    public Optional<BigDecimal> getMaxFeeBps() { return Optional.ofNullable(maxFeeBps); }

    // ── Builder ─────────────────────────────────────────────────────────────────

    /** Creates a new builder with the three required fields. */
    public static Builder builder(
            @NotNull String symbol,
            @NotNull Side side,
            @NotNull BigDecimal quantity
    ) {
        return new Builder(symbol, side, quantity);
    }

    /** Fluent builder for {@link ExecutionRequest}. */
    public static final class Builder {
        private final String symbol;
        private final Side side;
        private final BigDecimal quantity;
        private BigDecimal limitPrice;
        private ExecutionVenue preferredVenue = ExecutionVenue.CENTRALIZED;
        private boolean allowFallback = true;
        private String preferredExchange;
        private BigDecimal maxSlippageBps;
        private BigDecimal maxFeeBps;
        private boolean paperMode = false;

        private Builder(String symbol, Side side, BigDecimal quantity) {
            this.symbol = symbol;
            this.side = side;
            this.quantity = quantity;
        }

        public Builder limitPrice(BigDecimal price) { this.limitPrice = price; return this; }
        public Builder venue(ExecutionVenue v) { this.preferredVenue = v; return this; }
        public Builder allowFallback(boolean allow) { this.allowFallback = allow; return this; }
        public Builder exchange(String name) { this.preferredExchange = name; return this; }
        public Builder maxSlippageBps(BigDecimal bps) { this.maxSlippageBps = bps; return this; }
        public Builder maxFeeBps(BigDecimal bps) { this.maxFeeBps = bps; return this; }
        public Builder paperMode(boolean paper) { this.paperMode = paper; return this; }

        public ExecutionRequest build() {
            return new ExecutionRequest(
                    UUID.randomUUID().toString(),
                    symbol, side, quantity, limitPrice,
                    preferredVenue, allowFallback, preferredExchange,
                    maxSlippageBps, maxFeeBps, paperMode,
                    Instant.now()
            );
        }
    }
}
