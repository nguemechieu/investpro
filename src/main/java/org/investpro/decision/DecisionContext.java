package org.investpro.decision;

import lombok.Builder;
import org.investpro.models.trading.TradePair;
import org.investpro.utils.Side;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;

/**
 * Typed context for a trade decision. Replaces the untyped {@code Map<String, Object>}
 * metadata that previously polluted {@link BotTradeDecision}.
 *
 * <p>All fields are typed; no runtime casting. Null-safe accessors provided throughout.
 * In LIGHTWEIGHT simulation mode this record is omitted entirely to minimize allocations.</p>
 */
@Builder
public record DecisionContext(

        /** The exchange or venue identifier (e.g., "COINBASE", "OANDA", "BINANCE"). */
         String exchange,

        /** Timeframe string (e.g., "1h", "4h", "1d"). */
         String timeframe,

        /** Market session at decision time (e.g., "LONDON", "NEW_YORK", "ASIAN"). */
        String marketSession,

        /** Annualized volatility estimate at decision time (0.0–1.0+). */
        double volatility,

        /** Bid-ask spread as a fraction of mid price at decision time. */
        double spreadFraction,

        /** Liquidity score (0.0 = illiquid, 1.0 = highly liquid). */
        double liquidityScore,

        /** Whether this decision was made in paper trading mode. */
        boolean paperTrading,

        /** Whether this decision was made in a simulation/backtest. */
        boolean simulation,

        /** The execution venue type for this decision. */
         ExecutionVenueType executionVenue,

        /** Estimated order-to-fill latency in milliseconds (0 for simulation). */
        long latencyEstimateMs,

        /** The trade pair for cross-referencing outside the main decision object. */
         TradePair tradePair,

        /** The side (BUY/SELL) for cross-referencing. */
         Side side,

        /** The Instant at which this context was captured. */
        @NotNull Instant capturedAt

) {

    /** Returns a context with null/default values for lightweight simulation. */
    public static DecisionContext empty() {
        return new DecisionContext(
                builder().exchange, builder().timeframe, builder().marketSession,
                0.0, 0.0, 0.0,
                false, true,
                ExecutionVenueType.SIMULATED,
                0L, builder().tradePair, builder().side,
                Instant.EPOCH);
    }

    /** Returns true if context represents a live (non-simulated) execution. */
    public boolean isLive() {
        return !simulation && !paperTrading;
    }

    /** Returns true if context represents a DEX or blockchain venue. */
    public boolean isOnChain() {
        return executionVenue != null && executionVenue.isOnChain;
    }

    /** Returns the exchange name, or "UNKNOWN" if not set. */
    public String exchangeName() {
        return exchange != null ? exchange : "UNKNOWN";
    }
}
