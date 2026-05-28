package org.investpro.decision;

/**
 * Strategy interface for selecting an {@link ExecutionRoute} for a given trade.
 *
 * <p>Implementations perform smart order routing: choosing the optimal venue
 * based on liquidity, latency, fees, slippage, and venue health. Routers may
 * access real-time market microstructure data or use static configuration.</p>
 *
 * <h3>Built-in implementations:</h3>
 * <ul>
 *   <li>{@code SimulatedExecutionRouter} — always returns {@link ExecutionRoute#simulated()}</li>
 *   <li>{@code DirectVenueRouter} — routes to a pre-configured venue</li>
 *   <li>{@code SmartOrderRouter} — multi-venue comparison (future)</li>
 * </ul>
 *
 * <p>The router is injected into {@link DecisionPipelineOrchestrator} and called
 * during the routing phase of the institutional decision pipeline.</p>
 */
@FunctionalInterface
public interface ExecutionRouter {

    /**
     * Selects the best {@link ExecutionRoute} for the given trade intent and plan.
     *
     * @param intent the trade intent describing what should be traded and at what size
     * @param plan   the execution plan (may be {@link ExecutionPlan#EMPTY} in simulation)
     * @param mode   the execution mode (LIVE, PAPER, SIMULATION, LIGHTWEIGHT)
     * @return a non-null {@link ExecutionRoute} describing where and how to execute
     */
    ExecutionRoute route(TradeIntent intent, ExecutionPlan plan, DecisionMode mode);

    // ─── Default implementations ──────────────────────────────────────────────

    /**
     * Returns a router that always selects a simulated route.
     * Suitable for backtesting and paper trading.
     */
    static ExecutionRouter simulated() {
        return (intent, plan, mode) -> ExecutionRoute.simulated();
    }

    /**
     * Returns a router that routes directly to the specified venue.
     * No latency, fee, or liquidity analysis is performed.
     *
     * @param venue the target execution venue
     */
    static ExecutionRouter direct(ExecutionVenueType venue) {
        return (intent, plan, mode) -> ExecutionRoute.direct(venue, 0, 0.001, 100, java.math.BigDecimal.ZERO);
    }
}
