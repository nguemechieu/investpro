package org.investpro.decision;

import org.investpro.models.trading.TradePair;

/**
 * Strategy interface for analyzing market liquidity at a given execution venue.
 *
 * <p>Liquidity analysis informs position sizing, execution routing, and slippage
 * estimation. Implementations may query order book depth, historical volume, or
 * on-chain pool reserves.</p>
 *
 * <p>Results are used by the {@link DecisionPipelineOrchestrator} during the
 * routing phase to score and select execution routes.</p>
 */
@FunctionalInterface
public interface LiquidityAnalyzer {

    /**
     * Estimates the available liquidity for a given trading pair at a specific venue.
     *
     * @param pair  the trading pair to analyze
     * @param venue the execution venue to query
     * @return liquidity estimate in base currency units; {@code Double.MAX_VALUE} if
     *         unlimited/unknown (e.g. simulated); {@code 0.0} if data is unavailable
     */
    double analyzeLiquidity(TradePair pair, ExecutionVenueType venue);

    // ─── Default implementations ──────────────────────────────────────────────

    /**
     * Returns an analyzer that always reports unlimited liquidity.
     * Safe default for backtesting and simulation environments.
     */
    static LiquidityAnalyzer unlimited() {
        return (pair, venue) -> Double.MAX_VALUE;
    }

    /**
     * Returns an analyzer that reports zero liquidity for DEX venues
     * and unlimited liquidity for centralized exchanges.
     * Useful for conservative routing tests.
     */
    static LiquidityAnalyzer conservativeDex() {
        return (pair, venue) -> venue.isOnChain() ? 0.0 : Double.MAX_VALUE;
    }
}
