package org.investpro.strategy.api;

import org.investpro.strategy.StrategyContext;
import org.investpro.strategy.StrategySignal;
import org.jetbrains.annotations.NotNull;

/**
 * Public API for user-developed trading strategies.
 *
 * User strategies are allowed to:
 * - analyze market data from StrategyContext
 * - generate StrategySignal objects with trading recommendations
 * - implement their own indicators and analysis logic
 *
 * User strategies are NOT allowed to:
 * - access Exchange directly
 * - access SystemCore
 * - bypass RiskManagementSystem
 * - bypass TradeExecutionCoordinator
 * - place orders directly
 * - mutate account or position state
 * - access credentials or secrets
 * - access database
 *
 * All trading decisions flow through the platform's safety gates:
 * UserStrategy -> StrategySignal -> RiskManagementSystem ->
 * TradeExecutionCoordinator -> Exchange
 */
public interface UserStrategy {

    /**
     * Unique identifier for this strategy.
     *
     * Must be unique across all registered strategies.
     * Examples: "my-ema-strategy", "custom-rsi-scalper", "user-breakout-bot"
     *
     * @return non-blank unique strategy ID
     */
    @NotNull
    String getId();

    /**
     * Human-readable name for this strategy.
     *
     * Examples: "Simple EMA Crossover", "Custom RSI Scalper", "Breakout Bot"
     *
     * @return non-blank strategy name
     */
    @NotNull
    String getName();

    /**
     * Optional description of what this strategy does.
     *
     * @return description text, or empty string if not provided
     */
    default String getDescription() {
        return "";
    }

    /**
     * Number of historical candles required before this strategy can generate
     * signals.
     *
     * The platform will not call generateSignal() until at least this many candles
     * are available.
     *
     * Examples:
     * - Simple moving average: 50-200 bars
     * - RSI: 15 bars
     * - MACD: 35 bars
     * - Complex analysis: 300+ bars
     *
     * Default: 100 bars
     *
     * @return number of bars (minimum 1)
     */
    default int requiredWarmupBars() {
        return 100;
    }

    /**
     * Generate a trading signal based on the current market context.
     *
     * This is called on every new candle or market update.
     * Return a StrategySignal indicating what action to take (BUY, SELL, or HOLD).
     *
     * IMPORTANT: This method MUST NOT:
     * - throw exceptions (catch and handle internally)
     * - place orders
     * - access Exchange or account
     * - take more than a few hundred milliseconds
     * - store state that violates thread safety
     *
     * Exceptions will be caught and logged, but a signal must be returned.
     *
     * @param context market data and analysis context
     * @return StrategySignal with side (BUY/SELL/HOLD), prices, confidence, and
     *         reason
     *         Never null - return StrategySignal.hold(...) if uncertain
     */
    @NotNull
    StrategySignal generateSignal(@NotNull StrategyContext context);
}
