package org.investpro.decision;

/**
 * Strategy interface for scoring the suitability of an execution venue for a given trade.
 *
 * <p>Venue scoring is used by smart order routers to select the optimal execution destination.
 * Scores range from {@code 0.0} (unsuitable) to {@code 1.0} (ideal). Factors influencing
 * the score include: current venue health, latency, fee structure, liquidity depth, and
 * regulatory compatibility for the asset.</p>
 *
 * <h3>Usage in pipeline:</h3>
 * <p>The {@link DecisionPipelineOrchestrator} calls {@code VenueScorer} implementations
 * during the routing phase to rank available venues and select the highest-scoring one.</p>
 */
@FunctionalInterface
public interface VenueScorer {

    /**
     * Computes a suitability score for the given venue in the context of a trade intent.
     *
     * @param venue  the execution venue to score
     * @param intent the trade intent providing context (asset class, exposure, regime)
     * @return score in the range [0.0, 1.0]; 1.0 = perfectly suitable
     */
    double scoreVenue(ExecutionVenueType venue, TradeIntent intent);

    // ─── Default implementations ──────────────────────────────────────────────

    /**
     * Returns a scorer that always gives all venues a perfect score.
     * Suitable for simulation mode where venue selection is not meaningful.
     */
    static VenueScorer alwaysPerfect() {
        return (venue, intent) -> 1.0;
    }

    /**
     * Returns a scorer that penalizes on-chain venues by 40% to reflect
     * higher latency and slippage compared to centralized exchanges.
     */
    static VenueScorer preferCentralized() {
        return (venue, intent) -> venue.isOnChain() ? 0.6 : 1.0;
    }

    /**
     * Returns a scorer that gives simulated venues the lowest possible score
     * (0.0) so they are never selected in live or paper mode.
     */
    static VenueScorer noSimulated() {
        return (venue, intent) -> venue == ExecutionVenueType.SIMULATED ? 0.0 : 1.0;
    }
}
