package org.investpro.decision;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable execution routing decision produced by an {@link ExecutionRouter}.
 *
 * <p>An {@code ExecutionRoute} describes HOW and WHERE a trade should be executed:
 * the target venue, expected liquidity, fee estimates, latency expectations, and
 * route health. It is produced before the {@link ExecutionPlan} is submitted and
 * drives smart-order-routing decisions.</p>
 *
 * <h3>Supported venues:</h3>
 * <ul>
 *   <li>Centralized exchanges: Coinbase, Binance, OANDA</li>
 *   <li>Decentralized: Solana DEX, Stellar DEX, generic DEX</li>
 *   <li>Paper / Simulated routing for backtesting</li>
 * </ul>
 *
 * @param executionVenue      target venue for the trade
 * @param routeType           classification of how this route executes
 * @param liquidityEstimate   estimated available liquidity (base currency units, 0.0 if unknown)
 * @param slippageEstimate    expected slippage as a fraction (e.g. 0.001 = 0.1%)
 * @param latencyEstimate     estimated round-trip latency in milliseconds
 * @param routingPriority     the priority level driving routing selection
 * @param feeEstimate         estimated total fee in quote currency
 * @param venueHealth         health score of the venue (0.0 worst → 1.0 best)
 * @param routeConfidence     confidence that this is the optimal route (0.0–1.0)
 * @param routedAt            timestamp when routing was determined
 */
public record ExecutionRoute(
        ExecutionVenueType executionVenue,
        RouteType routeType,
        double liquidityEstimate,
        double slippageEstimate,
        long latencyEstimate,
        RoutingPriority routingPriority,
        BigDecimal feeEstimate,
        double venueHealth,
        double routeConfidence,
        Instant routedAt
) {

    // ─── Inner enums ──────────────────────────────────────────────────────────

    /** Broad classification of how the execution reaches the market. */
    public enum RouteType {
        /** Single venue, direct order submission. */
        DIRECT,
        /** Split across multiple venues for size or liquidity optimization. */
        SPLIT,
        /** DEX pool swap (e.g. Raydium, Jupiter on Solana). */
        DEX_SWAP,
        /** DEX liquidity pool aggregated across multiple pools. */
        DEX_AGGREGATED,
        /** Simulated routing — used for backtesting and paper trading. */
        SIMULATED,
        /** Dark pool or block-trade routing. */
        DARK_POOL
    }

    /** Optimization priority that guided venue selection. */
    public enum RoutingPriority {
        /** Minimize total fees. */
        FEE_MINIMIZATION,
        /** Minimize execution latency. */
        LATENCY,
        /** Maximize liquidity depth. */
        LIQUIDITY,
        /** Minimize price impact / slippage. */
        SLIPPAGE,
        /** Best overall considering all factors. */
        BEST_EXECUTION
    }

    // ─── Compact constructor (validation) ─────────────────────────────────────

    public ExecutionRoute {
        if (executionVenue == null) throw new IllegalArgumentException("executionVenue must not be null");
        if (routeType == null)      throw new IllegalArgumentException("routeType must not be null");
        if (routingPriority == null) throw new IllegalArgumentException("routingPriority must not be null");
        if (feeEstimate == null)    feeEstimate = BigDecimal.ZERO;
        if (routedAt == null)       routedAt = Instant.now();
        liquidityEstimate = Math.max(0.0, liquidityEstimate);
        slippageEstimate  = Math.max(0.0, Math.min(1.0, slippageEstimate));
        latencyEstimate   = Math.max(0L, latencyEstimate);
        venueHealth       = Math.max(0.0, Math.min(1.0, venueHealth));
        routeConfidence   = Math.max(0.0, Math.min(1.0, routeConfidence));
    }

    // ─── Factory methods ──────────────────────────────────────────────────────

    /**
     * Creates a simulated route suitable for backtesting. Uses zero fee, zero latency,
     * and perfect liquidity/health assumptions.
     */
    public static ExecutionRoute simulated() {
        return new ExecutionRoute(
                ExecutionVenueType.SIMULATED, RouteType.SIMULATED,
                Double.MAX_VALUE, 0.0, 0L,
                RoutingPriority.BEST_EXECUTION, BigDecimal.ZERO,
                1.0, 1.0, Instant.now());
    }

    /**
     * Creates a direct route to a centralized exchange with given parameters.
     */
    public static ExecutionRoute direct(
            ExecutionVenueType venue,
            double liquidityEstimate,
            double slippageEstimate,
            long latencyMs,
            BigDecimal feeEstimate) {
        return new ExecutionRoute(
                venue, RouteType.DIRECT, liquidityEstimate, slippageEstimate,
                latencyMs, RoutingPriority.BEST_EXECUTION, feeEstimate,
                1.0, 0.8, Instant.now());
    }

    // ─── Derived properties ───────────────────────────────────────────────────

    /** Returns {@code true} if this is an on-chain DEX route. */
    public boolean isOnChain() {
        return executionVenue.isOnChain();
    }

    /** Returns {@code true} if the route is simulated (backtesting / paper mode). */
    public boolean isSimulated() {
        return routeType == RouteType.SIMULATED || executionVenue == ExecutionVenueType.SIMULATED;
    }

    /** Returns {@code true} if execution quality (health + confidence) is above 70%. */
    public boolean isHighQuality() {
        return venueHealth >= 0.7 && routeConfidence >= 0.7;
    }

    /**
     * Returns an overall quality score combining health and confidence.
     * Range: 0.0 (worst) to 1.0 (best).
     */
    public double qualityScore() {
        return (venueHealth + routeConfidence) / 2.0;
    }
}
